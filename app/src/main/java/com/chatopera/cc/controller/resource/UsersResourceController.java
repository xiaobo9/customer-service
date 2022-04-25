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
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.repository.OrganRepository;
import com.github.xiaobo9.repository.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

// FIXME 估计没用了
@Controller
@RequestMapping("/res")
public class UsersResourceController extends Handler {
    @Autowired
    private UserRepository userRes;

    @Autowired
    private OrganRepository organRes;

    @RequestMapping("/users.html")
    @Menu(type = "res", subtype = "users")
    public ModelAndView add(ModelMap map, @Valid String q) {
        map.addAttribute("usersList", getUsers(q));
        return request(super.pageTplResponse("/public/users"));
    }

    @RequestMapping("/bpm/users.html")
    @Menu(type = "res", subtype = "users")
    public ModelAndView bpmusers(ModelMap map, @Valid String q) {
        map.addAttribute("usersList", getUsers(q));
        return request(super.pageTplResponse("/public/bpmusers"));
    }

    @RequestMapping("/bpm/organ.html")
    @Menu(type = "res", subtype = "users")
    public ModelAndView organ(ModelMap map, HttpServletRequest request, @Valid String ids) {
        String orgi = super.getOrgi(request);
        map.addAttribute("organList", organRes.findByOrgiAndSkill(orgi, true));
        map.addAttribute("usersList", userRes.findByOrgiAndDatastatus(orgi, false));
        map.addAttribute("ids", ids);
        return request(super.pageTplResponse("/public/organ"));
    }

    /**
     * 获取当前产品下人员信息
     */
    private Page<User> getUsers(String q) {
        String query = StringUtils.defaultString(q, "");
        PageRequest pageRequest = PageRequest.of(0, 10);
        return userRes.findByDatastatusAndOrgiAndUsernameLike(false, super.getOrgi(), "%" + query + "%", pageRequest);
    }

}
