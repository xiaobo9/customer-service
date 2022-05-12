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
import com.github.xiaobo9.entity.Template;
import com.github.xiaobo9.repository.TemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TemplateService {

    @Autowired
    private TemplateRepository repository;

    @Autowired
    private CacheService cacheService;

    public Template getTemplate(String id) {
        Template template = cacheService.findOneSystemByIdAndOrgi(id, Constants.SYSTEM_ORGI);
        if (template == null) {
            // FIXME 并发处理
            template = repository.findByIdAndOrgi(id, Constants.SYSTEM_ORGI);
            cacheService.putSystemByIdAndOrgi(id, Constants.SYSTEM_ORGI, template);
        }
        return template;
    }

}
