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

import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.proxy.OrganProxy;
import com.github.xiaobo9.commons.enums.AgentStatusEnum;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.entity.AgentReport;
import com.github.xiaobo9.entity.AgentStatus;
import com.github.xiaobo9.entity.Organ;
import com.github.xiaobo9.entity.WorkMonitor;
import com.github.xiaobo9.repository.AgentServiceRepository;
import com.github.xiaobo9.repository.AgentUserRepository;
import com.github.xiaobo9.repository.WorkMonitorRepository;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class ACDWorkMonitor {
    private final static Logger logger = LoggerFactory.getLogger(ACDWorkMonitor.class);

    @Autowired
    private WorkMonitorRepository workMonitorRes;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private OrganProxy organProxy;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private AgentUserRepository agentUserRes;

    /**
     * 获得 当前服务状态
     *
     * @param orgi
     * @return
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public AgentReport getAgentReport(String orgi) {
        return getAgentReport(null, orgi);
    }

    /**
     * 获得一个技能组的坐席状态
     *
     * @param organ
     * @param orgi
     * @return
     */
    public AgentReport getAgentReport(String organ, String orgi) {
        /**
         * 统计当前在线的坐席数量
         */
        AgentReport report = new AgentReport();

        Map<String, AgentStatus> readys = cacheService.getAgentStatusReadyByOrig(orgi);

        int readyNum = 0;
        int busyNum = 0;

        for (Map.Entry<String, AgentStatus> entry : readys.entrySet()) {
            if (organ == null) {
                readyNum++;
                if (entry.getValue().isBusy()) {
                    busyNum++;
                }
                continue;
            }

            if (entry.getValue().getSkills() != null &&
                    entry.getValue().getSkills().containsKey(organ)) {
                readyNum++;
                if (entry.getValue().isBusy()) {
                    busyNum++;
                }

            }
        }
        report.setAgents(readyNum);
        report.setBusy(busyNum);
        report.setOrgi(orgi);

        /**
         * 统计当前服务中的用户数量
         */

        if (organ != null) {
            Organ currentOrgan = new Organ();
            currentOrgan.setId(organ);
            Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, orgi);

            report.setUsers(agentServiceRes.countByOrgiAndStatusAndAgentskillIn(orgi, AgentUserStatusEnum.INSERVICE.toString(), organs.keySet()));
            report.setInquene(agentUserRes.countByOrgiAndStatusAndSkillIn(orgi, AgentUserStatusEnum.INQUENE.toString(), organs.keySet()));
        } else {
            // 服务中
            report.setUsers(cacheService.getInservAgentUsersSizeByOrgi(orgi));
            // 等待中
            report.setInquene(cacheService.getInqueAgentUsersSizeByOrgi(orgi));
        }

        // DEBUG
        logger.info(
                "[getAgentReport] orgi {}, organ {}, agents {}, busy {}, users {}, inqueue {}", orgi, organ,
                report.getAgents(), report.getBusy(), report.getUsers(), report.getInquene()
        );
        return report;
    }

    /**
     * @param agent    坐席
     * @param userid   用户ID
     * @param status   工作状态，也就是上一个状态
     * @param current  下一个工作状态
     * @param worktype 类型 ： 语音OR 文本
     * @param orgi
     * @param lasttime
     */
    public void recordAgentStatus(
            String agent,
            String username,
            String extno,
            boolean admin,
            String userid,
            String status,
            String current,
            String worktype,
            String orgi,
            Date lasttime
    ) {
        WorkMonitor workMonitor = new WorkMonitor();
        if (StringUtils.isNotBlank(agent) && StringUtils.isNotBlank(status)) {
            workMonitor.setAgent(agent);
            workMonitor.setAgentno(agent);
            workMonitor.setStatus(status);
            workMonitor.setAdmin(admin);
            workMonitor.setUsername(username);
            workMonitor.setExtno(extno);
            workMonitor.setWorktype(worktype);
            if (lasttime != null) {
                workMonitor.setDuration((int) (System.currentTimeMillis() - lasttime.getTime()) / 1000);
            }
            if (status.equals(AgentStatusEnum.BUSY.toString())) {
                workMonitor.setBusy(true);
            }
            if (status.equals(AgentStatusEnum.READY.toString())) {
                int count = workMonitorRes.countByAgentAndDatestrAndStatusAndOrgi(
                        agent, DateFormatEnum.DAY.format(new Date()),
                        AgentStatusEnum.READY.toString(), orgi
                );
                if (count == 0) {
                    workMonitor.setFirsttime(true);
                }
            }
            if (current.equals(AgentStatusEnum.NOTREADY.toString())) {
                List<WorkMonitor> workMonitorList = workMonitorRes.findByOrgiAndAgentAndDatestrAndFirsttime(
                        orgi, agent, DateFormatEnum.DAY.format(new Date()), true);
                if (workMonitorList.size() > 0) {
                    WorkMonitor firstWorkMonitor = workMonitorList.get(0);
                    if (firstWorkMonitor.getFirsttimes() == 0) {
                        firstWorkMonitor.setFirsttimes(
                                (int) (System.currentTimeMillis() - firstWorkMonitor.getCreatetime().getTime()));
                        workMonitorRes.save(firstWorkMonitor);
                    }
                }
            }
            workMonitor.setCreatetime(new Date());
            workMonitor.setDatestr(DateFormatEnum.DAY.format(new Date()));

            workMonitor.setName(agent);
            workMonitor.setOrgi(orgi);
            workMonitor.setUserid(userid);

            workMonitorRes.save(workMonitor);
        }
    }

}
