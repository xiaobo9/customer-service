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
package com.chatopera.cc.config.listener;

import com.chatopera.cc.acd.ACDPolicyService;
import com.chatopera.cc.basic.MainContext;
import com.github.xiaobo9.entity.AgentStatus;
import com.github.xiaobo9.entity.Favorites;
import com.github.xiaobo9.entity.WorkOrders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.elasticsearch.ElasticsearchException;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupListener implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    ElasticsearchTemplate elasticSearchTemplate;

    @Autowired
    private ACDPolicyService acdPolicyService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        elasticSearchTemplate();

        AgentStatus.policyService((organId, organ) -> acdPolicyService.initSessionConfig(organId, organ));
    }

    private void elasticSearchTemplate() {
        if (!elasticSearchTemplate.indexExists(WorkOrders.class)) {
            elasticSearchTemplate.createIndex(WorkOrders.class);
        }
        if (!elasticSearchTemplate.indexExists(Favorites.class)) {
            elasticSearchTemplate.createIndex(Favorites.class);
        }
        try {
            elasticSearchTemplate.getMapping(WorkOrders.class);
        } catch (ElasticsearchException e) {
            elasticSearchTemplate.putMapping(Favorites.class);
            elasticSearchTemplate.putMapping(WorkOrders.class);
        }
        MainContext.setTemplet(elasticSearchTemplate);
    }
}