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
package com.chatopera.cc.util;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.cache.RedisKey;
import com.github.xiaobo9.service.DictService;
import com.github.xiaobo9.entity.SysDic;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;

@Slf4j
public class Dict<K, V> extends HashMap<K, V> {

    private static Dict dict = new Dict();

    public static Dict getInstance() {
        return dict;
    }

    private static DictService dictService;

    public static void setDictService(DictService dictService) {
        Dict.dictService = dictService;
    }

    /**
     * 模板页面 直接拿这个当 数据字段 用，神奇的用法。。。
     *
     * @param key the key whose associated value is to be returned
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get(final Object key) {
        // TODO 从日志中看到，有时会查找key为空的调用，这是为什么？
        log.debug("[get] key {}", key);
        return (V) dictService.get(String.valueOf(key));
    }

    @NotNull
    public List<SysDic> getDic(final String code) {
        return dictService.getDic(code);
    }

    /**
     * 获得一个词典的所有子项，并且每个子项的父都是id
     */
    public List<SysDic> getDic(final String code, final String id) {
        return dictService.getSubDict(code, id);
    }


    /**
     * 获得一个根词典的所有子项
     */
    public List<SysDic> getSysDic(String code) {
        return dictService.getSysDic(code);
    }

    /**
     * 获得一个词典子项
     */
    public SysDic getDicItem(String code) {
        return dictService.getDicItem(code);
    }
}
