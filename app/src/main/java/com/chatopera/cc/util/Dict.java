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

import com.github.xiaobo9.service.DictService;
import com.github.xiaobo9.entity.SysDic;

import javax.validation.constraints.NotNull;
import java.util.List;

// FIXME 换个地方吧
public class Dict {

    private static Dict dict = new Dict();

    public static Dict getInstance() {
        return dict;
    }

    private static DictService dictService;

    public static void setDictService(DictService dictService) {
        Dict.dictService = dictService;
    }


    @NotNull
    public List<SysDic> getDic(final String code) {
        return dictService.getDic(code);
    }

    /**
     * 获得一个词典的所有子项，并且每个子项的父都是id
     */
    public List<SysDic> getDic(final String code, final String id) {
        return dictService.getDic(code, id);
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
