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

package com.chatopera.cc.service;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.persistence.es.QuickReplyRepository;
import com.chatopera.cc.persistence.interfaces.DataExchangeInterface;
import com.chatopera.cc.persistence.repository.ChatMessageRepository;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.mobile.MobileAddress;
import com.github.xiaobo9.commons.utils.mobile.MobileNumberUtils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

@Service
public class AgentServiceProxy {
    private final static Logger logger = LoggerFactory.getLogger(AgentServiceProxy.class);

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private AgentUserContactsRepository agentUserContactsRes;

    @Autowired
    private SNSAccountRepository snsAccountRes;

    @Autowired
    private WeiXinUserRepository weiXinUserRes;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private PbxHostRepository pbxHostRes;

    @Autowired
    private StatusEventRepository statusEventRes;

    @Autowired
    private ServiceSummaryRepository serviceSummaryRes;

    @Autowired
    private TagRepository tagRes;

    @Autowired
    private QuickTypeRepository quickTypeRes;

    @Autowired
    private QuickReplyRepository quickReplyRes;

    @Autowired
    private TagRelationRepository tagRelationRes;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ContactsRepository contactsRes;
    @Autowired
    private OnlineUserService onlineUserService;

    /**
     * ????????????
     *
     * @param agentno
     * @param orgi
     * @param agentService
     * @param map
     */
    public void processRelaData(
            final String agentno,
            final String orgi,
            final AgentService agentService,
            final ModelMap map) {
        Sort defaultSort;
        defaultSort = new Sort(Sort.Direction.DESC, "servicetime");
        map.addAttribute(
                "agentServiceList",
                agentServiceRes.findByUseridAndOrgiAndStatus(
                        agentService.getUserid(),
                        orgi,
                        AgentUserStatusEnum.END.toString(),
                        defaultSort
                )
        );

        if (StringUtils.isNotBlank(agentService.getAppid())) {
            map.addAttribute("snsAccount", snsAccountRes.findBySnsidAndOrgi(agentService.getAppid(), orgi));
        }
        agentUserContactsRes.findOneByUseridAndOrgi(
                agentService.getUserid(), agentService.getOrgi()).ifPresent(p -> {
            if (MainContext.hasModule(Constants.CSKEFU_MODULE_CONTACTS) && StringUtils.isNotBlank(
                    p.getContactsid())) {
                contactsRes.findOneById(p.getContactsid()).ifPresent(k -> {
                    map.addAttribute("contacts", k);
                });
            }
            if (MainContext.hasModule(Constants.CSKEFU_MODULE_WORKORDERS) && StringUtils.isNotBlank(
                    p.getContactsid())) {
                DataExchangeInterface dataExchange = (DataExchangeInterface) MainContext.getContext().getBean(
                        "workorders");
                if (dataExchange != null) {
                    map.addAttribute(
                            "workOrdersList",
                            dataExchange.getListDataByIdAndOrgi(p.getContactsid(), agentno, orgi));
                }
                map.addAttribute("contactsid", p.getContactsid());
            }
        });
    }


    /**
     * ???????????????????????????
     *
     * @param view
     * @param agentUser
     * @param agentService
     * @param logined      ???????????????
     */
    public void attacheChannelInfo(
            final ModelAndView view,
            final AgentUser agentUser,
            final AgentService agentService,
            final User logined) {
        if (Enums.ChannelType.WEIXIN.toString().equals(agentUser.getChannel())) {
            List<WeiXinUser> weiXinUserList = weiXinUserRes.findByOpenidAndOrgi(
                    agentUser.getUserid(), logined.getOrgi());
            if (weiXinUserList.size() > 0) {
                WeiXinUser weiXinUser = weiXinUserList.get(0);
                view.addObject("weiXinUser", weiXinUser);
            }
        } else if (Enums.ChannelType.WEBIM.toString().equals(agentUser.getChannel())) {
            OnlineUser onlineUser = onlineUserRes.findById(agentUser.getUserid()).orElse(null);
            if (onlineUser != null) {
                if (StringUtils.equals(
                        Enums.OnlineUserStatusEnum.OFFLINE.toString(), onlineUser.getStatus())) {
                    onlineUser.setBetweentime(
                            (int) (onlineUser.getUpdatetime().getTime() - onlineUser.getLogintime().getTime()));
                } else {
                    onlineUser.setBetweentime((int) (System.currentTimeMillis() - onlineUser.getLogintime().getTime()));
                }
                view.addObject("onlineUser", onlineUser);
            }
        } else if (Enums.ChannelType.PHONE.toString().equals(agentUser.getChannel())) {
            if (agentService != null && StringUtils.isNotBlank(agentService.getOwner())) {
                StatusEvent statusEvent = statusEventRes.findById(agentService.getOwner()).orElse(null);
                if (statusEvent != null) {
                    if (StringUtils.isNotBlank(statusEvent.getHostid())) {
                        pbxHostRes.findById(statusEvent.getHostid()).ifPresent(p -> {
                            view.addObject("pbxHost", p);
                        });
                    }
                    view.addObject("statusEvent", statusEvent);
                }
                MobileAddress ma = MobileNumberUtils.getAddress(agentUser.getPhone());
                view.addObject("mobileAddress", ma);
            }
        }
    }

    /**
     * ??????AgentUser???????????????????????????ModelView???
     *
     * @param view
     * @param map
     * @param agentUser
     * @param orgi
     * @param logined
     */
    public void bundleDialogRequiredDataInView(
            final ModelAndView view,
            final ModelMap map,
            final AgentUser agentUser,
            final String orgi,
            final User logined) {
        view.addObject("curagentuser", agentUser);

        CousultInvite invite = onlineUserService.consult(agentUser.getAppid(), agentUser.getOrgi());
        if (invite != null) {
            view.addObject("aisuggest", invite.isAisuggest());
            view.addObject("ccaAisuggest", invite.isAisuggest());
        }

        // ????????????
        if (StringUtils.isNotBlank(agentUser.getAppid())) {
            view.addObject("inviteData", onlineUserService.consult(agentUser.getAppid(), orgi));
            // ????????????
            if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                List<AgentServiceSummary> summarizes = serviceSummaryRes.findByAgentserviceidAndOrgi(
                        agentUser.getAgentserviceid(), orgi);
                if (summarizes.size() > 0) {
                    view.addObject("summary", summarizes.get(0));
                }
            }

            // ????????????
            view.addObject(
                    "agentUserMessageList",
                    chatMessageRepository.findByUsessionAndOrgi(agentUser.getUserid(), logined.getOrgi(),
                            PageRequest.of(0, 20, Sort.Direction.DESC,
                                    "updatetime")));

            // ??????????????????
            AgentService agentService = null;
            if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                agentService = agentServiceRes.findById(agentUser.getAgentserviceid()).orElse(null);
                view.addObject("curAgentService", agentService);
                /**
                 * ??????????????????
                 */
                if (agentService != null) {
                    processRelaData(logined.getId(), orgi, agentService, map);
                }
            }


            // ????????????
            attacheChannelInfo(view, agentUser, agentService, logined);

            // ????????????????????????
            view.addObject("serviceCount", agentServiceRes
                    .countByUseridAndOrgiAndStatus(agentUser
                                    .getUserid(), logined.getOrgi(),
                            AgentUserStatusEnum.END.toString()));
            view.addObject("tagRelationList", tagRelationRes.findByUserid(agentUser.getUserid()));
        }

        AgentService service = agentServiceRes.findByIdAndOrgi(agentUser.getAgentserviceid(), orgi);
        if (service != null) {
            view.addObject("tags", tagRes.findByOrgiAndTagtypeAndSkill(orgi, Enums.ModelType.USER.toString(), service.getSkill()));
        }
        view.addObject("quickReplyList", quickReplyRes.findByOrgiAndCreater(logined.getOrgi(), logined.getId(), null));
        List<QuickType> quickTypeList = quickTypeRes.findByOrgiAndQuicktype(
                logined.getOrgi(), Enums.QuickType.PUB.toString());
        List<QuickType> priQuickTypeList = quickTypeRes.findByOrgiAndQuicktypeAndCreater(
                logined.getOrgi(),
                Enums.QuickType.PRI.toString(),
                logined.getId());
        quickTypeList.addAll(priQuickTypeList);
        view.addObject("pubQuickTypeList", quickTypeList);
    }
}
