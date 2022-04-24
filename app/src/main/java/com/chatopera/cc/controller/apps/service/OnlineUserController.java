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
package com.chatopera.cc.controller.apps.service;

import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.basic.enums.AgentUserStatusEnum;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.model.AgentService;
import com.chatopera.cc.model.AgentServiceSummary;
import com.chatopera.cc.model.WeiXinUser;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.persistence.repository.*;
import com.chatopera.cc.util.Menu;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/service")
public class OnlineUserController extends Handler {
    private final static Logger logger = LoggerFactory.getLogger(OnlineUserController.class);

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private UserEventRepository userEventRes;

    @Autowired
    private ServiceSummaryRepository serviceSummaryRes;


    @Autowired
    private OnlineUserHisRepository onlineUserHisRes;

    @Autowired
    private WeiXinUserRepository weiXinUserRes;

    @Autowired
    private TagRepository tagRes;

    @Autowired
    private TagRelationRepository tagRelationRes;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private AgentUserContactsRepository agentUserContactsRes;

    @Autowired
    private CacheService cacheService;

    @RequestMapping("/online/index.html")
    @Menu(type = "service", subtype = "online", admin = true)
    public ModelAndView index(ModelMap map, HttpServletRequest request, String userid, String agentservice, @Valid String channel) {
        if (StringUtils.isBlank(userid)) {
            return request(super.createAppsTempletResponse("/apps/service/online/index"));
        }

        final String orgi = super.getOrgi(request);
        map.put("inviteResult", MainUtils.getWebIMInviteResult(onlineUserRes.findByOrgiAndUserid(orgi, userid)));
        map.put("tagRelationList", tagRelationRes.findByUserid(userid));
        map.put("onlineUserHistList", onlineUserHisRes.findByUseridAndOrgi(userid, orgi));
        map.put("agentServicesAvg", onlineUserRes.countByUserForAvagTime(orgi, AgentUserStatusEnum.END.toString(), userid));

        List<AgentService> agentServiceList = agentServiceRes.findByUseridAndOrgiOrderByLogindateDesc(userid, orgi);

        map.put("agentServiceList", agentServiceList);
        if (agentServiceList.size() > 0) {
            int count = this.agentServiceRes.countByUseridAndOrgiAndStatus(userid, orgi, AgentUserStatusEnum.END.toString());
            map.put("serviceCount", count);

            AgentService agentService = agentServiceList.get(0);
            if (StringUtils.isNotBlank(agentservice)) {
                for (AgentService as : agentServiceList) {
                    if (as.getId().equals(agentservice)) {
                        agentService = as;
                        break;
                    }
                }
            }

            if (agentService != null) {
                List<AgentServiceSummary> summaries = serviceSummaryRes.findByAgentserviceidAndOrgi(
                        agentService.getId(), orgi);
                if (summaries.size() > 0) {
                    map.put("summary", summaries.get(0));
                }

            }

            agentUserContactsRes.findOneByUseridAndOrgi(userid, orgi)
                    .ifPresent(p -> map.put("contacts", contactsRes.findById(p.getContactsid()).orElse(null)));

            AgentService service = agentServiceRes.findByIdAndOrgi(agentservice, orgi);
            if (service != null) {
                map.addAttribute("tags", tagRes.findByOrgiAndTagtypeAndSkill(orgi, MainContext.ModelType.USER.toString(), service.getSkill()));
            }
            map.put("summaryTags", tagRes.findByOrgiAndTagtype(orgi, MainContext.ModelType.SUMMARY.toString()));
            map.put("curAgentService", agentService);


            map.put("agentUserMessageList", chatMessageRepository.findByAgentserviceidAndOrgi(agentService.getId(), orgi,
                    PageRequest.of(0, 50, Direction.DESC, "updatetime")));
        }

        if (MainContext.ChannelType.WEIXIN.toString().equals(channel)) {
            List<WeiXinUser> weiXinUserList = weiXinUserRes.findByOpenidAndOrgi(userid, orgi);
            if (weiXinUserList.size() > 0) {
                WeiXinUser weiXinUser = weiXinUserList.get(0);
                map.put("weiXinUser", weiXinUser);
            }
        } else if (MainContext.ChannelType.WEBIM.toString().equals(channel)) {
            onlineUserRes.findById(userid).ifPresent(onlineUser -> map.put("onlineUser", onlineUser));
        }

        cacheService.findOneAgentUserByUserIdAndOrgi(userid, orgi).ifPresent(agentUser -> {
            map.put("agentUser", agentUser);
        });
        return request(super.createAppsTempletResponse("/apps/service/online/index"));
    }

    @RequestMapping("/online/chatmsg.html")
    @Menu(type = "service", subtype = "chatmsg", admin = true)
    public ModelAndView onlinechat(ModelMap map, HttpServletRequest request, String id, String title) {
        AgentService agentService = agentServiceRes.getOne(id);
        map.put("curAgentService", agentService);
        String orgi = super.getOrgi(request);
        cacheService.findOneAgentUserByUserIdAndOrgi(agentService.getUserid(), orgi)
                .ifPresent(p -> map.put("curragentuser", p));

        if (StringUtils.isNotBlank(title)) {
            map.put("title", title);
        }

        map.put("summaryTags", tagRes.findByOrgiAndTagtype(orgi, MainContext.ModelType.SUMMARY.toString()));

        List<AgentServiceSummary> summaries = serviceSummaryRes.findByAgentserviceidAndOrgi(agentService.getId(), orgi);
        if (summaries.size() > 0) {
            map.put("summary", summaries.get(0));
        }

        PageRequest pageRequest = PageRequest.of(0, 50, Direction.DESC,
                "updatetime");
        map.put("agentUserMessageList", chatMessageRepository.findByAgentserviceidAndOrgi(agentService.getId(), orgi, pageRequest));

        return request(super.pageTplResponse("/apps/service/online/chatmsg"));
    }

    @RequestMapping("/trace.html")
    @Menu(type = "service", subtype = "trace")
    public ModelAndView trace(final ModelMap map, final HttpServletRequest request,
                              final @Valid String sessionid, final @Valid String userid) {
        logger.info("[trace] online user {}, sessionid {}", userid, sessionid);
        if (StringUtils.isNotBlank(sessionid)) {
            PageRequest page = PageRequest.of(0, 100);
            map.addAttribute("traceHisList", userEventRes.findBySessionidAndOrgi(sessionid, super.getOrgi(request), page));
        }
        return request(super.pageTplResponse("/apps/service/online/trace"));
    }
}
