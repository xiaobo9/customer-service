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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DictService {

    private final CacheService cacheService;
    private final RedisCommand redisCommand;

    public DictService(CacheService cacheService, RedisCommand redisCommand) {
        this.cacheService = cacheService;
        this.redisCommand = redisCommand;
    }

    /**
     * 模板页面 直接拿这个当 数据字段 用，神奇的用法。。。
     *
     * @param key the key whose associated value is to be returned
     * @return SysDic or SysDic list ???
     */
    public Object get(@NotNull final String key) {
        String serialized = redisCommand.getHashKV(RedisKey.getSysDicHashKeyByOrgi(Constants.SYSTEM_ORGI), key);

        if (StringUtils.isNotBlank(serialized)) {
            Object obj = SerializeUtil.deserialize(serialized);
            if (obj instanceof List) {
                return getDic(key);
            }
            return obj;
        }

        Object result = null;
        if (key.endsWith(".subdic") && key.lastIndexOf(".subdic") > 0) {
            String id = key.substring(0, key.lastIndexOf(".subdic"));
            SysDic dic = cacheService.findOneSysDicByIdAndOrgi(id, Constants.SYSTEM_ORGI);
            if (dic != null) {
                SysDic sysDic = cacheService.findOneSysDicByIdAndOrgi(dic.getDicid(), Constants.SYSTEM_ORGI);
                result = getSubDict(sysDic.getCode(), dic.getParentid());
            }
        }
        return result;
    }

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
    public List<SysDic> getSubDict(final String code, final String parentId) {
        String serialized = redisCommand.getHashKV(RedisKey.getSysDicHashKeyByOrgi(Constants.SYSTEM_ORGI), code);
        if (StringUtils.isBlank(serialized)) {
            return Collections.emptyList();
        }
        List<SysDic> result = new ArrayList<>();
        Object obj = SerializeUtil.deserialize(serialized);
        if (obj instanceof List) {
            List<SysDic> sysDics = (List<SysDic>) obj;
            for (SysDic dic : sysDics) {
                if (dic.getParentid().equals(parentId)) {
                    result.add(dic);
                }
            }
        } else if (obj instanceof SysDic) {
            result.add((SysDic) obj);
        }
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
