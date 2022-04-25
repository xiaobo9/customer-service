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
package com.chatopera.cc.controller.api;

import com.chatopera.cc.acd.ACDAgentService;
import com.chatopera.cc.acd.ACDPolicyService;
import com.chatopera.cc.acd.ACDWorkMonitor;
import com.github.xiaobo9.commons.enums.Enums;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.proxy.AgentStatusProxy;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.RestResult;
import com.chatopera.cc.util.RestResultType;
import com.github.xiaobo9.commons.enums.AgentStatusEnum;
import com.github.xiaobo9.entity.AgentStatus;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.repository.AgentStatusRepository;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Date;

/**
 * ACD服务
 * 获取队列统计信息
 */
@RestController
@RequestMapping("/api/servicequene")
public class ApiServiceQueneController extends Handler {

    @Autowired
    private AgentStatusProxy agentStatusProxy;

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    @Autowired
    private ACDPolicyService acdPolicyService;

    @Autowired
    private AgentStatusRepository agentStatusRes;

    @Autowired
    private ACDAgentService acdAgentService;

    @Autowired
    private CacheService cacheService;

    /**
     * 获取队列统计信息，包含当前队列服务中的访客数，排队人数，坐席数
     *
     * @param request
     * @return
     */
    @RequestMapping(method = RequestMethod.GET)
    @Menu(type = "apps", subtype = "user", access = true)
    public ResponseEntity<RestResult> list(HttpServletRequest request) {
        return new ResponseEntity<>(
                new RestResult(RestResultType.OK, acdWorkMonitor.getAgentReport(super.getOrgi(request))),
                HttpStatus.OK);
    }

    /**
     * 坐席状态操作，就绪、未就绪、忙
     *
     * @param request
     * @return
     */
    @RequestMapping(method = RequestMethod.PUT)
    @Menu(type = "apps", subtype = "user", access = true)
    public ResponseEntity<RestResult> agentStatus(
            HttpServletRequest request,
            @Valid String status) {
        User logined = super.getUser(request);
        AgentStatus agentStatus = null;
        if (StringUtils.isNotBlank(status) && status.equals(AgentStatusEnum.READY.toString())) {

            agentStatus = agentStatusRes.findOneByAgentnoAndOrgi(logined.getId(), logined.getOrgi()).orElseGet(() -> {
                AgentStatus p = new AgentStatus();
                p.setUserid(logined.getId());
                p.setUsername(logined.getUname());
                p.setAgentno(logined.getId());
                p.setLogindate(new Date());

//                SessionConfig sessionConfig = acdPolicyService.initSessionConfig(logined.getOrgi());
                p.setUpdatetime(new Date());
                p.setOrgi(super.getOrgi(request));
//                p.setMaxusers(sessionConfig.getMaxuser());
                return p;
            });

            /**
             * 设置技能组
             */
            agentStatus.setSkills(logined.getSkills());

            /**
             * 更新当前用户状态
             */
            agentStatus.setUsers(cacheService.getInservAgentUsersSizeByAgentnoAndOrgi(
                    agentStatus.getAgentno(),
                    super.getOrgi(request)));
            agentStatus.setStatus(AgentStatusEnum.READY.toString());
            agentStatusRes.save(agentStatus);

            acdWorkMonitor.recordAgentStatus(
                    agentStatus.getAgentno(), agentStatus.getUsername(), agentStatus.getAgentno(),
                    logined.isAdmin(), agentStatus.getAgentno(),
                    AgentStatusEnum.OFFLINE.toString(), AgentStatusEnum.READY.toString(),
                    Enums.AgentWorkType.MEIDIACHAT.toString(), agentStatus.getOrgi(), null);
            acdAgentService.assignVisitors(agentStatus.getAgentno(), super.getOrgi(request));
        } else if (StringUtils.isNotBlank(status)) {
            if (status.equals(AgentStatusEnum.NOTREADY.toString())) {
                agentStatusRes.findOneByAgentnoAndOrgi(
                        logined.getId(), super.getOrgi(request)).ifPresent(p -> {
                    acdWorkMonitor.recordAgentStatus(
                            p.getAgentno(), p.getUsername(), p.getAgentno(),
                            logined.isAdmin(),
                            p.getAgentno(),
                            p.isBusy() ? AgentStatusEnum.BUSY.toString() : AgentStatusEnum.READY.toString(),
                            AgentStatusEnum.NOTREADY.toString(),
                            Enums.AgentWorkType.MEIDIACHAT.toString(), p.getOrgi(), p.getUpdatetime());
                    agentStatusRes.delete(p);
                });
            } else if (StringUtils.isNotBlank(status) && status.equals(AgentStatusEnum.BUSY.toString())) {
                agentStatusRes.findOneByAgentnoAndOrgi(
                        logined.getId(), logined.getOrgi()).ifPresent(p -> {
                    p.setBusy(true);
                    acdWorkMonitor.recordAgentStatus(
                            p.getAgentno(), p.getUsername(), p.getAgentno(),
                            logined.isAdmin(), p.getAgentno(),
                            AgentStatusEnum.READY.toString(), AgentStatusEnum.BUSY.toString(),
                            Enums.AgentWorkType.MEIDIACHAT.toString(), p.getOrgi(),
                            p.getUpdatetime());
                    p.setUpdatetime(new Date());
                    agentStatusRes.save(p);
                });
            } else if (StringUtils.isNotBlank(status) && status.equals(
                    AgentStatusEnum.NOTBUSY.toString())) {
                agentStatusRes.findOneByAgentnoAndOrgi(
                        logined.getId(), logined.getOrgi()).ifPresent(p -> {
                    p.setBusy(false);
                    acdWorkMonitor.recordAgentStatus(
                            p.getAgentno(), p.getUsername(), p.getAgentno(),
                            logined.isAdmin(), p.getAgentno(),
                            AgentStatusEnum.BUSY.toString(), AgentStatusEnum.READY.toString(),
                            Enums.AgentWorkType.MEIDIACHAT.toString(), p.getOrgi(),
                            p.getUpdatetime());

                    p.setUpdatetime(new Date());
                    agentStatusRes.save(p);
                });
                acdAgentService.assignVisitors(agentStatus.getAgentno(), super.getOrgi(request));
            }
            agentStatusProxy.broadcastAgentsStatus(
                    super.getOrgi(request), "agent", "api", super.getUser(request).getId());
        }
        return new ResponseEntity<>(new RestResult(RestResultType.OK, agentStatus), HttpStatus.OK);
    }
}