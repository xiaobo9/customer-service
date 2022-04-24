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
package com.chatopera.cc.config;

import com.chatopera.cc.config.interceptor.CrossInterceptorHandler;
import com.chatopera.cc.config.interceptor.LogInterceptorHandler;
import com.chatopera.cc.config.interceptor.UserInterceptorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.MultipartConfigElement;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    private final UserInterceptorHandler userInterceptorHandler;
    private final LogInterceptorHandler logInterceptorHandler;

    @Value("${web.upload-path}")
    private String uploaddir;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String multipartMaxUpload;

    @Value("${spring.servlet.multipart.max-request-size}")
    private String multipartMaxRequest;

    public WebMvcConfiguration(UserInterceptorHandler userInterceptorHandler, LogInterceptorHandler logInterceptorHandler) {
        this.userInterceptorHandler = userInterceptorHandler;
        this.logInterceptorHandler = logInterceptorHandler;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 多个拦截器组成一个拦截器链
        // addPathPatterns 用于添加拦截规则
        // excludePathPatterns 用户排除拦截
        registry.addInterceptor(userInterceptorHandler).addPathPatterns("/**")
                .excludePathPatterns("/login.html", "/im/**", "/res/image*", "/res/file*", "/cs/**");
        registry.addInterceptor(new CrossInterceptorHandler())
                .addPathPatterns("/**").excludePathPatterns("/static");
        registry.addInterceptor(logInterceptorHandler).addPathPatterns("/**");
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToDateConverter());
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.parse(multipartMaxUpload)); //KB,MB
        factory.setMaxRequestSize(DataSize.parse(multipartMaxRequest));
        factory.setLocation(uploaddir);
        return factory.createMultipartConfig();
    }

}
