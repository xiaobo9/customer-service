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

package com.chatopera.cc.basic;

import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.peer.PeerSyncIM;
import com.chatopera.cc.util.SystemEnvHelper;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;

import java.util.HashSet;
import java.util.Set;

public class MainContext {

    private final static Logger logger = LoggerFactory.getLogger(MainContext.class);

    private static boolean imServerRunning = false;  // IM服务状态

    private static final Set<String> modules = new HashSet<>();

    private static ApplicationContext applicationContext;

    private static ElasticsearchTemplate template;

    private static CacheService cacheService;

    private static PeerSyncIM peerSyncIM;

    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }

    public static ApplicationContext getContext() {
        return applicationContext;
    }

    public static ElasticsearchTemplate getTemplate() {
        return template;
    }

    public static void setTemplate(ElasticsearchTemplate template) {
        MainContext.template = template;
    }

    /**
     * 系统级的加密密码 ， 从CA获取
     */
    public static String getSystemSecurityPassword() {
        return SystemEnvHelper.parseFromApplicationProps("application.security.password");
    }

    public static void setIMServerStatus(boolean running) {
        imServerRunning = running;
    }

    public static boolean getIMServerStatus() {
        return imServerRunning;
    }

    /**
     * 缓存管理
     */
    public static CacheService getCache() {
        if (cacheService == null) {
            cacheService = getContext().getBean(CacheService.class);
        }
        return cacheService;
    }

    public static PeerSyncIM getPeerSyncIM() {
        if (peerSyncIM == null) {
            peerSyncIM = getContext().getBean(PeerSyncIM.class);
        }
        return peerSyncIM;
    }

    /**
     * 开启模块
     */
    public static void enableModule(final String moduleName) {
        logger.info("[module] enable module {}", moduleName);
        modules.add(StringUtils.lowerCase(moduleName));
    }

    public static boolean hasModule(final String moduleName) {
        return modules.contains(StringUtils.lowerCase(moduleName));
    }

    public static void removeModule(final String moduleName) {
        modules.remove(moduleName);
    }

    /**
     * 获得Model
     */
    public static Set<String> getModules() {
        return modules;
    }
}
