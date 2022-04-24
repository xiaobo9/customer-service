package com.chatopera.cc.config.filter;

import com.chatopera.cc.basic.auth.AuthToken;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ApiRequestMatchingFilter implements Filter {

    private final RequestMatcher apiNeedAuthorization;
    @Autowired
    private AuthToken authToken;

    public ApiRequestMatchingFilter() {
        this.apiNeedAuthorization = new AntPathRequestMatcher("/api/**");
    }

    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        // options 操作，不继续调用链
        if ("options".equalsIgnoreCase(request.getMethod())) {
            headers(response);
            return;
        }

        // 不是 api 操作，不需要认证信息，直接继续
        if (!apiNeedAuthorization.matches(request)) {
            chain.doFilter(req, resp);
            return;
        }
        // 验证认证信息
        if (authorization(request)) {
            chain.doFilter(req, resp);
            return;
        }
        response.sendRedirect("/tokens/error");
    }

    private void headers(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with,accept,authorization,content-type");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setStatus(HttpStatus.ACCEPTED.value());
    }

    private boolean authorization(HttpServletRequest request) {
        String authorization = request.getHeader("authorization");
        if (StringUtils.isBlank(authorization)) {
            authorization = request.getParameter("authorization");
        }
        return StringUtils.isNotBlank(authorization) && authToken.existUserByAuth(authorization);
    }

}
