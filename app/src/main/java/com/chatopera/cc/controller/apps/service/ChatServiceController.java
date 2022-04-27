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

import com.chatopera.cc.acd.ACDAgentService;
import com.chatopera.cc.acd.ACDVisitorDispatcher;
import com.chatopera.cc.acd.basic.ACDComposeContext;
import com.chatopera.cc.acd.basic.ACDMessageHelper;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import com.chatopera.cc.peer.PeerSyncIM;
import com.chatopera.cc.proxy.*;
import com.chatopera.cc.socketio.message.Message;
import com.chatopera.cc.util.IP;
import com.chatopera.cc.util.IPTools;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.text.ParseException;
import java.util.*;

@Controller
@RequestMapping("/service")
public class ChatServiceController extends Handler {

    private final static Logger logger = LoggerFactory.getLogger(ChatServiceController.class);

    @Autowired
    private AgentUserProxy agentUserProxy;

    @Autowired
    private AgentStatusProxy agentStatusProxy;

    @Autowired
    private ACDAgentService acdAgentService;

    @Autowired
    private ACDVisitorDispatcher acdVisitorDispatcher;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private AgentUserRepository agentUserRes;

    @Autowired
    private AgentStatusRepository agentStatusRepository;

    @Autowired
    private AgentUserRepository agentUserRepository;

    @Autowired
    private LeaveMsgRepository leaveMsgRes;

    @Autowired
    private OrganRepository organRes;

    @Autowired
    private UserRepository userRes;

    @Autowired
    private UserProxy userProxy;

    @Autowired
    private OrganProxy organProxy;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PeerSyncIM peerSyncIM;

    @Autowired
    private ACDMessageHelper acdMessageHelper;

    @Autowired
    private LeaveMsgProxy leaveMsgProxy;

    @RequestMapping("/history/index.html")
    @Menu(type = "service", subtype = "history", admin = true)
    public ModelAndView index(ModelMap map, HttpServletRequest request, final String username, final String channel, final String servicetype, final String allocation, final String servicetimetype, final String begin, final String end) {
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        Page<AgentService> page = agentServiceRes.findAll((Specification<AgentService>) (root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            Expression<String> exp = root.get("skill");
            list.add(exp.in(organs.keySet()));
            if (StringUtils.isNotBlank(username)) {
                list.add(cb.equal(root.get("username").as(String.class), username));
            }
            if (StringUtils.isNotBlank(channel)) {
                list.add(cb.equal(root.get("channel").as(String.class), channel));
            }
            if (StringUtils.isNotBlank(servicetype) && StringUtils.isNotBlank(allocation)) {
                list.add(cb.equal(root.get(servicetype).as(String.class), allocation));
            }
            if (StringUtils.isNotBlank(servicetimetype)) {
                try {
                    if (StringUtils.isNotBlank(begin) && begin.matches("[\\d]{4}-[\\d]{2}-[\\d]{2}")) {
                        list.add(cb.greaterThanOrEqualTo(
                                root.get(servicetimetype).as(Date.class),
                                DateFormatEnum.DAY.parse(begin)));
                    }
                    if (StringUtils.isNotBlank(end) && end.matches("[\\d]{4}-[\\d]{2}-[\\d]{2}")) {
                        list.add(cb.lessThanOrEqualTo(
                                root.get(servicetimetype).as(Date.class),
                                DateFormatEnum.DAY_TIME.parse(end + " 23:59:59")));
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            Predicate[] p = new Predicate[list.size()];
            return cb.and(list.toArray(p));
        }, super.page(request, Direction.DESC, "createtime"));
        map.put("agentServiceList", page);
        map.put("username", username);
        map.put("channel", channel);
        map.put("servicetype", servicetype);
        map.put("servicetimetype", servicetimetype);
        map.put("allocation", allocation);
        map.put("begin", begin);
        map.put("end", end);
        map.put("deptlist", organs.values());
        map.put("userlist", userProxy.findUserInOrgans(organs.keySet()));

        return request(super.createAppsTempletResponse("/apps/service/history/index"));
    }

    @RequestMapping("/current/index.html")
    @Menu(type = "service", subtype = "current", admin = true)
    public ModelAndView current(ModelMap map, HttpServletRequest request) {
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        map.put("agentServiceList", agentServiceRes.findByOrgiAndStatusAndAgentskillIn(
                super.getOrgi(request),
                AgentUserStatusEnum.INSERVICE.toString(),
                organs.keySet(),
                super.page(request, Direction.DESC, "createtime")));

        return request(super.createAppsTempletResponse("/apps/service/current/index"));
    }

    @RequestMapping("/current/trans.html")
    @Menu(type = "service", subtype = "current", admin = true)
    public ModelAndView trans(ModelMap map, HttpServletRequest request, @Valid String id) {
        Organ targetOrgan = super.getOrgan(request);
        final String orgi = super.getOrgi(request);
        final User logined = super.getUser(request);
        Map<String, Organ> ownOrgans = organProxy.findAllOrganByParentAndOrgi(targetOrgan, super.getOrgi(request));

        if (StringUtils.isNotBlank(id)) {
            AgentService agentService = agentServiceRes.findByIdAndOrgi(id, super.getOrgi(request));
            List<Organ> skillGroups = organRes.findByOrgiAndIdInAndSkill(super.getOrgi(request), ownOrgans.keySet(), true);
            Set<String> organs = ownOrgans.keySet();
            String currentOrgan = agentService.getSkill();

            if (StringUtils.isBlank(currentOrgan)) {
                if (!skillGroups.isEmpty()) {
                    currentOrgan = skillGroups.get(0).getId();
                }
            }
            final Map<String, AgentStatus> agentStatusMap = cacheService.findAllReadyAgentStatusByOrgi(orgi);
            List<String> usersids = new ArrayList<String>();
            for (final String o : agentStatusMap.keySet()) {
                if (!StringUtils.equals(o, agentService.getAgentno())) {
                    usersids.add(o);
                }
            }
            List<User> userList = userRes.findAllById(usersids);
            for (User user : userList) {
                user.setAgentStatus(cacheService.findOneAgentStatusByAgentnoAndOrig(user.getId(), super.getOrgi(request)));
                userProxy.attachOrgansPropertiesForUser(user);
            }
            map.addAttribute("userList", userList);
            map.addAttribute("userid", agentService.getUserid());
            map.addAttribute("agentserviceid", agentService.getId());
            map.addAttribute("agentuserid", agentService.getAgentuserid());
            map.addAttribute("agentservice", agentService);
            map.addAttribute("skillGroups", skillGroups);
            map.addAttribute("currentorgan", currentOrgan);
        }

        return request(super.pageTplResponse("/apps/service/current/transfer"));
    }

    @RequestMapping(value = "/transfer/save.html")
    @Menu(type = "apps", subtype = "transfersave")
    public ModelAndView transfersave(HttpServletRequest request, @Valid String id, @Valid String agentno, @Valid String memo) {
        if (StringUtils.isNotBlank(id)) {
            AgentService agentService = agentServiceRes.findByIdAndOrgi(id, super.getOrgi(request));
            final User targetAgent = userRes.findById(agentno).orElseThrow(EntityNotFoundEx::new);
            AgentUser agentUser = null;
            Optional<AgentUser> agentUserOpt = cacheService.findOneAgentUserByUserIdAndOrgi(agentService.getUserid(), super.getOrgi(request));
            if (agentUserOpt.isPresent()) {
                agentUser = agentUserOpt.get();
            }

            if (agentUser != null) {
                agentUser.setAgentno(agentno);
                agentUser.setAgentname(targetAgent.getUname());
                agentUserRepository.save(agentUser);
                if (AgentUserStatusEnum.INSERVICE.toString().equals(
                        agentUser.getStatus())) {
                    // 转接 ， 发送消息给 目标坐席
                    AgentStatus agentStatus = cacheService.findOneAgentStatusByAgentnoAndOrig(
                            super.getUser(request).getId(), super.getOrgi(request));

                    if (agentStatus != null) {
                        agentUserProxy.updateAgentStatus(agentStatus, super.getOrgi(request));
                    }

                    AgentStatus transAgentStatus = cacheService.findOneAgentStatusByAgentnoAndOrig(
                            agentno, super.getOrgi(request));
                    if (transAgentStatus != null) {
                        agentUserProxy.updateAgentStatus(transAgentStatus, super.getOrgi(request));
                        agentService.setAgentno(agentno);
                        agentService.setAgentusername(transAgentStatus.getUsername());
                    }
                    // 转接坐席提示消息
                    try {
                        Message outMessage = new Message();
                        outMessage.setMessage(
                                acdMessageHelper.getSuccessMessage(agentService, agentUser.getChannel(), super.getOrgi(request)));
                        outMessage.setMessageType(Enums.MediaType.TEXT.toString());
                        outMessage.setCalltype(Enums.CallType.IN.toString());
                        outMessage.setCreatetime(DateFormatEnum.DAY_TIME.format(new Date()));
                        outMessage.setAgentUser(agentUser);
                        outMessage.setAgentService(agentService);

                        if (org.apache.commons.lang.StringUtils.isNotBlank(agentUser.getUserid())) {
                            peerSyncIM.send(
                                    Enums.ReceiverType.VISITOR,
                                    Enums.ChannelType.toValue(agentUser.getChannel()),
                                    agentUser.getAppid(),
                                    Enums.MessageType.STATUS,
                                    agentUser.getUserid(),
                                    outMessage,
                                    true
                            );
                        }

                        // 通知转接消息给新坐席
                        outMessage.setChannelMessage(agentUser);
                        outMessage.setAgentUser(agentUser);
                        peerSyncIM.send(
                                Enums.ReceiverType.AGENT, Enums.ChannelType.WEBIM,
                                agentUser.getAppid(), Enums.MessageType.NEW, agentService.getAgentno(),
                                outMessage, true
                        );

                    } catch (Exception ex) {
                        logger.error("[transfersave]", ex);
                    }
                }
            } else {
                agentUser = agentUserRepository.findByIdAndOrgi(agentService.getAgentuserid(), super.getOrgi(request));
                if (agentUser != null) {
                    agentUser.setAgentno(agentno);
                    agentUser.setAgentname(targetAgent.getUname());
                    agentUserRepository.save(agentUser);
                }
            }

            if (agentService != null) {
                agentService.setAgentno(agentno);
                if (StringUtils.isNotBlank(memo)) {
                    agentService.setTransmemo(memo);
                }
                agentService.setTrans(true);
                agentService.setTranstime(new Date());
                agentServiceRes.save(agentService);
            }
        }

        return request(super.pageTplResponse("redirect:/service/current/index.html"));
    }

    @RequestMapping("/current/end.html")
    @Menu(type = "service", subtype = "current", admin = true)
    public ModelAndView end(ModelMap map, HttpServletRequest request, @Valid String id) throws Exception {
        if (StringUtils.isNotBlank(id)) {
            AgentService agentService = agentServiceRes.findByIdAndOrgi(id, super.getOrgi(request));
            if (agentService != null) {
                User user = super.getUser(request);
                AgentUser agentUser = agentUserRepository.findByIdAndOrgi(
                        agentService.getAgentuserid(), super.getOrgi(request));
                if (agentUser != null) {
                    acdAgentService.finishAgentUser(agentUser, user.getOrgi());
                }
                agentService.setStatus(AgentUserStatusEnum.END.toString());
                agentServiceRes.save(agentService);
            }
        }
        return request(super.pageTplResponse("redirect:/service/current/index.html"));
    }

    /**
     * 邀请
     *
     * @param map
     * @param request
     * @param id
     * @return
     * @throws Exception
     */
    @RequestMapping("/current/invite.html")
    @Menu(type = "service", subtype = "current", admin = true)
    public ModelAndView currentinvite(
            ModelMap map,
            final HttpServletRequest request,
            final @Valid String id) throws Exception {
        if (StringUtils.isNotBlank(id)) {
            AgentService agentService = agentServiceRes.findByIdAndOrgi(id, super.getOrgi(request));
            if (agentService != null) {
                final User user = super.getUser(request);
                if (StringUtils.isBlank(agentService.getAgentno())) {

                    // 将AiUser替换为OnlineUser
                    // TODO #153 https://gitlab.chatopera.com/chatopera/cosinee/issues/153
                    //  此处可能会有逻辑问题，从而导致BUG
                    // AiUser 定义参考
                    // https://gitlab.chatopera.com/chatopera/cosinee.w4l/blob/2ea2ad5cad92d2d9f4ceb88e9608c7019495ccf5/contact-center/app/src/main/java/com/chatopera/cc/app/model/AiUser.java
                    // 需要做更多测试
                    OnlineUser onlineUser = cacheService.findOneOnlineUserByUserIdAndOrgi(
                            agentService.getUserid(), agentService.getOrgi());

                    if (onlineUser != null) {
                        IP ipdata = IPTools.findGeography(onlineUser.getIp());
                        acdVisitorDispatcher.enqueue(ACDMessageHelper.getWebIMComposeContext(
                                onlineUser.getUserid(),
                                onlineUser.getUsername(),
                                user.getOrgi(),
                                agentService.getSessionid(),
                                agentService.getAppid(),
                                agentService.getIpaddr(),
                                agentService.getOsname(),
                                agentService.getBrowser(),
                                "",
                                ipdata,
                                agentService.getChannel(),
                                null, // 此处绑定坐席，不指定技能组
                                user.getId(),
                                null,
                                null,
                                agentService.getContactsid(),
                                onlineUser.getOwner(),
                                true,
                                Enums.ChatInitiatorType.AGENT.toString()));
                    }
                }
            }
        }
        return request(super.pageTplResponse("redirect:/service/current/index.html"));
    }


    @RequestMapping("/quene/index.html")
    @Menu(type = "service", subtype = "filter", admin = true)
    public ModelAndView quene(ModelMap map, HttpServletRequest request) {
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        Page<AgentUser> agentUserList = agentUserRes.findByOrgiAndStatusAndSkillIn(
                super.getOrgi(request), AgentUserStatusEnum.INQUENE.toString(), organs.keySet(),
                super.page(request, Direction.DESC, "createtime"));
        List<String> skillGroups = new ArrayList<String>();
        for (AgentUser agentUser : agentUserList.getContent()) {
            agentUser.setWaittingtime((int) (System.currentTimeMillis() - agentUser.getCreatetime().getTime()));
            if (StringUtils.isNotBlank(agentUser.getSkill())) {
                skillGroups.add(agentUser.getSkill());
            }
        }
        if (skillGroups.size() > 0) {
            List<Organ> organList = organRes.findAllById(skillGroups);
            for (AgentUser agentUser : agentUserList.getContent()) {
                if (StringUtils.isNotBlank(agentUser.getSkill())) {
                    for (Organ organ : organList) {
                        if (agentUser.getSkill().equals(organ.getId())) {
                            agentUser.setSkillname(organ.getName());
                            break;
                        }
                    }
                }
            }
        }
        map.put("agentUserList", agentUserList);

        return request(super.createAppsTempletResponse("/apps/service/quene/index"));
    }

    @RequestMapping("/quene/transfer.html")
    @Menu(type = "service", subtype = "quenetransfer", admin = true)
    public ModelAndView transfer(ModelMap map, HttpServletRequest request, @Valid String id, @Valid String skillid) {

        Organ targetOrgan = super.getOrgan(request);
        Map<String, Organ> ownOrgans = organProxy.findAllOrganByParentAndOrgi(targetOrgan, super.getOrgi(request));

        if (StringUtils.isNotBlank(id)) {
            List<Organ> skillGroups = organRes.findByOrgiAndIdInAndSkill(super.getOrgi(request), ownOrgans.keySet(), true);
            Set<String> organs = ownOrgans.keySet();
            String currentOrgan = organs.size() > 0 ? (new ArrayList<>(organs)).get(0) : null;

            if (StringUtils.isBlank(currentOrgan)) {
                if (!skillGroups.isEmpty()) {
                    currentOrgan = skillGroups.get(0).getId();
                }
            }
            List<AgentStatus> agentStatusList = cacheService.getAgentStatusBySkillAndOrgi(null, super.getOrgi(request));
            List<String> usersids = new ArrayList<String>();
            if (!agentStatusList.isEmpty()) {
                for (AgentStatus agentStatus : agentStatusList) {
                    if (agentStatus != null) {
                        usersids.add(agentStatus.getAgentno());
                    }
                }
            }
            List<User> userList = userRes.findAllById(usersids);
            for (User user : userList) {
                user.setAgentStatus(cacheService.findOneAgentStatusByAgentnoAndOrig(user.getId(), super.getOrgi(request)));
                userProxy.attachOrgansPropertiesForUser(user);
            }
            map.put("id", id);
            map.put("skillid", skillid);
            map.addAttribute("userList", userList);
            map.addAttribute("skillGroups", skillGroups);
            map.addAttribute("currentorgan", currentOrgan);
        }
        return request(super.pageTplResponse("/apps/service/quene/transfer"));
    }

    @RequestMapping("/quene/transfer/save.html")
    @Menu(type = "service", subtype = "quenetransfer", admin = true)
    public ModelAndView queueTransferSave(ModelMap map, HttpServletRequest request, @Valid String id, @Valid String skillid) {
        AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, super.getOrgi(request));
        if (agentUser != null && agentUser.getStatus().equals(AgentUserStatusEnum.INQUENE.toString())) {
            agentUser.setAgentno(null);
            agentUser.setSkill(skillid);
            agentUserRes.save(agentUser);
            ACDComposeContext ctx = acdMessageHelper.getComposeContextWithAgentUser(
                    agentUser, false, Enums.ChatInitiatorType.USER.toString());
            acdVisitorDispatcher.enqueue(ctx);
        }
        return request(super.pageTplResponse("redirect:/service/quene/index.html"));
    }

    @RequestMapping("/quene/invite.html")
    @Menu(type = "service", subtype = "invite", admin = true)
    public ModelAndView invite(ModelMap map, HttpServletRequest request, @Valid String id) throws Exception {
        final User logined = super.getUser(request);
        final String orgi = logined.getOrgi();
        AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, super.getOrgi(request));
        if (agentUser != null && agentUser.getStatus().equals(AgentUserStatusEnum.INQUENE.toString())) {
            acdAgentService.assignVisitorAsInvite(logined.getId(), agentUser, orgi);
        }
        return request(super.pageTplResponse("redirect:/service/quene/index.html"));
    }

    /**
     * 管理员查看在线坐席
     *
     * @param map
     * @param request
     * @return
     */
    @RequestMapping("/agent/index.html")
    @Menu(type = "service", subtype = "onlineagent", admin = true)
    public ModelAndView agent(ModelMap map, HttpServletRequest request) {
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        final Map<String, AgentStatus> ass = cacheService.findAllAgentStatusByOrgi(super.getOrgi(request));
        List<AgentStatus> lis = new ArrayList<>();
        List<User> users = userProxy.findUserInOrgans(organs.keySet());
        if (users != null) {
            for (User us : users) {
                if (ass.containsKey(us.getId())) {
                    lis.add(ass.get(us.getId()));
                }
            }
        }
        map.put("agentStatusList", lis);
        return request(super.createAppsTempletResponse("/apps/service/agent/index"));
    }

    /**
     * 查看离线坐席
     *
     * @param map
     * @param request
     * @param id
     * @return
     */
    @RequestMapping("/agent/offline.html")
    @Menu(type = "service", subtype = "offline", admin = true)
    public ModelAndView offline(ModelMap map, HttpServletRequest request, @Valid String id) {

        AgentStatus agentStatus = agentStatusRepository.findByIdAndOrgi(id, super.getOrgi(request));
        if (agentStatus != null) {
            agentStatusRepository.delete(agentStatus);
        }
        cacheService.deleteAgentStatusByAgentnoAndOrgi(agentStatus.getAgentno(), super.getOrgi(request));

        agentStatusProxy.broadcastAgentsStatus(
                super.getOrgi(request), "agent", "offline", super.getUser(request).getId());

        return request(super.pageTplResponse("redirect:/service/agent/index.html"));
    }

    /**
     * 非管理员坐席
     *
     * @param map
     * @param request
     * @return
     */
    @RequestMapping("/user/index.html")
    @Menu(type = "service", subtype = "userlist", admin = true)
    public ModelAndView user(ModelMap map, HttpServletRequest request) {
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        Page<User> userList = userProxy.findUserInOrgans(organs.keySet(), super.page(request,
                Direction.DESC, "createtime"));
        Map<String, Boolean> onlines = new HashMap<>();
        if (userList != null) {
            for (User user : userList.getContent()) {
                if (cacheService.findOneAgentStatusByAgentnoAndOrig(user.getId(), super.getOrgi(request)) != null) {
                    onlines.put(user.getId(), true);
                } else {
                    onlines.put(user.getId(), false);
                }
            }
        }

        map.put("userList", userList);
        map.put("onlines", onlines);
        return request(super.createAppsTempletResponse("/apps/service/user/index"));
    }

    @RequestMapping("/leavemsg/index.html")
    @Menu(type = "service", subtype = "leavemsg", admin = true)
    public ModelAndView leavemsg(ModelMap map, HttpServletRequest request) {
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));

        Page<LeaveMsg> leaveMsgs = leaveMsgRes.findBySkillAndOrgi(organs.keySet(), super.getOrgi(request), super.page(request,
                Direction.DESC, "createtime"));
        logger.info("[leavemsg] current organ {}, find message size {}", currentOrgan.getId(), leaveMsgs.getSize());
        for (final LeaveMsg l : leaveMsgs) {
            leaveMsgProxy.resolveChannelBySnsid(l);
        }

        map.put("leaveMsgList", leaveMsgs);
        return request(super.createAppsTempletResponse("/apps/service/leavemsg/index"));
    }

    @RequestMapping("/leavemsg/delete.html")
    @Menu(type = "service", subtype = "leavemsg", admin = true)
    public ModelAndView leavemsg(ModelMap map, HttpServletRequest request, @Valid String id) {
        if (StringUtils.isNotBlank(id)) {
            leaveMsgRes.deleteById(id);
        }
        return request(super.pageTplResponse("redirect:/service/leavemsg/index.html"));
    }
}
