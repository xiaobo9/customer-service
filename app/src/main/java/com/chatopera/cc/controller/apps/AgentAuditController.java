/*
 * Copyright (C) 2019 Chatopera Inc, <https://www.chatopera.com>
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

import com.alibaba.fastjson.JSONObject;
import com.chatopera.cc.acd.ACDAgentService;
import com.chatopera.cc.acd.basic.ACDMessageHelper;
import com.chatopera.cc.activemq.BrokerPublisher;
import com.chatopera.cc.activemq.MqMessage;
import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.service.*;
import com.github.xiaobo9.commons.exception.ServerException;
import com.chatopera.cc.peer.PeerSyncIM;
import com.chatopera.cc.persistence.repository.ChatMessageRepository;
import com.chatopera.cc.socketio.message.Message;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import com.github.xiaobo9.service.BlackEntityService;
import freemarker.template.TemplateException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping(value = "/apps/cca")
public class AgentAuditController extends Handler {
    private final static Logger logger = LoggerFactory.getLogger(AgentAuditController.class);

    @Autowired
    private AgentUserService agentUserService;

    @Autowired
    private ACDMessageHelper acdMessageHelper;

    @Autowired
    private AgentUserRepository agentUserRes;

    @Autowired
    private OrganRepository organRes;

    @Autowired
    private UserRepository userRes;

    @Autowired
    private AgentUserRepository agentUserRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private AgentUserTaskRepository agentUserTaskRes;

    @Autowired
    private ServiceSummaryRepository serviceSummaryRes;

    @Autowired
    private UserService userService;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private TagRepository tagRes;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PeerSyncIM peerSyncIM;

    @Autowired
    private TagRelationRepository tagRelationRes;

    @Autowired
    private BlackEntityService blackEntityService;

    @Autowired
    private BrokerPublisher brokerPublisher;

    @Autowired
    private AgentServiceProxy agentServiceProxy;

    @Autowired
    private ACDAgentService acdAgentService;

    @Autowired
    private OrganService organService;

    @Autowired
    private OnlineUserService onlineUserService;

    @RequestMapping(value = "/index.html")
    @Menu(type = "cca", subtype = "cca", access = true)
    public ModelAndView index(
            ModelMap map,
            HttpServletRequest request,
            @Valid final String skill,
            @Valid final String agentno,
            @Valid String sort
    ) {
        final String orgi = super.getOrgi(request);
        final User logined = super.getUser(request);
        logger.info("[index] skill {}, agentno {}, logined {}", skill, agentno, logined.getId());

        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(super.getOrgan(request), super.getOrgi(request));

        ModelAndView view = request(super.createAppsTempletResponse("/apps/cca/index"));
        Sort defaultSort = null;

        if (StringUtils.isNotBlank(sort)) {
            List<Sort.Order> criterias = new ArrayList<>();
            if (sort.equals("lastmessage")) {
                criterias.add(new Sort.Order(Sort.Direction.DESC, "status"));
                criterias.add(new Sort.Order(Sort.Direction.DESC, "lastmessage"));
            } else if (sort.equals("logintime")) {
                criterias.add(new Sort.Order(Sort.Direction.DESC, "status"));
                criterias.add(new Sort.Order(Sort.Direction.DESC, "createtime"));
            } else if (sort.equals("default")) {
                defaultSort = new Sort(Sort.Direction.DESC, "status");
            }
            if (criterias.size() > 0) {
                defaultSort = new Sort(criterias);
                map.addAttribute("sort", sort);
            }
        } else {
            defaultSort = new Sort(Sort.Direction.DESC, "status");
        }

        // ??????????????????
        List<AgentUser> agentUsers = new ArrayList<>();

        if (StringUtils.isBlank(skill) && StringUtils.isBlank(agentno)) {
            if (organs.size() > 0) {
                agentUsers = agentUserRes.findByOrgiAndStatusAndSkillInAndAgentnoIsNot(
                        orgi, AgentUserStatusEnum.INSERVICE.toString(), organs.keySet(), logined.getId(), defaultSort);
            }
        } else if (StringUtils.isNotBlank(skill) && StringUtils.isNotBlank(agentno)) {
            view.addObject("skill", skill);
            view.addObject("agentno", agentno);
            agentUsers = agentUserRes.findByOrgiAndStatusAndSkillAndAgentno(
                    orgi, AgentUserStatusEnum.INSERVICE.toString(), skill, agentno, defaultSort);
        } else if (StringUtils.isNotBlank(skill)) {
            view.addObject("skill", skill);
            agentUsers = agentUserRes.findByOrgiAndStatusAndSkillAndAgentnoIsNot(
                    orgi, AgentUserStatusEnum.INSERVICE.toString(), skill, agentno, defaultSort);
        } else {
            // agent is not Blank
            view.addObject("agentno", agentno);
            agentUsers = agentUserRes.findByOrgiAndStatusAndAgentno(
                    orgi, AgentUserStatusEnum.INSERVICE.toString(), agentno, defaultSort);
        }

        logger.info("[index] agent users size: {}", agentUsers.size());

        if (agentUsers.size() > 0) {
            view.addObject("agentUserList", agentUsers);

            /**
             * ????????????
             */
            final AgentUser currentAgentUser = agentUsers.get(0);
            agentServiceProxy.bundleDialogRequiredDataInView(view, map, currentAgentUser, orgi, logined);
        }

        // ?????????????????????
        List<Organ> skills = organRes.findByOrgiAndSkill(orgi, true);
        List<User> agents = userRes.findByOrgiAndAgentAndDatastatusAndIdIsNot(orgi, true, false, logined.getId());

        view.addObject("skillGroups", skills.stream().filter(s -> organs.containsKey(s.getId())).collect(Collectors.toList()));
        view.addObject("agentList", agents);

        return view;
    }

    @RequestMapping("/query.html")
    @Menu(type = "apps", subtype = "cca")
    public ModelAndView query(HttpServletRequest request, String skill, String agentno) {
        ModelAndView view = request(super.pageTplResponse("/apps/cca/chatusers"));

        final String orgi = super.getOrgi(request);
        final User logined = super.getUser(request);

        Sort defaultSort = new Sort(Sort.Direction.DESC, "status");

        // ??????????????????
        List<AgentUser> agentUsers;

        if (StringUtils.isBlank(skill) && StringUtils.isBlank(agentno)) {
            agentUsers = agentUserRes.findByOrgiAndStatusAndAgentnoIsNot(
                    orgi, AgentUserStatusEnum.INSERVICE.toString(), logined.getId(), defaultSort);
        } else if (StringUtils.isNotBlank(skill) && StringUtils.isNotBlank(agentno)) {
            agentUsers = agentUserRes.findByOrgiAndStatusAndSkillAndAgentno(
                    orgi, AgentUserStatusEnum.INSERVICE.toString(), skill, agentno, defaultSort);
        } else if (StringUtils.isNotBlank(skill)) {
            agentUsers = agentUserRes.findByOrgiAndStatusAndSkillAndAgentnoIsNot(
                    orgi, AgentUserStatusEnum.INSERVICE.toString(), skill, agentno, defaultSort);
        } else {
            // agent is not Blank
            agentUsers = agentUserRes.findByOrgiAndStatusAndAgentno(
                    orgi, AgentUserStatusEnum.INSERVICE.toString(), agentno, defaultSort);
        }

        view.addObject("agentUserList", agentUsers);

        return view;
    }

    @RequestMapping("/agentusers.html")
    @Menu(type = "apps", subtype = "cca")
    public ModelAndView agentusers(HttpServletRequest request, String userid) {
        ModelAndView view = request(super.pageTplResponse("/apps/cca/agentusers"));
        User logined = super.getUser(request);
        final String orgi = super.getOrgi(request);
        Sort defaultSort = new Sort(Sort.Direction.DESC, "status");
        view.addObject(
                "agentUserList", agentUserRes.findByOrgiAndStatusAndAgentnoIsNot(
                        orgi, AgentUserStatusEnum.INSERVICE.toString(), logined.getId(), defaultSort));
        List<AgentUser> agentUserList = agentUserRepository.findByUseridAndOrgi(userid, logined.getOrgi());
        view.addObject(
                "curagentuser", agentUserList != null && agentUserList.size() > 0 ? agentUserList.get(0) : null);

        return view;
    }

    @RequestMapping("/agentuser.html")
    @Menu(type = "apps", subtype = "cca")
    public ModelAndView agentuser(
            ModelMap map,
            HttpServletRequest request,
            String id,
            String channel
    ) throws IOException, TemplateException {
        String mainagentuser = "/apps/cca/mainagentuser";
        if (channel.equals("phone")) {
            mainagentuser = "/apps/cca/mainagentuser_callout";
        }
        ModelAndView view = request(super.pageTplResponse(mainagentuser));
        final User logined = super.getUser(request);
        final String orgi = logined.getOrgi();
        AgentUser agentUser = agentUserRepository.findByIdAndOrgi(id, orgi);

        if (agentUser != null) {
            view.addObject("curagentuser", agentUser);

            CousultInvite invite = onlineUserService.consult(agentUser.getAppid(), agentUser.getOrgi());
            if (invite != null) {
                view.addObject("ccaAisuggest", invite.isAisuggest());
            }
            view.addObject("inviteData", onlineUserService.consult(agentUser.getAppid(), agentUser.getOrgi()));
            List<AgentUserTask> agentUserTaskList = agentUserTaskRes.findByIdAndOrgi(id, orgi);
            if (agentUserTaskList.size() > 0) {
                AgentUserTask agentUserTask = agentUserTaskList.get(0);
                agentUserTask.setTokenum(0);
                agentUserTaskRes.save(agentUserTask);
            }

            if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                List<AgentServiceSummary> summarizes = this.serviceSummaryRes.findByAgentserviceidAndOrgi(agentUser.getAgentserviceid(), orgi);
                if (summarizes.size() > 0) {
                    view.addObject("summary", summarizes.get(0));
                }
            }

            view.addObject("agentUserMessageList", this.chatMessageRepository.findByUsessionAndOrgi(agentUser.getUserid(), orgi,
                    super.page(request, Sort.Direction.DESC, "updatetime")));
            AgentService agentService = null;
            if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                agentService = this.agentServiceRes.findById(agentUser.getAgentserviceid()).orElse(null);
                view.addObject("curAgentService", agentService);
                if (agentService != null) {
                    /**
                     * ??????????????????
                     */
                    agentServiceProxy.processRelaData(logined.getId(), orgi, agentService, map);
                }
            }
            if (Enums.ChannelType.WEBIM.toString().equals(agentUser.getChannel())) {
                OnlineUser onlineUser = onlineUserRes.findById(agentUser.getUserid()).orElse(null);
                if (onlineUser != null) {
                    if (onlineUser.getLogintime() != null) {
                        if (Enums.OnlineUserStatusEnum.OFFLINE.toString().equals(onlineUser.getStatus())) {
                            onlineUser.setBetweentime(
                                    (int) (onlineUser.getUpdatetime().getTime() - onlineUser.getLogintime().getTime()));
                        } else {
                            onlineUser.setBetweentime(
                                    (int) (System.currentTimeMillis() - onlineUser.getLogintime().getTime()));
                        }
                    }
                    view.addObject("onlineUser", onlineUser);
                }
            }
            view.addObject("serviceCount", Integer
                    .valueOf(this.agentServiceRes
                            .countByUseridAndOrgiAndStatus(agentUser
                                            .getUserid(), orgi,
                                    AgentUserStatusEnum.END
                                            .toString())));
            view.addObject("tagRelationList", tagRelationRes.findByUserid(agentUser.getUserid()));

//        TODO: mdx-organ clean
//        SessionConfig sessionConfig = acdPolicyService.initSessionConfig(super.getOrgi(request));
//
//        view.addObject("sessionConfig", sessionConfig);
//        if (sessionConfig.isOtherquickplay()) {
//            view.addObject("topicList", onlineUserProxy.search(null, orgi, super.getUser(request)));
//        }
            AgentService service = agentServiceRes.findByIdAndOrgi(agentUser.getAgentserviceid(), orgi);
            if (service != null) {
                view.addObject("tags", tagRes.findByOrgiAndTagtypeAndSkill(orgi, Enums.ModelType.USER.toString(), service.getSkill()));
            }
        }
        return view;
    }


    /**
     * ??????????????????
     *
     * @param map
     * @param request
     * @param userid
     * @param agentserviceid
     * @param agentuserid
     * @return
     */
    @RequestMapping(value = "/transfer.html")
    @Menu(type = "apps", subtype = "transfer")
    public ModelAndView transfer(
            ModelMap map,
            final HttpServletRequest request,
            final @Valid String userid,
            final @Valid String agentserviceid,
            final @Valid String agentnoid,
            final @Valid String agentuserid
    ) {
        logger.info("[transfer] userId {}, agentUser {}", userid, agentuserid);
        final String orgi = super.getOrgi(request);
        final User logined = super.getUser(request);

        Organ targetOrgan = super.getOrgan(request);
        Map<String, Organ> ownOrgans = organService.findAllOrganByParentAndOrgi(targetOrgan, super.getOrgi(request));
        if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(agentuserid)) {
            // ?????????????????????
            List<Organ> skillGroups = organRes.findByOrgiAndIdInAndSkill(super.getOrgi(request), ownOrgans.keySet(), true);

            // ????????????????????????????????????
            AgentService agentService = agentServiceRes.findByIdAndOrgi(agentserviceid, super.getOrgi(request));

            String currentOrgan = agentService.getSkill();

            if (StringUtils.isBlank(currentOrgan)) {
                if (!skillGroups.isEmpty()) {
                    currentOrgan = skillGroups.get(0).getId();
                }
            }

            // ??????????????????????????????????????????
            List<String> userids = new ArrayList<>();
            final Map<String, AgentStatus> agentStatusMap = cacheService.findAllReadyAgentStatusByOrgi(orgi);

            for (final String o : agentStatusMap.keySet()) {
                if (!StringUtils.equals(o, agentnoid)) {
                    userids.add(o);
                }
            }

            final List<User> userList = userRes.findAllById(userids);
            for (final User o : userList) {
                o.setAgentStatus(agentStatusMap.get(o.getId()));
                // find user's skills
                userService.attachOrgansPropertiesForUser(o);
            }

            map.addAttribute("userList", userList);
            map.addAttribute("userid", userid);
            map.addAttribute("agentserviceid", agentserviceid);
            map.addAttribute("agentuserid", agentuserid);
            map.addAttribute("agentno", agentnoid);
            map.addAttribute("skillGroups", skillGroups);
            map.addAttribute("agentservice", this.agentServiceRes.findByIdAndOrgi(agentserviceid, orgi));
            map.addAttribute("currentorgan", currentOrgan);
        }

        return request(super.pageTplResponse("/apps/cca/transfer"));
    }


    /**
     * ??????????????????????????????????????????
     *
     * @param map
     * @param request
     * @param organ
     * @return
     */
    @RequestMapping(value = "/transfer/agent.html")
    @Menu(type = "apps", subtype = "transferagent")
    public ModelAndView transferagent(
            ModelMap map,
            HttpServletRequest request,
            @Valid String agentid,
            @Valid String organ
    ) {
        final User logined = super.getUser(request);
        final String orgi = super.getOrgi(request);
        if (StringUtils.isNotBlank(organ)) {
            List<String> userids = new ArrayList<>();

            final Map<String, AgentStatus> agentStatusMap = cacheService.findAllReadyAgentStatusByOrgi(orgi);

            for (final String o : agentStatusMap.keySet()) {
                if (!StringUtils.equals(o, agentid)) {
                    userids.add(o);
                }
            }

            final List<User> userList = userRes.findAllById(userids);
            for (final User o : userList) {
                o.setAgentStatus(agentStatusMap.get(o.getId()));
                // find user's skills
                userService.attachOrgansPropertiesForUser(o);
            }
            map.addAttribute("userList", userList);
            map.addAttribute("currentorgan", organ);
        }
        return request(super.pageTplResponse("/apps/cca/transferagentlist"));
    }

    /**
     * ??????????????????
     *
     * @param map
     * @param request
     * @param userid
     * @param agentserviceid
     * @param agentuserid
     * @param agentno
     * @param memo
     * @return
     */
    @RequestMapping(value = "/transfer/save.html")
    @Menu(type = "apps", subtype = "transfersave")
    public ModelAndView transfersave(
            final ModelMap map, HttpServletRequest request,
            @Valid final String userid,         // ??????ID
            @Valid final String agentserviceid, // ????????????ID
            @Valid final String agentuserid,    // ????????????ID
            @Valid final String currentAgentnoid,
            @Valid final String agentno,   // ??????????????????????????????
            @Valid final String memo
    ) throws ServerException {
        final String currentAgentno = currentAgentnoid; // ?????????????????????agentno

        final String orgi = super.getOrgi(request);

        if (StringUtils.isNotBlank(userid) &&
                StringUtils.isNotBlank(agentuserid) &&
                StringUtils.isNotBlank(agentno)) {
            final User targetAgent = userRes.findById(agentno).orElseThrow(() -> new RuntimeException("not found"));
            final AgentService agentService = agentServiceRes.findByIdAndOrgi(agentserviceid, super.getOrgi(request));
            /**
             * ??????AgentUser
             */
            final AgentUser agentUser = agentUserService.resolveAgentUser(userid, agentuserid, orgi);
            agentUser.setAgentno(agentno);
            agentUser.setAgentname(targetAgent.getUname());
            agentUserRes.save(agentUser);

            /**
             * ????????????
             */
            // ??????????????????
            final AgentStatus transAgentStatus = cacheService.findOneAgentStatusByAgentnoAndOrig(agentno, orgi);

            // ???????????????
            final AgentStatus currentAgentStatus = cacheService.findOneAgentStatusByAgentnoAndOrig(currentAgentno, orgi);

            if (StringUtils.equals(
                    AgentUserStatusEnum.INSERVICE.toString(), agentUser.getStatus())) { //?????? ??? ??????????????? ????????????

                // ???????????????????????????????????????
                if (currentAgentStatus != null) {
                    cacheService.deleteOnlineUserIdFromAgentStatusByUseridAndAgentnoAndOrgi(userid, currentAgentno, orgi);
                    agentUserService.updateAgentStatus(currentAgentStatus, super.getOrgi(request));
                }

                if (transAgentStatus != null) {
                    agentService.setAgentno(agentno);
                    agentService.setAgentusername(transAgentStatus.getUsername());
                }

                // ????????????????????????
                try {
                    Message outMessage = new Message();
                    outMessage.setMessage(
                            acdMessageHelper.getSuccessMessage(agentService, agentUser.getChannel(), orgi));
                    outMessage.setMessageType(Enums.MediaType.TEXT.toString());
                    outMessage.setCalltype(Enums.CallType.IN.toString());
                    outMessage.setCreatetime(DateFormatEnum.DAY_TIME.format(new Date()));
                    outMessage.setAgentUser(agentUser);
                    outMessage.setAgentService(agentService);

                    if (StringUtils.isNotBlank(agentUser.getUserid())) {
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

                    // ??????????????????????????????
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
        return request(super.pageTplResponse("redirect:/apps/cca/index.html"));

    }


    /**
     * ????????????
     * ????????????????????????????????????????????????????????????????????????????????????????????????
     *
     * @param request
     * @param id
     * @return
     * @throws Exception
     */
    @RequestMapping({"/end.html"})
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView end(HttpServletRequest request, @Valid String id) {
        final String orgi = super.getOrgi(request);
        final User logined = super.getUser(request);

        final AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, orgi);

        if (agentUser != null) {
            if ((StringUtils.equals(
                    logined.getId(), agentUser.getAgentno()) || logined.isAdmin())) {
                // ????????????-?????????????????????????????????
                try {
                    acdAgentService.finishAgentUser(agentUser, orgi);
                } catch (ServerException e) {
                    // ??????????????????
                    logger.error("[end]", e);
                }
            } else {
                logger.info("[end] Permission not fulfill.");
            }
        }

        return request(super.pageTplResponse("redirect:/apps/cca/index.html"));
    }

    @RequestMapping({"/blacklist/add.html"})
    @Menu(type = "apps", subtype = "blacklist")
    public ModelAndView blacklistadd(ModelMap map, HttpServletRequest request, @Valid String agentuserid, @Valid String agentserviceid, @Valid String userid)
            throws Exception {
        map.addAttribute("agentuserid", agentuserid);
        map.addAttribute("agentserviceid", agentserviceid);
        map.addAttribute("userid", userid);
        map.addAttribute("agentUser", agentUserRes.findByIdAndOrgi(userid, super.getOrgi(request)));
        return request(super.pageTplResponse("/apps/cca/blacklistadd"));
    }

    @RequestMapping({"/blacklist/save.html"})
    @Menu(type = "apps", subtype = "blacklist")
    public ModelAndView blacklist(
            HttpServletRequest request,
            @Valid String agentuserid,
            @Valid String agentserviceid,
            @Valid String userid,
            @Valid BlackEntity blackEntity)
            throws Exception {
        logger.info("[blacklist] userid {}", userid);
        final User logined = super.getUser(request);
        final String orgi = logined.getOrgi();

        if (StringUtils.isBlank(userid)) {
            throw new ServerException("Invalid userid");
        }
        /**
         * ???????????????
         * ???????????????????????????
         */
        JSONObject payload = new JSONObject();

        int timeSeconds = blackEntity.getControltime() * 3600;
        payload.put("userId", userid);
        payload.put("orgi", orgi);
        ModelAndView view = end(request, agentuserid);
        // ????????????????????????
        blackEntityService.updateOrCreateBlackEntity(blackEntity, logined, userid, orgi, agentserviceid);

        // ?????????????????? ????????????
        brokerPublisher.send(new MqMessage().destination(Constants.WEBIM_SOCKETIO_ONLINE_USER_BLACKLIST)
                .payload(payload.toJSONString()).type(MqMessage.Type.QUEUE).delay(timeSeconds));

        return view;
    }

}
