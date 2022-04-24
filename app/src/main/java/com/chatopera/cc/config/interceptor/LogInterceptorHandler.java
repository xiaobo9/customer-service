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
import java.lang.reflect.Method;
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
    private static final String REQUEST_START_TIME = "REQUEST_START_TIME";
    private final RequestLogRepository requestLogRes;

    public LogInterceptorHandler(RequestLogRepository requestLogRes) {
        this.requestLogRes = requestLogRes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            Object bean = ((HandlerMethod) handler).getBean();
            if (bean instanceof Handler) {
                request.setAttribute(REQUEST_START_TIME, new Date());
            }
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView view) throws Exception {
        String requestURI = request.getRequestURI();
        if (notNeedLog(requestURI)) {
            return;
        }

        // 像 preHandle 一样判断是否进行处理
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Object bean = handlerMethod.getBean();

            if ((bean instanceof Handler)) {
                log(request, handlerMethod, bean);
            }
        }
    }

    private void log(HttpServletRequest request, HandlerMethod handlerMethod, Object bean) {
        Method method = handlerMethod.getMethod();

        RequestLog log = new RequestLog();
        Date start = (Date) request.getAttribute(REQUEST_START_TIME);
        log.setStarttime(start);
        log.setEndtime(new Date());
        log.setQuerytime(System.currentTimeMillis() - start.getTime());

        log.setUrl(request.getRequestURI());
        // 避免可能的性能问题，不记录客户端主机名
        // log.setHostname(request.getRemoteHost());
        log.setIp(request.getRemoteAddr());

        log.setType(MainContext.LogType.REQUEST.toString());

        log.setMethodname(handlerMethod.toString());
        log.setClassname(bean.getClass().toString());

        User user = (User) request.getSession(true).getAttribute(Constants.USER_SESSION_NAME);
        writeUserInfo2Log(user, log);

        writeAnnotationInfos2Log(method, log);

        log.setParameters(parameters(request));
        requestLogRes.save(log);
    }

    private void writeUserInfo2Log(User user, RequestLog log) {
        if (user != null) {
            log.setUserid(user.getId());
            log.setUsername(user.getUsername());
            log.setUsermail(user.getEmail());
            log.setOrgi(user.getOrgi());
        }
    }

    private void writeAnnotationInfos2Log(Method method, RequestLog log) {
        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null) {
            log.setName(requestMapping.name());
        }
        Menu menu = method.getAnnotation(Menu.class);
        if (menu != null) {
            log.setFuntype(menu.type());
            log.setFundesc(menu.subtype());
            log.setName(menu.name());
        }
    }

    private String parameters(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getParameter(name);
            if (name.contains("password")) {
                builder.append(name).append("=").append(MainUtils.encryption(value)).append(",");
            } else {
                builder.append(name).append("=").append(value).append(",");
            }
        }
        return builder.toString();
    }

    private boolean notNeedLog(String requestURI) {
        return StringUtils.isBlank(requestURI) ||
                requestURI.startsWith("/message/ping") ||
                requestURI.startsWith("/res/css") ||
                requestURI.startsWith("/error") ||
                requestURI.startsWith("/im/");
    }

}
