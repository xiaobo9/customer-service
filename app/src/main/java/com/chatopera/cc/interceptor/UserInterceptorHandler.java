/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chatopera.cc.interceptor;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.config.MessagingServerConfigure;
import com.chatopera.cc.model.Dict;
import com.chatopera.cc.model.SystemConfig;
import com.chatopera.cc.model.User;
import com.chatopera.cc.proxy.UserProxy;
import com.chatopera.cc.util.Menu;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.Serializable;

@Slf4j
public class UserInterceptorHandler extends HandlerInterceptorAdapter {
    private static UserProxy userProxy;
    private static Integer webimport;

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        HttpSession session = request.getSession(true);
        User user = (User) session.getAttribute(Constants.USER_SESSION_NAME);
        Menu menu = handlerMethod.getMethod().getAnnotation(Menu.class);
        if (user != null || (menu != null && menu.access()) || handlerMethod.getBean() instanceof BasicErrorController) {
            if (user != null && StringUtils.isNotBlank(user.getId())) {
                // 每次刷新用户的组织机构、角色和权限
                // TODO 此处代码执行频率高，但是并不是每次都要执行，存在很多冗余
                // 待用更好的方法实现
                UserProxy userProxy = getUserProxy();
                userProxy.attachOrgansPropertiesForUser(user);
                userProxy.attachRolesMap(user);

                session.setAttribute(Constants.USER_SESSION_NAME, user);
            }
            return true;
        }

        if (StringUtils.isNotBlank(request.getParameter("msg"))) {
            response.sendRedirect("/login.html?msg=" + request.getParameter("msg"));
        } else {
            response.sendRedirect("/login.html");
        }
        return false;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView view) {
        if (view == null) {
            return;
        }
        HttpSession session = request.getSession();
        final User user = (User) session.getAttribute(Constants.USER_SESSION_NAME);
        final SystemConfig systemConfig = MainUtils.getSystemConfig();
        if (user != null) {
            view.addObject("user", user);

            if (systemConfig.isEnablessl()) {
                view.addObject("schema", "https");
                if (request.getServerPort() == 80) {
                    view.addObject("port", 443);
                } else {
                    view.addObject("port", request.getServerPort());
                }
            } else {
                view.addObject("schema", request.getScheme());
                view.addObject("port", request.getServerPort());
            }
            view.addObject("hostname", request.getServerName());

            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Menu menu = handlerMethod.getMethod().getAnnotation(Menu.class);
            if (menu != null) {
                view.addObject("subtype", menu.subtype());
                view.addObject("maintype", menu.type());
                view.addObject("typename", menu.name());
            }
            view.addObject("orgi", user.getOrgi());
        }
        final String infoace = (String) session.getAttribute(Constants.CSKEFU_SYSTEM_INFOACQ);        //进入信息采集模式
        if (StringUtils.isNotBlank(infoace)) {
            view.addObject("infoace", infoace);        //进入信息采集模式
        }
        view.addObject("webimport", getWebimport());
        view.addObject("sessionid", MainUtils.getContextID(session.getId()));

        view.addObject("models", MainContext.getModules());

        // WebIM共享用户
        User imUser = (User) session.getAttribute(Constants.IM_USER_SESSION_NAME);
        if (imUser == null) {
            imUser = new User();
            imUser.setUsername(Constants.GUEST_USER);
            imUser.setId(MainUtils.getContextID(request.getSession(true).getId()));
            imUser.setSessionid(imUser.getId());
            view.addObject("imuser", imUser);
        }

        if (request.getParameter("msg") != null) {
            view.addObject("msg", request.getParameter("msg"));
        }

        view.addObject("uKeFuDic", Dict.getInstance());    //处理系统 字典数据 ， 通过 字典code 获取

        Serializable system = MainContext.getCache().findOneSystemByIdAndOrgi(Constants.CSKEFU_SYSTEM_SECFIELD, Constants.SYSTEM_ORGI);
        view.addObject("uKeFuSecField", system);    //处理系统 需要隐藏号码的字段， 启动的时候加载

        view.addObject("systemConfig", systemConfig);
        view.addObject("tagTypeList", Dict.getInstance().getDic("com.dic.tag.type"));

        view.addObject("advTypeList", Dict.getInstance().getDic("com.dic.adv.type"));
        view.addObject("ip", request.getRemoteAddr());
    }

    private static Integer getWebimport() {
        if (webimport == null) {
            webimport = MainContext.getContext().getBean(MessagingServerConfigure.class).getWebIMPort();
        }
        return webimport;
    }


    private static UserProxy getUserProxy() {
        if (userProxy == null) {
            userProxy = MainContext.getContext().getBean(UserProxy.class);
        }
        return userProxy;
    }
}
