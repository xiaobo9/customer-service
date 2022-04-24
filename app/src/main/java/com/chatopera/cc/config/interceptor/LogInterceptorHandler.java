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
package com.chatopera.cc.config.interceptor;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.model.RequestLog;
import com.chatopera.cc.model.User;
import com.chatopera.cc.persistence.repository.RequestLogRepository;
import com.chatopera.cc.util.Menu;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.Enumeration;

/**
 * 系统访问记录
 *
 * @author admin
 */
@Slf4j
@Component
public class LogInterceptorHandler implements HandlerInterceptor {
    private final RequestLogRepository requestLogRes;

    public LogInterceptorHandler(RequestLogRepository requestLogRes) {
        this.requestLogRes = requestLogRes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Object bean = handlerMethod.getBean();
            if (bean instanceof Handler) {
                ((Handler) bean).setStartTime(System.currentTimeMillis());
            }
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView arg3) throws Exception {
        String requestURI = request.getRequestURI();
        if (StringUtils.isBlank(requestURI) ||
                requestURI.startsWith("/message/ping") ||
                requestURI.startsWith("/res/css") ||
                requestURI.startsWith("/error") ||
                requestURI.startsWith("/im/")) {
            return;
        }

        if (!(handler instanceof HandlerMethod)) {
            return;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Object bean = handlerMethod.getBean();
        RequestMapping obj = handlerMethod.getMethod().getAnnotation(RequestMapping.class);

        RequestLog log = new RequestLog();
        log.setEndtime(new Date());
        log.setUrl(requestURI);
        log.setHostname(request.getRemoteHost());
        log.setType(MainContext.LogType.REQUEST.toString());

        if (obj != null) {
            log.setName(obj.name());
        }
        log.setMethodname(handlerMethod.toString());
        log.setIp(request.getRemoteAddr());
        log.setClassname(bean.getClass().toString());
        if (bean instanceof Handler && ((Handler) bean).getStartTime() != 0) {
            log.setQuerytime(System.currentTimeMillis() - ((Handler) bean).getStartTime());
        }

        User user = (User) request.getSession(true).getAttribute(Constants.USER_SESSION_NAME);
        if (user != null) {
            log.setUserid(user.getId());
            log.setUsername(user.getUsername());
            log.setUsermail(user.getEmail());
            log.setOrgi(user.getOrgi());
        }
        StringBuilder str = new StringBuilder();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String paraName = names.nextElement();
            if (paraName.contains("password")) {
                str.append(paraName).append("=").append(MainUtils.encryption(request.getParameter(paraName))).append(",");
            } else {
                str.append(paraName).append("=").append(request.getParameter(paraName)).append(",");
            }
        }

        Menu menu = handlerMethod.getMethod().getAnnotation(Menu.class);
        if (menu != null) {
            log.setFuntype(menu.type());
            log.setFundesc(menu.subtype());
            log.setName(menu.name());
        }

        log.setParameters(str.toString());
        requestLogRes.save(log);
    }

}
