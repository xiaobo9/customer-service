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

import com.github.xiaobo9.entity.LeaveMsg;
import com.github.xiaobo9.entity.SNSAccount;
import com.github.xiaobo9.repository.SNSAccountRepository;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LeaveMsgProxy {
    private final static Logger logger = LoggerFactory.getLogger(LeaveMsgProxy.class);

    @Autowired
    private SNSAccountRepository snsAccountRes;

    /**
     * 根据snsId获得渠道信息
     *
     * @param leaveMsg
     * @return
     */
    public boolean resolveChannelBySnsid(final LeaveMsg leaveMsg) {
        if (StringUtils.isNotBlank(leaveMsg.getSnsId())) {
            snsAccountRes.findBySnsid(leaveMsg.getSnsId()).ifPresent(p -> leaveMsg.setChannel(p));
        } else {
            leaveMsg.setChannel(new SNSAccount());
        }

        if (leaveMsg.getChannel() != null && StringUtils.isNotBlank(leaveMsg.getChannel().getName())) {
            return true;
        } else {
            return false;
        }
    }
}
