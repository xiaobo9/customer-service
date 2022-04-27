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

import com.chatopera.cc.acd.ACDWorkMonitor;
import com.corundumstudio.socketio.SocketIONamespace;
import com.github.xiaobo9.entity.AgentReport;
import com.github.xiaobo9.repository.AgentReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AgentStatusService {
    private final static Logger logger = LoggerFactory.getLogger(AgentStatusService.class);

    @Autowired
    private AgentReportRepository agentReportRes;

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    @Autowired
    @Qualifier("agentNamespace")
    private SocketIONamespace agentNamespace;

    /**
     * 向所有坐席client通知坐席状态变化
     *
     * @param orgi
     * @param worktype
     * @param workresult
     * @param dataid
     */
    public void broadcastAgentsStatus(final String orgi, final String worktype, final String workresult, final String dataid) {
        // 坐席状态改变，通知监测服务
        AgentReport agentReport = acdWorkMonitor.getAgentReport(orgi);
        agentReport.setOrgi(orgi);
        agentReport.setWorktype(worktype);
        agentReport.setWorkresult(workresult);
        agentReport.setDataid(dataid);
        agentReportRes.save(agentReport);
        agentNamespace.getBroadcastOperations().sendEvent("status", agentReport);
    }

}
