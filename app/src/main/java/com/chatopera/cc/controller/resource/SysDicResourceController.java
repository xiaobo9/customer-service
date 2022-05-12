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
package com.chatopera.cc.controller.resource;

import com.chatopera.cc.controller.Handler;
import com.github.xiaobo9.service.DictService;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.entity.SysDic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/res")
public class SysDicResourceController extends Handler {
    @Autowired
    private DictService dictService;

    @RequestMapping("/dic.html")
    @Menu(type = "resouce", subtype = "dic", access = true)
    public ModelAndView index(ModelMap map, @Valid String id, @Valid String name, @Valid String attr, @Valid String style) throws IOException {
        List<SysDic> itemList = new ArrayList<>();
        SysDic sysDic = dictService.getDicItem(id);
        if (sysDic != null) {
            SysDic dic = dictService.getDicItem(sysDic.getDicid());
            List<SysDic> sysDicList = dictService.getSysDic(dic.getCode());
            for (SysDic item : sysDicList) {
                if (item.getParentid().equals(id)) {
                    itemList.add(item);
                }
            }
        }
        map.addAttribute("sysDicList", itemList);
        map.addAttribute("name", name);
        map.addAttribute("attr", attr);
        map.addAttribute("style", style);
        return request(super.pageTplResponse("/public/select"));
    }

}