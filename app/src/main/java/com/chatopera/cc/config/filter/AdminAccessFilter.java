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
package com.chatopera.cc.config.filter;

import com.chatopera.cc.basic.Constants;
import com.github.xiaobo9.entity.User;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AdminAccessFilter implements Filter {
    private final RequestMatcher[] needAccessFilterMatchers;

    public AdminAccessFilter(RequestMatcher... needAccessFilterMatchers) {
        this.needAccessFilterMatchers = needAccessFilterMatchers;
    }

    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        boolean notNeedAccessFilter = true;
        for (RequestMatcher matcher : needAccessFilterMatchers) {
            if (matcher.matches(request)) {
                notNeedAccessFilter = false;
            }
        }
        if (notNeedAccessFilter) {
            // 不需要访问控制的资源，继续调用
            chain.doFilter(req, resp);
            return;
        }
        User user = (User) request.getSession().getAttribute(Constants.USER_SESSION_NAME);
        if (user == null || !user.isAdmin()) {
            // 重定向到 无权限执行操作的页面
            HttpServletResponse response = (HttpServletResponse) resp;
            response.sendRedirect("/?msg=security");
            return;
        }
        chain.doFilter(req, resp);
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FilterConfig arg0) throws ServletException {
    }
}