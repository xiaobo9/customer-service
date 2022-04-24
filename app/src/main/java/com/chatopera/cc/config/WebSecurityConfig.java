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

import com.chatopera.cc.config.filter.AdminAccessFilter;
import com.chatopera.cc.config.filter.ApiRequestMatchingFilter;
import com.chatopera.cc.config.filter.CsrfHeaderFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.addFilterAfter(adminAccessFilter(), BasicAuthenticationFilter.class)
                .antMatcher("*/*").authorizeRequests().anyRequest().permitAll()
                .and()
                .addFilterAfter(csrfHeaderFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(apiRequestMatchingFilter(), BasicAuthenticationFilter.class);
    }

    @Bean
    public AdminAccessFilter adminAccessFilter() {
        RequestMatcher actuator = new AntPathRequestMatcher("/actuator/**");
        RequestMatcher druid = new AntPathRequestMatcher("/druid/**");
        return new AdminAccessFilter(actuator, druid);
    }

    @Bean
    public ApiRequestMatchingFilter apiRequestMatchingFilter() {
        return new ApiRequestMatchingFilter();
    }

    private CsrfHeaderFilter csrfHeaderFilter() {
        return new CsrfHeaderFilter();
    }
}
