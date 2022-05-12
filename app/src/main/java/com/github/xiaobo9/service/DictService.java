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
import com.chatopera.cc.cache.RedisCommand;
import com.chatopera.cc.cache.RedisKey;
import com.chatopera.cc.util.Dict;
import com.chatopera.cc.util.SerializeUtil;
import com.github.xiaobo9.entity.SysDic;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DictService {

    private static final Logger logger = LoggerFactory.getLogger(DictService.class);
    @Autowired
    private CacheService cacheService;
    @Autowired
    private RedisCommand redisCommand;

    @NotNull
    public List<SysDic> getDic(final String code) {
        String serialized = redisCommand.getHashKV(RedisKey.getSysDicHashKeyByOrgi(Constants.SYSTEM_ORGI), code);
        if (StringUtils.isBlank(serialized)) {
            return Collections.emptyList();
        }
        Object obj = SerializeUtil.deserialize(serialized);
        if (obj instanceof List) {
            List<SysDic> sysDicList = (List<SysDic>) obj;
            return sysDicList.stream()
                    .filter(dic -> dic.getDicid().equals(dic.getParentid()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 获得一个词典的所有子项，并且每个子项的父都是id
     */
    public List<SysDic> getDic(final String code, final String id) {
        List<SysDic> result = new ArrayList<SysDic>();
        String serialized = redisCommand.getHashKV(RedisKey.getSysDicHashKeyByOrgi(Constants.SYSTEM_ORGI), code);

        if (StringUtils.isNotBlank(serialized)) {
            Object obj = SerializeUtil.deserialize(serialized);
            if (obj instanceof List) {
                List<SysDic> sysDics = (List<SysDic>) obj;
                for (SysDic dic : sysDics) {
                    if (dic.getParentid().equals(id)) {
                        result.add(dic);
                    }
                }
            } else if (obj instanceof SysDic) {
                result.add((SysDic) obj);
            } else {
                logger.warn("[getDic] nothing found for code or id {} with deserialize, this is a potential error.", code);
            }
        } else {
            logger.warn("[getDic] nothing found for code or id {}", code);
        }

        logger.debug("[getDic list] code or id: {}, dict size {}", code, result.size());

        return result;
    }


    /**
     * 获得一个根词典的所有子项
     */
    public List<SysDic> getSysDic(String code) {
        return cacheService.getSysDicItemsByCodeAndOrgi(code, Constants.SYSTEM_ORGI);
    }

    /**
     * 获得一个词典子项
     */
    public SysDic getDicItem(String code) {
        return cacheService.findOneSysDicByCodeAndOrgi(code, Constants.SYSTEM_ORGI);
    }

    @PostConstruct
    public void setDict() {
        Dict.setDictService(this);
    }
}
