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
package com.chatopera.cc.persistence.es;

import com.github.xiaobo9.entity.Topic;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.data.domain.Page;

import java.util.List;

public interface TopicEsCommonRepository {
	Page<Topic> getTopicByCateAndOrgi(String cate, String orgi, String q, int p, int ps) ;
	
	Page<Topic> getTopicByTopAndOrgi(boolean top, String orgi, String aiid, int p, int ps) ;
	
	List<Topic> getTopicByOrgi(String orgi, String type, String q) ;
	
	Page<Topic> getTopicByCateAndUser(String cate, String q, String user, int p, int ps) ;
	
	Page<Topic> getTopicByCon(BoolQueryBuilder booleanQueryBuilder, int p, int ps) ;
}
