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
package com.chatopera.cc.controller.admin;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.service.OrganService;
import com.chatopera.cc.service.UserService;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.entity.Organ;
import com.github.xiaobo9.entity.OrganUser;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.entity.UserRole;
import com.github.xiaobo9.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/user")
public class UsersController extends Handler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRes;

    @Autowired
    OrganService organService;

    @Autowired
    UserService userService;

    @Autowired
    private OrganUserRepository organUserRes;

    @Autowired
    private PbxHostRepository pbxHostRes;

    @Autowired
    private ExtensionRepository extensionRes;

    @RequestMapping("/index.html")
    @Menu(type = "admin", subtype = "user")
    public ModelAndView index(ModelMap map, HttpServletRequest request) throws IOException {
        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(super.getOrgan(request), super.getOrgi(request));
        Page<User> users = userService.findUserInOrgans(organs.keySet(), super.page(request, Sort.Direction.ASC, "createtime"));
        map.addAttribute("userList", users);

        return request(super.createAdminTemplateResponse("/admin/user/index"));
    }

    @RequestMapping("/add.html")
    @Menu(type = "admin", subtype = "user")
    public ModelAndView add(ModelMap map, HttpServletRequest request) {
        ModelAndView view = request(super.pageTplResponse("/admin/user/add"));
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        map.addAttribute("currentOrgan", currentOrgan);
        map.addAttribute("organList", organs.values());

        return view;
    }

    @RequestMapping("/edit.html")
    @Menu(type = "admin", subtype = "user")
    public ModelAndView edit(@Valid String id) {
        ModelAndView view = request(super.pageTplResponse("/admin/user/edit"));
        User user = userRepository.findById(id).orElse(null);
        if (user != null && MainContext.hasModule(Constants.CSKEFU_MODULE_CALLCENTER)) {
            // 加载呼叫中心信息
            extensionRes.findByAgentnoAndOrgi(user.getId(), user.getOrgi()).ifPresent(p -> {
                user.setExtensionId(p.getId());
                user.setExtension(p);

                pbxHostRes.findById(p.getHostid()).ifPresent(b -> {
                    user.setPbxhostId(b.getId());
                    user.setPbxHost(b);
                });
            });
        }
        view.addObject("userData", user);
        return view;
    }

    @RequestMapping("/delete.html")
    @Menu(type = "admin", subtype = "user")
    public ModelAndView delete(@Valid User user) {
        String msg = "admin_user_delete";
        if (user != null) {
            User dbUser = userRepository.getOne(user.getId());
            if (dbUser.isSuperadmin()) {
                msg = "admin_user_abandoned";
            } else {
                // 删除用户的时候，同时删除用户对应的权限数据
                List<UserRole> userRole = userRoleRes.findByOrgiAndUser(super.getOrgi(), user);
                userRoleRes.deleteAll(userRole);
                // 删除用户对应的组织机构关系
                List<OrganUser> organUsers = organUserRes.findByUserid(user.getId());
                organUserRes.deleteAll(organUsers);

                userRepository.delete(dbUser);
            }
        } else {
            msg = "admin_user_not_exist";
        }
        return request(super.pageTplResponse("redirect:/admin/user/index.html?msg=" + msg));
    }

}