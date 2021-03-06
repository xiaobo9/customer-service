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
package com.chatopera.cc.controller.apps;

import com.chatopera.cc.acd.ACDWorkMonitor;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.service.OnlineUserService;
import com.chatopera.cc.service.OrganService;
import com.chatopera.cc.service.UserService;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
public class AppsController extends Handler {
    private final static Logger logger = LoggerFactory.getLogger(AppsController.class);

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    @Autowired
    private UserRepository userRes;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private UserEventRepository userEventRes;

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private OrgiSkillRelRepository orgiSkillRelService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrganService organService;

    @Autowired
    private ConsultInviteRepository invite;

    @Autowired
    private OnlineUserService onlineUserService;

    @RequestMapping({"/apps/content.html"})
    @Menu(type = "apps", subtype = "content")
    public ModelAndView content(ModelMap map, HttpServletRequest request, @Valid String msg) {
        final User user = super.getUser(request);
        final String orgi = super.getOrgi(request);
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(currentOrgan, orgi);
        List<String> appids = new ArrayList<String>();
        if (organs.size() > 0) {
            appids = invite.findSNSIdByOrgiAndSkill(orgi, organs.keySet());
        }


        /****************************
         * 获得在线访客列表
         ****************************/

//        TODO 此处为从数据库加载
        final Page<OnlineUser> onlineUserList = onlineUserRes.findByOrgiAndStatusAndAppidIn(
                super.getOrgi(request),
                Enums.OnlineUserStatusEnum.ONLINE.toString(),
                appids,
                super.page(request, Sort.Direction.DESC, "createtime")
        );

        final long msec = System.currentTimeMillis();
        final List<String> contactIds = new ArrayList<>();

        /**
         * 设置访客状态
         *
         */
        for (final OnlineUser onlineUser : onlineUserList.getContent()) {
            onlineUser.setBetweentime((int) (msec - onlineUser.getLogintime().getTime()));
            if (StringUtils.isNotBlank(onlineUser.getContactsid())) {
                contactIds.add(onlineUser.getContactsid());
            }
        }

        /**
         * 获得在线访客与联系人的关联信息
         */
        if (contactIds.size() > 0) {
            final Iterable<Contacts> contacts = contactsRes.findAllById(contactIds);
            for (final OnlineUser onlineUser : onlineUserList.getContent()) {
                if (StringUtils.isNotBlank(onlineUser.getContactsid())) {
                    for (final Contacts contact : contacts) {
                        if (StringUtils.equals(onlineUser.getContactsid(), contact.getId())) {
                            onlineUser.setContacts(contact);
                            break;
                        }
                    }
                }
            }
        }

        map.put("onlineUserList", onlineUserList);
        map.put("msg", msg);

        aggValues(map, request);

        // 获取agentStatus
        map.put("agentStatus", cacheService.findOneAgentStatusByAgentnoAndOrig(user.getId(), orgi));
        return request(super.createAppsTempletResponse("/apps/desktop/index"));
    }

    private void aggValues(ModelMap map, HttpServletRequest request) {
        Organ currentOrgan = super.getOrgan(request);
        String orgi = super.getOrgi(request);
        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(currentOrgan, orgi);

        List<Object> onlineUsers = new ArrayList<>();
        List<Object> userEvents = new ArrayList<>();
        if (organs.size() > 0) {
            List<String> appids = invite.findSNSIdByOrgiAndSkill(orgi, organs.keySet());

            if (appids.size() > 0) {
                onlineUsers = onlineUserRes.findByOrgiAndStatusAndInAppIds(
                        orgi, Enums.OnlineUserStatusEnum.ONLINE.toString(), appids);

                userEvents = userEventRes.findByOrgiAndCreatetimeRangeAndInAppIds(orgi, MainUtils.getStartTime(),
                        MainUtils.getEndTime(), appids);
            }
        }

        map.put("agentReport", acdWorkMonitor.getAgentReport(currentOrgan != null ? currentOrgan.getId() : null, orgi));
        map.put("webIMReport", MainUtils.getWebIMReport(userEvents));

        // TODO 此处为什么不用agentReport中的agents？
        map.put("agents", getUsers(request).size());

        map.put("webIMInvite", MainUtils.getWebIMInviteStatus(onlineUsers));

        User user = super.getUser(request);
        onlineUserService.onlineUserInfo(user, map, orgi);
    }

    @RequestMapping({"/apps/onlineuser.html"})
    @Menu(type = "apps", subtype = "onlineuser")
    public ModelAndView onlineuser(ModelMap map, HttpServletRequest request) {
        Page<OnlineUser> onlineUserList = this.onlineUserRes.findByOrgiAndStatus(
                super.getOrgi(request), Enums.OnlineUserStatusEnum.ONLINE.toString(),
                super.page(request, Sort.Direction.DESC, "createtime"));
        List<String> ids = new ArrayList<String>();
        for (OnlineUser onlineUser : onlineUserList.getContent()) {
            onlineUser.setBetweentime((int) (System.currentTimeMillis() - onlineUser.getLogintime().getTime()));
            if (StringUtils.isNotBlank(onlineUser.getContactsid())) {
                ids.add(onlineUser.getContactsid());
            }
        }
        if (ids.size() > 0) {
            Iterable<Contacts> contactsList = contactsRes.findAllById(ids);
            for (OnlineUser onlineUser : onlineUserList.getContent()) {
                if (StringUtils.isNotBlank(onlineUser.getContactsid())) {
                    for (Contacts contacts : contactsList) {
                        if (onlineUser.getContactsid().equals(contacts.getId())) {
                            onlineUser.setContacts(contacts);
                        }
                    }
                }
            }
        }
        map.put("onlineUserList", onlineUserList);
        aggValues(map, request);

        return request(super.createAppsTempletResponse("/apps/desktop/onlineuser"));
    }

    @RequestMapping({"/apps/profile.html"})
    @Menu(type = "apps", subtype = "content")
    public ModelAndView profile(ModelMap map, HttpServletRequest request, @Valid String index) {
        map.addAttribute("userData", super.getUser(request));
        map.addAttribute("index", index);
        return request(super.pageTplResponse("/apps/desktop/profile"));
    }

    @RequestMapping({"/apps/profile/save.html"})
    @Menu(type = "apps", subtype = "content")
    public ModelAndView profile(ModelMap map, HttpServletRequest request, @Valid User user, @Valid String index) {
        User tempUser = userRes.getOne(user.getId());
        final User logined = super.getUser(request);
        // 用户名不可修改
        user.setUsername(logined.getUsername());

        if (tempUser != null) {
            String msg = userService.validUserUpdate(user, tempUser);
            if (StringUtils.isNotBlank(msg) && (!StringUtils.equals(msg, "edit_user_success"))) {
                // 处理异常返回
                if (StringUtils.isBlank(index)) {
                    return request(super.pageTplResponse("redirect:/apps/content.html?msg=" + msg));
                }
                return request(super.pageTplResponse("redirect:/apps/tenant/index.html?msg=" + msg));
            }

            // 执行更新
            tempUser.setUname(user.getUname());
            tempUser.setEmail(user.getEmail());
            tempUser.setMobile(user.getMobile());

            if (logined.isAdmin()) {
                // 作为管理员，强制设置为坐席
                tempUser.setAgent(true);
            }

            tempUser.setOrgi(super.getOrgi());
            final Date now = new Date();
            if (StringUtils.isNotBlank(user.getPassword())) {
                tempUser.setPassword(MD5Utils.md5(user.getPassword()));
            }
            if (tempUser.getCreatetime() == null) {
                tempUser.setCreatetime(now);
            }
            tempUser.setUpdatetime(now);
            userRes.save(tempUser);
            User sessionUser = super.getUser(request);
            tempUser.setRoleList(sessionUser.getRoleList());
            tempUser.setRoleAuthMap(sessionUser.getRoleAuthMap());
            tempUser.setAffiliates(sessionUser.getAffiliates());
            User u = tempUser;
            u.setOrgi(super.getOrgi(request));
            super.setUser(request, u);
            //切换成非坐席 判断是否坐席 以及 是否有对话
            if (!user.isAgent()) {
                AgentStatus agentStatus = cacheService.findOneAgentStatusByAgentnoAndOrig(
                        (super.getUser(request)).getId(), super.getOrgi(request));

                if (!(agentStatus == null && cacheService.getInservAgentUsersSizeByAgentnoAndOrgi(
                        super.getUser(request).getId(), super.getOrgi(request)) == 0)) {
                    if (StringUtils.isBlank(index)) {
                        return request(super.pageTplResponse("redirect:/apps/content.html?msg=t1"));
                    }
                    return request(super.pageTplResponse("redirect:/apps/tenant/index.html?msg=t1"));
                }
            }

        }
        if (StringUtils.isBlank(index)) {
            return request(super.pageTplResponse("redirect:/apps/content.html"));
        }
        return request(super.pageTplResponse("redirect:/apps/tenant/index.html"));
    }

    /**
     * 获取当前产品下人员信息
     *
     * @param request
     * @return
     */
    private List<User> getUsers(HttpServletRequest request) {
        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(super.getOrgan(request), super.getOrgi(request));
        return userService.findByOrganInAndAgentAndDatastatus(organs.keySet(), true, false);
    }

}
