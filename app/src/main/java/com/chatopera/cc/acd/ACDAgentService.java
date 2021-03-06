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

package com.chatopera.cc.acd;

import com.chatopera.cc.acd.basic.ACDComposeContext;
import com.chatopera.cc.acd.basic.ACDMessageHelper;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.AgentStatusRepository;
import com.github.xiaobo9.commons.enums.Enums;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.cache.RedisCommand;
import com.chatopera.cc.cache.RedisKey;
import com.github.xiaobo9.commons.exception.ServerException;
import com.chatopera.cc.peer.PeerSyncIM;
import com.chatopera.cc.service.AgentStatusService;
import com.chatopera.cc.service.AgentUserService;
import com.chatopera.cc.socketio.client.NettyClients;
import com.chatopera.cc.socketio.message.Message;
import com.chatopera.cc.util.SerializeUtil;
import com.github.xiaobo9.commons.enums.AgentStatusEnum;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.repository.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class ACDAgentService {
    private final static Logger logger = LoggerFactory.getLogger(ACDAgentService.class);

    @Autowired
    private RedisCommand redisCommand;

    @Autowired
    private ACDMessageHelper acdMessageHelper;

    @Autowired
    private AgentStatusService agentStatusService;

    @Autowired
    private ACDPolicyService acdPolicyService;

    @Autowired
    private PeerSyncIM peerSyncIM;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AgentUserRepository agentUserRes;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private AgentUserTaskRepository agentUserTaskRes;

    @Autowired
    private AgentStatusRepository agentStatusRes;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private UserRepository userRes;

    @Autowired
    private AgentUserService agentUserService;


    /**
     * ACD????????????
     *
     * @param ctx
     */
    public void notifyAgentUserProcessResult(final ACDComposeContext ctx) {
        if (ctx != null && StringUtils.isNotBlank(
                ctx.getMessage())) {
            logger.info("[onConnect] find available agent for onlineUser id {}", ctx.getOnlineUserId());

            /**
             * ?????????????????????
             * ????????????AgentService??????AgentService???????????????AgentService???????????????????????????
             */
            if (ctx.getAgentService() != null && (!ctx.isNoagent()) && !StringUtils.equals(
                    AgentUserStatusEnum.INQUENE.toString(),
                    ctx.getAgentService().getStatus())) {
                // ?????????????????????
                MainContext.getPeerSyncIM().send(Enums.ReceiverType.AGENT,
                        Enums.ChannelType.WEBIM,
                        ctx.getAppid(),
                        Enums.MessageType.NEW,
                        ctx.getAgentService().getAgentno(),
                        ctx, true);
            }

            /**
             * ?????????????????????
             */
            Message outMessage = new Message();
            outMessage.setMessage(ctx.getMessage());
            outMessage.setMessageType(Enums.MessageType.MESSAGE.toString());
            outMessage.setCalltype(Enums.CallType.IN.toString());
            outMessage.setCreatetime(DateFormatEnum.DAY_TIME.format(new Date()));
            outMessage.setNoagent(ctx.isNoagent());
            if (ctx.getAgentService() != null) {
                outMessage.setAgentserviceid(ctx.getAgentService().getId());
            }

            MainContext.getPeerSyncIM().send(Enums.ReceiverType.VISITOR,
                    Enums.ChannelType.WEBIM, ctx.getAppid(),
                    Enums.MessageType.NEW, ctx.getOnlineUserId(), outMessage, true);


        } else {
            logger.info("[onConnect] can not find available agent for user {}", ctx.getOnlineUserId());
        }
    }

    /**
     * ?????????????????????????????????????????????????????? ????????????????????????????????????????????????????????????
     * ????????????????????????????????????????????????????????????
     *
     * @param agentno
     * @param agentUser
     * @param orgi
     * @return
     * @throws Exception
     */
    public AgentService assignVisitorAsInvite(
            final String agentno,
            final AgentUser agentUser,
            final String orgi
    ) throws Exception {
        final AgentStatus agentStatus = cacheService.findOneAgentStatusByAgentnoAndOrig(agentno, orgi);
        return pickupAgentUserInQueue(agentUser, agentStatus);
    }

    /**
     * ???????????????????????????
     *
     * @param agentno
     * @param orgi
     */
    public void assignVisitors(String agentno, String orgi) {
        logger.info("[assignVisitors] agentno {}, orgi {}", agentno, orgi);
        // ???????????????????????????
        AgentStatus agentStatus = SerializeUtil.deserialize(
                redisCommand.getHashKV(RedisKey.getAgentStatusReadyHashKey(orgi), agentno));

        if (agentStatus == null) {
            logger.warn("[assignVisitors] can not find AgentStatus for agentno {}", agentno);
            return;
        }
        logger.info("[assignVisitors] agentStatus id {}, status {}, service {}/{}, skills {}, busy {}",
                agentStatus.getId(), agentStatus.getStatus(), agentStatus.getUsers(), agentStatus.getMaxusers(),
                String.join("|", agentStatus.getSkills().keySet()), agentStatus.isBusy());

        if ((!StringUtils.equals(
                AgentStatusEnum.READY.toString(), agentStatus.getStatus())) || agentStatus.isBusy()) {
            // ?????????????????????????????????????????????????????????
            // ???????????????
            return;
        }

        // ????????????????????????????????????
        final Map<String, AgentUser> pendingAgentUsers = cacheService.getAgentUsersInQueByOrgi(orgi);

        // ??????????????????????????????
        Map<String, Integer> assigned = new HashMap<>();
        int currentAssigned = cacheService.getInservAgentUsersSizeByAgentnoAndOrgi(
                agentStatus.getAgentno(), agentStatus.getOrgi());

        logger.info(
                "[assignVisitors] agentno {}, name {}, current assigned {}, batch size in queue {}",
                agentStatus.getAgentno(),
                agentStatus.getUsername(), currentAssigned, pendingAgentUsers.size());

        for (Map.Entry<String, AgentUser> entry : pendingAgentUsers.entrySet()) {
            AgentUser agentUser = entry.getValue();
            boolean process = false;

            if ((StringUtils.equals(agentUser.getAgentno(), agentno))) {
                // ????????????????????????????????????
                process = true;
            } else if (agentStatus != null &&
                    agentStatus.getSkills() != null &&
                    agentStatus.getSkills().size() > 0) {
                // ??????????????????????????????????????????????????????
                if ((StringUtils.isBlank(agentUser.getAgentno()) &&
                        StringUtils.isBlank(agentUser.getSkill()))) {
                    // ????????????????????????????????????????????????????????????????????????
                    process = true;
                } else if (StringUtils.isBlank(agentUser.getAgentno()) &&
                        agentStatus.getSkills().containsKey(agentUser.getSkill())) {
                    // ????????????????????????????????????????????????????????????????????????????????????????????????
                    process = true;
                }
            } else if (StringUtils.isBlank(agentUser.getAgentno()) &&
                    StringUtils.isBlank(agentUser.getSkill())) {
                // ?????????????????????????????????????????????????????????????????????????????????????????????
                // ???????????????????????????????????????????????????????????????
                process = true;
            }

            if (!process) {
                continue;
            }

            // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????(initMaxuser)
            final SessionConfig sessionConfig = acdPolicyService.initSessionConfig(agentUser.getSkill(), orgi);
            if ((ACDServiceRouter.getAcdPolicyService().getAgentUsersBySkill(agentStatus, agentUser.getSkill()) < sessionConfig.getMaxuser()) && (assigned.getOrDefault(agentUser.getSkill(), 0) < sessionConfig.getInitmaxuser())) {
                assigned.merge(agentUser.getSkill(), 1, Integer::sum);
                pickupAgentUserInQueue(agentUser, agentStatus);
            } else {
                logger.info(
                        "[assignVisitors] agentno {} reach the max users limit {}/{} or batch assign limit {}/{}",
                        agentno,
                        (currentAssigned + assigned.getOrDefault(agentUser.getSkill(), 0)),
                        sessionConfig.getMaxuser(), assigned, sessionConfig.getInitmaxuser());
                break;
            }
        }
        agentStatusService.broadcastAgentsStatus(orgi, "agent", "success", agentno);
    }

    /**
     * ????????????????????????????????????
     *
     * @param agentUser
     * @param agentStatus
     * @return
     */
    public AgentService pickupAgentUserInQueue(final AgentUser agentUser, final AgentStatus agentStatus) {
        // ?????????????????????
        cacheService.deleteAgentUserInqueByAgentUserIdAndOrgi(agentUser.getUserid(), agentUser.getOrgi());
        AgentService agentService = null;
        // ????????????????????????????????????????????????
        try {
            agentService = resolveAgentService(
                    agentStatus, agentUser, agentUser.getOrgi(), false);

            // ?????????????????? agentService
            Message outMessage = new Message();
            outMessage.setMessage(acdMessageHelper.getSuccessMessage(
                    agentService,
                    agentUser.getChannel(),
                    agentUser.getOrgi()));
            outMessage.setMessageType(Enums.MediaType.TEXT.toString());
            outMessage.setCalltype(Enums.CallType.IN.toString());
            outMessage.setCreatetime(DateFormatEnum.DAY_TIME.format(new Date()));

            if (StringUtils.isNotBlank(agentUser.getUserid())) {
                outMessage.setAgentUser(agentUser);
                outMessage.setChannelMessage(agentUser);

                // ?????????????????????
                peerSyncIM.send(
                        Enums.ReceiverType.VISITOR,
                        Enums.ChannelType.toValue(agentUser.getChannel()), agentUser.getAppid(),
                        Enums.MessageType.STATUS, agentUser.getUserid(), outMessage, true
                );

                // ?????????????????????
                peerSyncIM.send(Enums.ReceiverType.AGENT, Enums.ChannelType.WEBIM,
                        agentUser.getAppid(),
                        Enums.MessageType.NEW, agentUser.getAgentno(), outMessage, true);

                // ????????????????????????
                agentStatusService.broadcastAgentsStatus(agentUser.getOrgi(), "agent", "pickup", agentStatus.getAgentno());
            }
        } catch (Exception ex) {
            logger.warn("[assignVisitors] fail to process service", ex);
        }
        return agentService;
    }

    /**
     * ??????????????????
     *
     * @param agentUser
     * @param orgi
     * @throws Exception
     */
    public void finishAgentService(final AgentUser agentUser, final String orgi) {
        if (agentUser != null) {
            /**
             * ??????AgentUser
             */
            // ??????????????????
            AgentStatus agentStatus = null;
            if (StringUtils.equals(AgentUserStatusEnum.INSERVICE.toString(), agentUser.getStatus()) &&
                    agentUser.getAgentno() != null) {
                agentStatus = cacheService.findOneAgentStatusByAgentnoAndOrig(agentUser.getAgentno(), orgi);
            }

            // ?????????AgentUser?????????
            agentUser.setStatus(AgentUserStatusEnum.END.toString());
            if (agentUser.getServicetime() != null) {
                agentUser.setSessiontimes(System.currentTimeMillis() - agentUser.getServicetime().getTime());
            }

            // ??????????????????agentUser??????
            agentUserRes.save(agentUser);

            final SessionConfig sessionConfig = acdPolicyService.initSessionConfig(agentUser.getSkill(), orgi);

            /**
             * ????????????
             */
            AgentService service = null;
            if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                service = agentServiceRes.findByIdAndOrgi(agentUser.getAgentserviceid(), agentUser.getOrgi());
            } else if (agentStatus != null) {
                // ????????????????????????????????????????????? AgentService
                // ??????????????????????????????????????? AgentService
                service = resolveAgentService(agentStatus, agentUser, orgi, true);
            }

            if (service != null) {
                service.setStatus(AgentUserStatusEnum.END.toString());
                service.setEndtime(new Date());
                if (service.getServicetime() != null) {
                    service.setSessiontimes(System.currentTimeMillis() - service.getServicetime().getTime());
                }

                final AgentUserTask agentUserTask = agentUserTaskRes.findById(agentUser.getId()).orElse(null);
                if (agentUserTask != null) {
                    service.setAgentreplyinterval(agentUserTask.getAgentreplyinterval());
                    service.setAgentreplytime(agentUserTask.getAgentreplytime());
                    service.setAvgreplyinterval(agentUserTask.getAvgreplyinterval());
                    service.setAvgreplytime(agentUserTask.getAvgreplytime());

                    service.setUserasks(agentUserTask.getUserasks());
                    service.setAgentreplys(agentUserTask.getAgentreplys());

                    // ???????????????????????????????????????
                    if (sessionConfig.isQuality()) {
                        // ?????????????????????
                        service.setQualitystatus(Enums.QualityStatusEnum.NODIS.toString());
                    }
                }

                /**
                 * ????????????????????????????????????
                 */
                if ((!sessionConfig.isQuality()) || service.getUserasks() == 0) {
                    // ??????????????? ???????????????????????????
                    service.setQualitystatus(Enums.QualityStatusEnum.NO.toString());
                }
                agentServiceRes.save(service);
            }

            /**
             * ??????AgentStatus
             */
            if (agentStatus != null) {
                agentStatus.setUsers(
                        cacheService.getInservAgentUsersSizeByAgentnoAndOrgi(agentStatus.getAgentno(), agentStatus.getOrgi()));
                agentStatusRes.save(agentStatus);
            }

            /**
             * ???????????????????????????
             */
            switch (Enums.ChannelType.toValue(agentUser.getChannel())) {
                case WEBIM:
                    // WebIM ????????????????????????
                    // ?????????????????????
                    Message outMessage = new Message();
                    outMessage.setAgentStatus(agentStatus);
                    outMessage.setMessage(acdMessageHelper.getServiceFinishMessage(agentUser.getChannel(), agentUser.getSkill(), orgi));
                    outMessage.setMessageType(AgentUserStatusEnum.END.toString());
                    outMessage.setCalltype(Enums.CallType.IN.toString());
                    outMessage.setCreatetime(DateFormatEnum.DAY_TIME.format(new Date()));
                    outMessage.setAgentUser(agentUser);

                    // ?????????????????????
                    peerSyncIM.send(
                            Enums.ReceiverType.VISITOR,
                            Enums.ChannelType.toValue(agentUser.getChannel()), agentUser.getAppid(),
                            Enums.MessageType.STATUS, agentUser.getUserid(), outMessage, true
                    );

                    if (agentStatus != null) {
                        // ?????????????????????????????????
                        outMessage.setChannelMessage(agentUser);
                        outMessage.setAgentUser(agentUser);
                        peerSyncIM.send(Enums.ReceiverType.AGENT, Enums.ChannelType.WEBIM,
                                agentUser.getAppid(),
                                Enums.MessageType.END, agentUser.getAgentno(), outMessage, true);
                    }
                    break;
                case PHONE:
                    // ???????????????????????????
                    logger.info(
                            "[finishAgentService] send notify to callout channel agentno {}", agentUser.getAgentno());
                    NettyClients.getInstance().sendCalloutEventMessage(
                            agentUser.getAgentno(), Enums.MessageType.END.toString(), agentUser);
                    break;
                default:
                    logger.info(
                            "[finishAgentService] ignore notify agent service end for channel {}, agent user id {}",
                            agentUser.getChannel(), agentUser.getId());
            }

            // ??????????????????????????????????????????
            final OnlineUser onlineUser = onlineUserRes.findOneByUseridAndOrgi(
                    agentUser.getUserid(), agentUser.getOrgi());
            if (onlineUser != null) {
                onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.DEFAULT.toString());
                onlineUserRes.save(onlineUser);
                logger.info(
                        "[finishAgentService] onlineUser id {}, status {}, invite status {}", onlineUser.getId(),
                        onlineUser.getStatus(), onlineUser.getInvitestatus());
            }

            // ?????????????????????????????????????????????????????????
            if (agentStatus != null) {
                if ((ACDServiceRouter.getAcdPolicyService().getAgentUsersBySkill(agentStatus, agentUser.getSkill()) - 1) < sessionConfig.getMaxuser()) {
                    assignVisitors(agentStatus.getAgentno(), orgi);
                }
            }
            agentStatusService.broadcastAgentsStatus(
                    orgi, "end", "success", agentUser != null ? agentUser.getId() : null);
        } else {
            logger.info("[finishAgentService] orgi {}, invalid agent user, should not be null", orgi);
        }
    }


    /**
     * ??????AgentUser
     * ????????????????????????????????????
     *
     * @param agentUser
     * @param orgi
     * @return
     */
    public void finishAgentUser(final AgentUser agentUser, final String orgi) throws ServerException {
        logger.info("[finishAgentUser] userId {}, orgi {}", agentUser.getUserid(), orgi);

        if (agentUser == null || agentUser.getId() == null) {
            throw new ServerException("Invalid agentUser info");
        }

        if (!StringUtils.equals(AgentUserStatusEnum.END.toString(), agentUser.getStatus())) {
            /**
             * ??????????????????????????????????????????????????????
             */
            // ????????????
            finishAgentService(agentUser, orgi);
        }

        // ?????????????????????AgentUser??????
        agentUserRes.delete(agentUser);
    }

    /**
     * ???agentUser???????????????AgentService
     * ???????????????
     * 1. ???AgentUser????????????????????????????????????AgentService
     * 2. ?????????????????????????????????
     *
     * @param agentStatus ????????????
     * @param agentUser   ??????????????????
     * @param orgi        ??????ID
     * @param finished    ????????????
     * @return
     */
    public AgentService resolveAgentService(
            AgentStatus agentStatus,
            final AgentUser agentUser,
            final String orgi,
            final boolean finished) {

        AgentService agentService = new AgentService();
        if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
            AgentService existAgentService = agentServiceRes.findByIdAndOrgi(agentUser.getAgentserviceid(), orgi);
            if (existAgentService != null) {
                agentService = existAgentService;
            } else {
                agentService.setId(agentUser.getAgentserviceid());
            }
        }
        agentService.setOrgi(orgi);

        final Date now = new Date();
        // ??????????????????
        MainUtils.copyProperties(agentUser, agentService);
        agentService.setChannel(agentUser.getChannel());
        agentService.setSessionid(agentUser.getSessionid());

        // ??????????????????loginDate?????????
        agentUser.setLogindate(now);
        OnlineUser onlineUser = onlineUserRes.findOneByUseridAndOrgi(agentUser.getUserid(), orgi);

        if (finished == true) {
            // ????????????
            agentUser.setStatus(AgentUserStatusEnum.END.toString());
            agentService.setStatus(AgentUserStatusEnum.END.toString());
            agentService.setSessiontype(AgentUserStatusEnum.END.toString());
            if (agentStatus == null) {
                // ????????????????????????????????????
                agentService.setLeavemsg(true);
                agentService.setLeavemsgstatus(Enums.LeaveMsgStatus.NOTPROCESS.toString()); //??????????????????
            }

            if (onlineUser != null) {
                //  ??????OnlineUser???????????????????????????????????????????????????
                onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.DEFAULT.toString());
            }
        } else if (agentStatus != null) {
            agentService.setAgent(agentStatus.getAgentno());
            agentService.setSkill(agentUser.getSkill());
            agentUser.setStatus(AgentUserStatusEnum.INSERVICE.toString());
            agentService.setStatus(AgentUserStatusEnum.INSERVICE.toString());
            agentService.setSessiontype(AgentUserStatusEnum.INSERVICE.toString());
            // ??????????????????
            agentService.setAgentno(agentStatus.getUserid());
            agentService.setAgentusername(agentStatus.getUsername());
        } else {
            // ??????????????????????????????????????????????????????
            // ??????????????????
            agentUser.setStatus(AgentUserStatusEnum.INQUENE.toString());
            agentService.setStatus(AgentUserStatusEnum.INQUENE.toString());
            agentService.setSessiontype(AgentUserStatusEnum.INQUENE.toString());
        }

        if (finished || agentStatus != null) {
            agentService.setAgentuserid(agentUser.getId());
            agentService.setInitiator(Enums.ChatInitiatorType.USER.toString());

            long waittingtime = 0;
            if (agentUser.getWaittingtimestart() != null) {
                waittingtime = System.currentTimeMillis() - agentUser.getWaittingtimestart().getTime();
            } else {
                if (agentUser.getCreatetime() != null) {
                    waittingtime = System.currentTimeMillis() - agentUser.getCreatetime().getTime();
                }
            }

            agentUser.setWaittingtime((int) waittingtime);
            agentUser.setServicetime(now);
            agentService.setOwner(agentUser.getOwner());
            agentService.setTimes(0);

            final User agent = userRes.findById(agentService.getAgentno()).orElse(null);
            agentUser.setAgentname(agent.getUname());
            agentUser.setAgentno(agentService.getAgentno());

            if (StringUtils.isNotBlank(agentUser.getName())) {
                agentService.setName(agentUser.getName());
            }
            if (StringUtils.isNotBlank(agentUser.getPhone())) {
                agentService.setPhone(agentUser.getPhone());
            }
            if (StringUtils.isNotBlank(agentUser.getEmail())) {
                agentService.setEmail(agentUser.getEmail());
            }
            if (StringUtils.isNotBlank(agentUser.getResion())) {
                agentService.setResion(agentUser.getResion());
            }

            if (StringUtils.isNotBlank(agentUser.getSkill())) {
                agentService.setAgentskill(agentUser.getSkill());
            }

            agentService.setServicetime(now);

            if (agentUser.getCreatetime() != null) {
                agentService.setWaittingtime((int) (System.currentTimeMillis() - agentUser.getCreatetime().getTime()));
                agentUser.setWaittingtime(agentService.getWaittingtime());
            }
            if (onlineUser != null) {
                agentService.setOsname(onlineUser.getOpersystem());
                agentService.setBrowser(onlineUser.getBrowser());
                // ??????onlineUser???id
                agentService.setDataid(onlineUser.getId());
            }

            agentService.setLogindate(agentUser.getCreatetime());
            agentServiceRes.save(agentService);

            agentUser.setAgentserviceid(agentService.getId());
            agentUser.setLastgetmessage(now);
            agentUser.setLastmessage(now);
        }

        agentService.setDataid(agentUser.getId());

        /**
         * ????????????????????? ????????????????????????????????????????????????
         * ??? AgentUser ????????????????????????????????????
         */
        agentUserRes.save(agentUser);

        /**
         * ??????OnlineUser??????????????????????????????????????????
         */
        if (onlineUser != null && !finished) {
            onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.INSERV.toString());
            onlineUserRes.save(onlineUser);
        }

        // ??????????????????????????????????????????????????????
        if (agentStatus != null) {
            agentUserService.updateAgentStatus(agentStatus, orgi);
        }
        return agentService;
    }


}
