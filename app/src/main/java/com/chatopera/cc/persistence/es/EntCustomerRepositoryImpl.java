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

import com.github.xiaobo9.entity.EntCustomer;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.repository.UserRepository;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Repository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Repository
public class EntCustomerRepositoryImpl implements EntCustomerEsCommonRepository {

    private SimpleDateFormat dateFromate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private UserRepository userRes;

    private ElasticsearchTemplate elasticsearchTemplate;

    @Autowired
    public void setElasticsearchTemplate(ElasticsearchTemplate elasticsearchTemplate) {
        this.elasticsearchTemplate = elasticsearchTemplate;
    }

    @Override
    public Page<EntCustomer> findByCreaterAndSharesAndOrgi(String creater, String shares, String orgi, boolean includeDeleteData, String q, Pageable page) {

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .must(new BoolQueryBuilder()
                        .should(termQuery("creater", creater))
                        .should(termQuery("shares", creater))
                        .should(termQuery("shares", "all")))
                .must(termQuery("orgi", orgi))
                .must(termQuery("datastatus", includeDeleteData));

        if (!StringUtils.isBlank(q)) {
            boolQueryBuilder.must(new QueryStringQueryBuilder(q).defaultOperator(Operator.AND));
        }
        return processQuery(boolQueryBuilder, page);
    }

    @Override
    public Page<EntCustomer> findByCreaterAndSharesAndOrgi(
            String creater, String shares, String orgi, Date begin, Date end, boolean includeDeleteData,
            BoolQueryBuilder boolQueryBuilder, String q, Pageable page) {
        boolQueryBuilder
                .must(new BoolQueryBuilder()
                        .should(termQuery("creater", creater))
                        .should(termQuery("shares", creater))
                        .should(termQuery("shares", "all")))
                .must(termQuery("orgi", orgi))
                .must(termQuery("datastatus", includeDeleteData));

        RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("createtime");
        if (begin != null) {
            rangeQuery.from(begin.getTime());
        }
        rangeQuery.to(end != null ? end.getTime() : new Date().getTime());
        if (begin != null || end != null) {
            boolQueryBuilder.must(rangeQuery);
        }
        if (!StringUtils.isBlank(q)) {
            boolQueryBuilder.must(new QueryStringQueryBuilder(q).defaultOperator(Operator.AND));
        }
        return processQuery(boolQueryBuilder, page);
    }

    @Override
    public Page<EntCustomer> findByCreaterAndSharesAndOrgi(String creater, String shares, String orgi, Date begin, Date end, boolean includeDeleteData, String q, Pageable page) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .must(new BoolQueryBuilder()
                        .should(termQuery("creater", creater))
                        .should(termQuery("shares", creater))
                        .should(termQuery("shares", "all"))
                )
                .must(termQuery("orgi", orgi))
                .must(termQuery("datastatus", includeDeleteData));

        RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("createtime");
        if (begin != null) {
            rangeQuery.from(dateFromate.format(begin));
        }
        rangeQuery.to(dateFromate.format(end != null ? end : new Date()));
        if (begin != null || end != null) {
            boolQueryBuilder.must(rangeQuery);
        }
        if (StringUtils.isNotBlank(q)) {
            boolQueryBuilder.must(new QueryStringQueryBuilder(q).defaultOperator(Operator.AND));
        }
        return processQuery(boolQueryBuilder, page);
    }


    private Page<EntCustomer> processQuery(BoolQueryBuilder boolQueryBuilder, Pageable page) {
        NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withSort(new FieldSortBuilder("creater.keyword").unmappedType("boolean").order(SortOrder.DESC))
                .withSort(new FieldSortBuilder("name.keyword").unmappedType("string").order(SortOrder.DESC));

        searchQueryBuilder.withPageable(page);

        Page<EntCustomer> entCustomerList = null;
        if (elasticsearchTemplate.indexExists(EntCustomer.class)) {
            entCustomerList = elasticsearchTemplate.queryForPage(searchQueryBuilder.build(), EntCustomer.class);
        }
        if (entCustomerList.getContent().size() > 0) {
            List<String> ids = new ArrayList<>();
            for (EntCustomer entCustomer : entCustomerList.getContent()) {
                if (entCustomer.getCreater() != null && ids.size() < 1024) {
                    ids.add(entCustomer.getCreater());
                }
            }
            List<User> users = userRes.findAllById(ids);
            for (EntCustomer entCustomer : entCustomerList.getContent()) {
                for (User user : users) {
                    if (user.getId().equals(entCustomer.getCreater())) {
                        entCustomer.setUser(user);
                        break;
                    }
                }
            }
        }
        return entCustomerList;
    }
}
