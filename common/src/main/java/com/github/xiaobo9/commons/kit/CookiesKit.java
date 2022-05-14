/*
 * Copyright 2022 xiaobo9 <https://github.com/xiaobo9>
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

package com.github.xiaobo9.commons.kit;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CookiesKit {
    public static Optional<Cookie> getCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && StringUtils.isNotBlank(cookie.getValue())) {
                return Optional.of(cookie);
            }
        }
        return Optional.empty();
    }

    public static Map<String, String> cookie2Map(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (Cookie cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            if (StringUtils.isNotBlank(cookie.getName()) && StringUtils.isNotBlank(cookie.getValue())) {
                map.put(cookie.getName(), cookie.getValue());
            }
        }
        return map;
    }
}
