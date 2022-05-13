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

package com.github.xiaobo9.service;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.vo.IMUploadFileBO;
import com.chatopera.cc.controller.vo.IMVO;
import com.chatopera.cc.model.ChatMessage;
import com.chatopera.cc.socketio.util.HumanUtils;
import com.chatopera.cc.util.Dict;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.AdTypeRepository;
import com.github.xiaobo9.repository.AgentUserContactsRepository;
import com.github.xiaobo9.repository.InviteRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class IMService {
    private static final Random random = new Random();
    @Autowired
    private CacheService cacheService;
    @Autowired
    private AgentUserContactsRepository agentUserContactsRepository;
    @Autowired
    private InviteRecordRepository inviteRecordRepository;

    /**
     * 上传图片
     */
    public ChatMessage uploadImage(IMUploadFileBO bo) {
        return createRichMediaMessage(bo, Enums.MediaType.IMAGE.toString());
    }

    /**
     * 上传文件
     */
    public ChatMessage uploadFile(IMUploadFileBO bo) {
        return createRichMediaMessage(bo, Enums.MediaType.FILE.toString());
    }

    /**
     * 上传图片
     */
    public ChatMessage uploadImageWithChannel(IMUploadFileBO bo, String channel, String appid, String orgi) {
        return createRichMediaMessageWithChannel(bo, channel, Enums.MediaType.IMAGE.toString(), appid, orgi);
    }

    /**
     * 上传文件
     */
    public ChatMessage uploadFileWithChannel(IMUploadFileBO bo, String channel, String appid, String orgi) {
        return createRichMediaMessageWithChannel(bo, channel, Enums.MediaType.FILE.toString(), appid, orgi);
    }

    /**
     * 创建图片，文件消息
     */
    private ChatMessage createRichMediaMessage(IMUploadFileBO bo, String msgtype) {
        ChatMessage data = new ChatMessage();
        data.setFilesize(bo.getSize());
        data.setFilename(bo.getName());
        data.setAttachmentid(bo.getId());
        data.setMessage(bo.getUrl());
        data.setMsgtype(msgtype);
        data.setType(Enums.MessageType.MESSAGE.toString());

        MainContext.getCache().findOneAgentUserByUserIdAndOrgi(bo.getUserId(), Constants.SYSTEM_ORGI).ifPresent(p -> {
            data.setUserid(p.getUserid());
            data.setUsername(p.getUsername());
            data.setTouser(p.getAgentno());
            data.setAppid(p.getAppid());
            data.setOrgi(p.getOrgi());
            if (p.isChatbotops()) {
                // TODO #75 create Chatbot Message
                // https://github.com/chatopera/cosin/issues/75
                log.info("[createRichMediaMessageWithChannel] TODO #75 create Chatbot Message");
            } else {
                HumanUtils.processMessage(data, msgtype, bo.getUserId());
            }
        });

        return data;
    }

    /**
     * 创建图片，文件消息
     */
    private ChatMessage createRichMediaMessageWithChannel(IMUploadFileBO bo, String channel, String msgtype, String appid, String orgi) {
        ChatMessage data = new ChatMessage();
        data.setUserid(bo.getUserId());
        data.setUsername(bo.getUserName());
        data.setTouser(bo.getUserId());
        data.setAppid(appid);
        data.setOrgi(orgi);
        data.setChannel(channel);
        data.setMessage(bo.getUrl());
        data.setFilesize(bo.getSize());
        data.setFilename(bo.getName());
        data.setAttachmentid(bo.getId());
        data.setMsgtype(msgtype);
        data.setType(Enums.MessageType.MESSAGE.toString());

        if (StringUtils.isNotBlank(bo.getUserId())) {
            CacheService cache = MainContext.getCache();
            Optional<AgentUser> optionalAgentUser = cache.findOneAgentUserByUserIdAndOrgi(bo.getUserId(), Constants.SYSTEM_ORGI);
            if (optionalAgentUser.filter(p -> Enums.OptType.CHATBOT.match(p.getOpttype())).isPresent()) {
                // TODO 给聊天机器人发送图片或文字
                // #652 创建聊天机器人插件时去掉了对它的支持，需要将来实现
//                getChatbotProxy().createMessage(
//                        data, appid, channel, Enums.CallType.IN.toString(),
//                        Enums.ChatbotItemType.USERINPUT.toString(), msgtype, data.getUserid(), orgi);
            } else {
                HumanUtils.processMessage(data, msgtype, bo.getUserId());
            }
        }
        return data;
    }

    /**
     * 按照权重获取广告
     */
    public AdType getPointAdv(String adpos, String skill, String orgi) {
        List<AdType> adTypes = cacheService.findOneSystemListByIdAndOrgi(Constants.CSKEFU_SYSTEM_ADV + "_" + skill, orgi);
        if (adTypes == null) {
            AdTypeRepository adRes = MainContext.getContext().getBean(AdTypeRepository.class);
            adTypes = adRes.findByOrgiAndSkill(orgi, skill);
            cacheService.putSystemListByIdAndOrgi(Constants.CSKEFU_SYSTEM_ADV + "_" + skill, orgi, adTypes);
        }
        List<SysDic> sysDicList = Dict.getInstance().getDic(Constants.CSKEFU_SYSTEM_ADPOS_DIC);
        SysDic sysDic = null;
        for (SysDic dic : sysDicList) {
            if (dic.getCode().equals(adpos)) {
                sysDic = dic;
                break;
            }
        }
        List<AdType> adTypeList = new ArrayList<>();
        if (sysDic != null) {
            for (AdType adType : adTypes) {
                if (adType.getAdpos().equals(sysDic.getId())) {
                    adTypeList.add(adType);
                }
            }
        }
        return weight(adTypeList);
    }

    /**
     * 按照权重，获取广告内容
     */
    private AdType weight(List<AdType> adList) {
        if (adList == null || adList.isEmpty()) {
            return null;
        }
        int weight = 0;
        for (AdType ad : adList) {
            weight += ad.getWeight();
        }
        AdType adType = null;
        int n = random.nextInt(weight), m = 0;
        for (AdType ad : adList) {
            if (m <= n && n < m + ad.getWeight()) {
                adType = ad;
                break;
            }
            m += ad.getWeight();
        }
        return adType;
    }


    public void saveAgentUserContacts(IMVO imvo, Contacts contacts, AgentUser p, User user, String username) {
        // 关联AgentUserContact的联系人
        // NOTE: 如果该userid已经有了关联的Contact则忽略，继续使用之前的
        agentUserContactsRepository.findOneByUseridAndOrgi(imvo.getUserid(), imvo.getOrgi())
                .ifPresent(a -> {
                    AgentUserContacts agentUserContacts = new AgentUserContacts();
                    agentUserContacts.setOrgi(imvo.getOrgi());
                    agentUserContacts.setAppid(imvo.getAppid());
                    agentUserContacts.setChannel(p.getChannel());
                    agentUserContacts.setContactsid(contacts.getId());
                    agentUserContacts.setUserid(imvo.getUserid());
                    agentUserContacts.setUsername(username);
                    agentUserContacts.setCreater(user.getId());
                    agentUserContacts.setCreatetime(new Date());
                    agentUserContactsRepository.save(agentUserContacts);
                });
    }

    public void updateInviteRecord(IMVO imvo) {
        String userid = imvo.getUserid();
        log.info("[index] update inviteRecord for user {}", userid);
        final Date threshold = new Date(System.currentTimeMillis() - Constants.WEBIM_AGENT_INVITE_TIMEOUT);
        PageRequest page = PageRequest.of(0, 1, Sort.Direction.DESC, "createtime");
        Page<InviteRecord> records = inviteRecordRepository.findByUseridAndOrgiAndResultAndCreatetimeGreaterThan(
                userid, imvo.getOrgi(), Enums.OnlineUserInviteStatus.DEFAULT.toString(), threshold, page);
        if (records.getContent().size() > 0) {
            final InviteRecord record = records.getContent().get(0);
            record.setUpdatetime(new Date());
            record.setTraceid(imvo.getTraceid());
            record.setTitle(imvo.getTitle());
            record.setUrl(imvo.getUrl());
            record.setResponsetime((int) (System.currentTimeMillis() - record.getCreatetime().getTime()));
            record.setResult(Enums.OnlineUserInviteStatus.ACCEPT.toString());
            log.info("[index] re-save inviteRecord id {}", record.getId());
            inviteRecordRepository.save(record);
        }
    }

}
