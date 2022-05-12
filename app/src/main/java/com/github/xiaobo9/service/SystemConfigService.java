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

package com.github.xiaobo9.service;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.cache.CacheService;
import com.github.xiaobo9.entity.SystemConfig;
import com.github.xiaobo9.repository.SystemConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;

@Slf4j
@Service
public class SystemConfigService {
    @Autowired
    private SystemConfigRepository repository;
    @Autowired
    private CacheService cacheService;

    /**
     * 获取系统配置
     *
     * @return system config bean
     */
    @NotNull
    public SystemConfig getSystemConfig() {
        SystemConfig systemConfig = cacheService.findOneSystemByIdAndOrgi("systemConfig", Constants.SYSTEM_ORGI);
        if (systemConfig == null) {
            systemConfig = repository.findByOrgi(Constants.SYSTEM_ORGI);
        }
        // FIXME 默认配置
        return systemConfig != null ? systemConfig : new SystemConfig();
    }
}
