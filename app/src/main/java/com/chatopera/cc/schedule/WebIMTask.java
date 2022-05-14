/*
 * Copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
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
package com.chatopera.cc.schedule;

import com.chatopera.cc.acd.ACDAgentService;
import com.chatopera.cc.acd.ACDPolicyService;
import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.model.ChatMessage;
import com.chatopera.cc.peer.PeerSyncIM;
import com.chatopera.cc.service.OnlineUserService;
import com.chatopera.cc.socketio.message.Message;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.AgentUserTaskRepository;
import com.github.xiaobo9.repository.JobDetailRepository;
import com.github.xiaobo9.repository.OnlineUserRepository;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collection;
import java.util.Date;
import java.util.List;

@Configuration
@EnableScheduling
public class WebIMTask {

    private final static Logger logger = LoggerFactory.getLogger(WebIMTask.class);

    @Autowired
    private ACDPolicyService acdPolicyService;

    @Autowired
    private ACDAgentService acdAgentService;

    @Autowired
    private AgentUserTaskRepository agentUserTaskRes;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private JobDetailRepository jobDetailRes;

    @Autowired
    private TaskExecutor webimTaskExecutor;

    @Autowired
    private PeerSyncIM peerSyncIM;

    @Autowired
    private CacheService cache;

    @Autowired
    private OnlineUserService onlineUserService;
    /**
     * 处理超时消息，每5秒执行一次
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 20000)
    public void task() {
        final List<SessionConfig> configs = acdPolicyService.initSessionConfigList();
        if (isEmpty(configs) || MainContext.getContext() == null) {
            return;
        }
        for (final SessionConfig config : configs) {
            // 设置了启用 超时提醒
            if (config.isSessiontimeout()) {
                doTask(config);
            } else if (config.isResessiontimeout()) {
                // 未启用超时提醒，只设置了超时断开
                doTask2(config);
            }
            // 启用排队超时功能，超时断开
            if (config.isQuene()) {
                doTask3(config);
            }
        }
    }

    private void doTask(SessionConfig config) {
        final List<AgentUserTask> tasks = agentUserTaskRes.findByLastmessageLessThanAndStatusAndOrgi(
                MainUtils.getLastTime(config.getTimeout()),
                AgentUserStatusEnum.INSERVICE.toString(), config.getOrgi());
        // 超时未回复
        for (final AgentUserTask task : tasks) {
            AgentUser agentUser = cache.findOneAgentUserByUserIdAndOrgi(task.getUserid(), Constants.SYSTEM_ORGI).orElse(null);
            if (agentUser == null || StringUtils.isBlank(agentUser.getAgentno())) {
                continue;
            }
            AgentStatus agentStatus = cache.findOneAgentStatusByAgentnoAndOrig(agentUser.getAgentno(), task.getOrgi());
            if (agentStatus == null) {
                continue;
            }
            task.setAgenttimeouttimes(task.getAgenttimeouttimes() + 1);
            if (task.getWarnings() == null || task.getWarnings().equals("0")) {
                task.setWarnings("1");
                task.setWarningtime(new Date());

                // 发送提示消息
                processMessage(config.getTimeoutmsg(), agentStatus.getUsername(), agentUser);
                agentUserTaskRes.save(task);
                continue;
            }
            Date lastReTimout = MainUtils.getLastTime(config.getRetimeout());
            // 再次超时未回复 设置了再次超时,断开
            if (config.isResessiontimeout() && task.getWarningtime() != null && lastReTimout.after(task.getWarningtime())) {
                processMessage(config.getRetimeoutmsg(), config.getServicename(), agentUser);
                try {
                    acdAgentService.finishAgentService(agentUser, task.getOrgi());
                } catch (Exception e) {
                    logger.warn("[task] exception: ", e);
                }
            }
        }
    }

    private void doTask2(SessionConfig config) {

        List<AgentUserTask> agentUserTask = agentUserTaskRes.findByLastmessageLessThanAndStatusAndOrgi(
                MainUtils.getLastTime(config.getRetimeout()),
                AgentUserStatusEnum.INSERVICE.toString(), config.getOrgi());
        for (final AgentUserTask task : agentUserTask) {        // 超时未回复
            AgentUser user = cache.findOneAgentUserByUserIdAndOrgi(task.getUserid(), Constants.SYSTEM_ORGI).orElse(null);
            if (user == null) {
                continue;
            }
            AgentStatus status = cache.findOneAgentStatusByAgentnoAndOrig(user.getAgentno(), task.getOrgi());
            Date lastReTimeOut = MainUtils.getLastTime(config.getRetimeout());
            if (status != null && task.getWarningtime() != null && lastReTimeOut.after(task.getWarningtime())) {
                //再次超时未回复 设置了再次超时,断开
                processMessage(config.getRetimeoutmsg(), status.getUsername(), user);
                try {
                    acdAgentService.finishAgentService(user, task.getOrgi());
                } catch (Exception e) {
                    logger.warn("[task] exception: ", e);
                }
            }
        }
    }

    private void doTask3(SessionConfig config) {
        List<AgentUserTask> tasks = agentUserTaskRes.findByLogindateLessThanAndStatusAndOrgi(
                MainUtils.getLastTime(config.getQuenetimeout()),
                AgentUserStatusEnum.INQUENE.toString(), config.getOrgi());
        for (final AgentUserTask task : tasks) {
            // 超时未回复
            cache.findOneAgentUserByUserIdAndOrgi(task.getUserid(), Constants.SYSTEM_ORGI)
                    .ifPresent(p -> {
                        // 设置了超时,断开
                        processMessage(config.getQuenetimeoutmsg(), config.getServicename(), p);
                        try {
                            acdAgentService.finishAgentService(p, task.getOrgi());
                        } catch (Exception e) {
                            logger.warn("[task] exception: ", e);
                        }
                    });
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 20000) // 每5秒执行一次
    public void agent() {
        List<SessionConfig> configs = acdPolicyService.initSessionConfigList();
        if (isEmpty(configs) || MainContext.getContext() == null) {
            return;
        }
        for (final SessionConfig config : configs) {
            if (config == null || !config.isAgentreplaytimeout()) {
                continue;
            }
            List<AgentUserTask> tasks = agentUserTaskRes.findByLastgetmessageLessThanAndStatusAndOrgi(
                    MainUtils.getLastTime(config.getAgenttimeout()),
                    AgentUserStatusEnum.INSERVICE.toString(), config.getOrgi());
            for (final AgentUserTask task : tasks) {        // 超时未回复
                AgentUser user = cache.findOneAgentUserByUserIdAndOrgi(task.getUserid(), Constants.SYSTEM_ORGI).orElse(null);
                if (user == null) {
                    continue;
                }
                AgentStatus agentStatus = cache.findOneAgentStatusByAgentnoAndOrig(user.getAgentno(), task.getOrgi());
                if (agentStatus != null && (task.getReptimes() == null || task.getReptimes().equals("0"))) {
                    task.setReptimes("1");
                    task.setReptime(new Date());
                    //发送提示消息
                    processMessage(config.getAgenttimeoutmsg(), config.getServicename(), user);
                    agentUserTaskRes.save(task);
                }
            }
        }
    }

    /**
     * 将一分钟之前的不活跃的OnlineUser设置为离线
     * 每分钟执行一次，将不活跃的访客设置为离线
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 20000)
    public void onlineUserTask() {
        final Page<OnlineUser> pages = onlineUserRes.findByStatusAndCreatetimeLessThan(
                Enums.OnlineUserStatusEnum.ONLINE.toString(),
                MainUtils.getLastTime(60), PageRequest.of(0, 1000));
        if (pages.getContent().size() > 0) {
            for (final OnlineUser onlineUser : pages.getContent()) {
                try {
                    logger.info("[save] put onlineUser id {}, status {}, invite status {}",
                            onlineUser.getId(), onlineUser.getStatus(), onlineUser.getInvitestatus());
                    onlineUserService.offline(onlineUser.getId(), onlineUser.getOrgi());
                } catch (Exception e) {
                    logger.warn("[onlineuser] error", e);
                }
            }
        }
    }

    private void processMessage(String message, String serviceName, AgentUser agentUser) {
        if (StringUtils.isBlank(message) || agentUser == null) {
            return;
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(UUIDUtils.getUUID());
        chatMessage.setAppid(agentUser.getAppid());
        chatMessage.setUserid(agentUser.getUserid());

        chatMessage.setUsession(agentUser.getUserid());
        chatMessage.setTouser(agentUser.getUserid());
        chatMessage.setOrgi(agentUser.getOrgi());
        chatMessage.setUsername(agentUser.getUsername());
        chatMessage.setMessage(message);

        chatMessage.setContextid(agentUser.getContextid());

        chatMessage.setAgentserviceid(agentUser.getAgentserviceid());

        chatMessage.setCalltype(Enums.CallType.OUT.toString());
        if (StringUtils.isNotBlank(agentUser.getAgentno())) {
            chatMessage.setTouser(agentUser.getUserid());
        }
        chatMessage.setChannel(agentUser.getChannel());
        chatMessage.setUsession(agentUser.getUserid());
        // OUT类型，设置发消息人名字
        chatMessage.setUsername(StringUtils.defaultString(agentUser.getAgentname(), serviceName));

        Message outMessage = new Message();
        outMessage.setMessage(message);
        outMessage.setMessageType(Enums.MediaType.TEXT.toString());
        outMessage.setCalltype(Enums.CallType.OUT.toString());
        outMessage.setAgentUser(agentUser);
        outMessage.setSnsAccount(null);

        outMessage.setContextid(agentUser.getContextid());
        outMessage.setChannelMessage(chatMessage);
        outMessage.setCreatetime(Constants.DISPLAY_DATE_FORMATTER.format(chatMessage.getCreatetime()));

        // 通知坐席
        if (StringUtils.isNotBlank(agentUser.getAgentno())) {
            peerSyncIM.send(Enums.ReceiverType.AGENT, Enums.ChannelType.WEBIM,
                    agentUser.getAppid(),
                    Enums.MessageType.MESSAGE, agentUser.getAgentno(), outMessage, true);
        }

        // 通知访客
        if (StringUtils.isNotBlank(chatMessage.getTouser())) {
            peerSyncIM.send(Enums.ReceiverType.VISITOR,
                    Enums.ChannelType.toValue(agentUser.getChannel()),
                    agentUser.getAppid(),
                    Enums.MessageType.MESSAGE,
                    agentUser.getUserid(),
                    outMessage, true);
        }

    }

    /**
     * 每三秒 , 加载 标记为执行中的任务何 即将执行的 计划任务
     * TODO 需要重构，将这个作业执行引擎拆解，比如引入
     * <a href="https://airflow.apache.org/">https://airflow.apache.org/</a>
     * 暂时设计为10分钟执行一次，测试有什么影响，需要重新看代码
     */
    @Scheduled(fixedDelay = 600000) //
    public void jobDetail() {
        PageRequest page = PageRequest.of(0, 100);
        List<JobDetail> readyTasks = jobDetailRes.findByTaskstatus(Enums.TaskStatusType.READ.getType(), page)
                .getContent();
        List<JobDetail> planTasks = jobDetailRes.findByPlantaskAndTaskstatusAndNextfiretimeLessThan(
                        true, Enums.TaskStatusType.NORMAL.getType(), new Date(), page)
                .getContent();

        if (readyTasks.size() > 0) {
            for (JobDetail jobDetail : readyTasks) {
                doJob(jobDetail);
            }
        }
        if (planTasks.size() > 0) {
            for (JobDetail jobDetail : planTasks) {
                doJob(jobDetail);
            }
        }
    }

    private void doJob(JobDetail jobDetail) {
        if (cache.existJobByIdAndOrgi(jobDetail.getId(), jobDetail.getOrgi())) {
            return;
        }
        jobDetail.setTaskstatus(Enums.TaskStatusType.QUEUE.getType());
        jobDetailRes.save(jobDetail);
        cache.putJobByIdAndOrgi(jobDetail.getId(), jobDetail.getOrgi(), jobDetail);
        // 加入到作业执行引擎
        webimTaskExecutor.execute(new Task(jobDetail, jobDetailRes));
    }

    private boolean isEmpty(final Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

}
