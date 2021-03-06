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
import com.chatopera.cc.basic.ThumbnailUtils;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.model.ChatMessage;
import com.chatopera.cc.peer.PeerSyncIM;
import com.chatopera.cc.persistence.blob.JpaBlobHelper;
import com.chatopera.cc.socketio.message.Message;
import com.github.xiaobo9.commons.enums.AgentStatusEnum;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.exception.ServerException;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.AgentStatusRepository;
import com.github.xiaobo9.repository.AgentUserTaskRepository;
import com.github.xiaobo9.repository.SNSAccountRepository;
import com.github.xiaobo9.repository.StreamingFileRepository;
import com.github.xiaobo9.service.AttachmentService;
import com.github.xiaobo9.service.UploadService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

@Service
public class AgentProxyService {
    private final static Logger logger = LoggerFactory.getLogger(AgentProxyService.class);

    @Autowired
    private JpaBlobHelper jpaBlobHelper;

    @Autowired
    private StreamingFileRepository streamingFileRepository;

    @Autowired
    private PeerSyncIM peerSyncIM;

    @Autowired
    private SNSAccountRepository snsAccountRes;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AgentStatusRepository agentStatusRes;

    @Autowired
    private AgentUserTaskRepository agentUserTaskRes;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private AttachmentService attachmentService;

    /**
     * ?????????????????????????????????
     * ?????????ACD
     *
     * @param user
     * @param agentStatus
     */
    public void ready(final User user, final AgentStatus agentStatus, final boolean busy) {
        agentStatus.setOrgi(user.getOrgi());
        agentStatus.setUserid(user.getId());
        agentStatus.setUsername(user.getUname());
        agentStatus.setAgentno(user.getId());
        agentStatus.setLogindate(new Date());
        agentStatus.setOrgi(agentStatus.getOrgi());
        agentStatus.setUpdatetime(new Date());
        agentStatus.setSkills(user.getSkills());
        // TODO ??????busy???????????????????????????AgentStatus maxuser??????users??????
        // ????????????????????????????????????????????????????????????????????????
        agentStatus.setBusy(busy);
//        SessionConfig sessionConfig = acdPolicyService.initSessionConfig(agentStatus.getOrgi());
//        agentStatus.setMaxusers(sessionConfig.getMaxuser());

        // ????????????????????????
        agentStatus.setUsers(cacheService.getInservAgentUsersSizeByAgentnoAndOrgi(agentStatus.getAgentno(), agentStatus.getOrgi()));
        agentStatus.setStatus(AgentStatusEnum.READY.toString());

        logger.info("[ready] set agent {}, status {}", agentStatus.getAgentno(), AgentStatusEnum.READY);

        // ???????????????
        agentStatusRes.save(agentStatus);
    }


    /**
     * ???????????????????????????
     *
     * @param chatMessage
     * @param agentUser
     */
    public void sendChatMessageByAgent(final ChatMessage chatMessage, final AgentUser agentUser) {
        Message outMessage = new Message();
        outMessage.setMessage(chatMessage.getMessage());
        outMessage.setCalltype(chatMessage.getCalltype());
        outMessage.setAgentUser(agentUser);

        // ??????SNSAccount??????
        if (StringUtils.isNotBlank(agentUser.getAppid())) {
            snsAccountRes.findOneBySnsTypeAndSnsIdAndOrgi(agentUser.getChannel(), agentUser.getAppid(), agentUser.getOrgi())
                    .ifPresent(outMessage::setSnsAccount);
        }

        outMessage.setContextid(chatMessage.getContextid());
        outMessage.setAttachmentid(chatMessage.getAttachmentid());
        outMessage.setMessageType(chatMessage.getMsgtype());
        outMessage.setCreatetime(Constants.DISPLAY_DATE_FORMATTER.format(chatMessage.getCreatetime()));
        outMessage.setChannelMessage(chatMessage);

        // ??????????????????
        AgentUserTask agentUserTask = agentUserTaskRes.getOne(agentUser.getId());
        agentUserTask.setWarnings("0");
        agentUserTask.setWarningtime(null);

        agentUserTask.setReptime(null);
        agentUserTask.setReptimes("1");
        agentUserTask.setLastmessage(new Date());

        agentUserTaskRes.save(agentUserTask);

        // ???????????????????????????(?????????????????????????????????????????????????????????)
        peerSyncIM.send(
                Enums.ReceiverType.VISITOR,
                Enums.ChannelType.toValue(agentUser.getChannel()),
                agentUser.getAppid(),
                Enums.MessageType.MESSAGE,
                chatMessage.getTouser(),
                outMessage,
                true);

        // ??????????????????????????????????????????????????????
        peerSyncIM.send(
                Enums.ReceiverType.AGENT,
                Enums.ChannelType.WEBIM,
                agentUser.getAppid(),
                Enums.MessageType.MESSAGE,
                agentUser.getAgentno(),
                outMessage,
                true);
    }

    /**
     * ???????????????????????????????????????????????????
     *
     * @param creator
     * @param agentUser
     * @param multipart
     * @param sf
     */
    public void sendFileMessageByAgent(final User creator, final AgentUser agentUser, final MultipartFile multipart, final StreamingFile sf) {
        // ?????????
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setFilename(multipart.getOriginalFilename());
        chatMessage.setFilesize(multipart.getSize());
        chatMessage.setAttachmentid(sf.getId());
        chatMessage.setMessage(sf.getFileUrl());
        chatMessage.setId(UUIDUtils.getUUID());
        chatMessage.setContextid(agentUser.getContextid());
        chatMessage.setAgentserviceid(agentUser.getAgentserviceid());
        chatMessage.setChannel(agentUser.getChannel());
        chatMessage.setUsession(agentUser.getUserid());
        chatMessage.setAppid(agentUser.getAppid());
        chatMessage.setUserid(creator.getId());
        chatMessage.setOrgi(creator.getOrgi());
        chatMessage.setCreater(creator.getId());
        chatMessage.setUsername(creator.getUname());

        chatMessage.setCalltype(Enums.CallType.OUT.toString());
        if (StringUtils.isNotBlank(agentUser.getAgentno())) {
            chatMessage.setTouser(agentUser.getUserid());
        }

        if (multipart.getContentType() != null && multipart.getContentType().contains(Constants.ATTACHMENT_TYPE_IMAGE)) {
            chatMessage.setMsgtype(Enums.MediaType.IMAGE.toString());
        } else {
            chatMessage.setMsgtype(Enums.MediaType.FILE.toString());
        }

        Message outMessage = new Message();
        outMessage.setCalltype(chatMessage.getCalltype());
        outMessage.setMessage(sf.getFileUrl());

        if (!AgentUserStatusEnum.END.toString().equals(agentUser.getStatus())) {
            // ????????????
            outMessage.setFilename(multipart.getOriginalFilename());
            outMessage.setFilesize((int) multipart.getSize());
            outMessage.setChannelMessage(chatMessage);
            outMessage.setAgentUser(agentUser);
            outMessage.setCreatetime(Constants.DISPLAY_DATE_FORMATTER.format(new Date()));
            outMessage.setMessageType(chatMessage.getMsgtype());

            /**
             * ????????????????????????
             */
            // ?????????????????????
            peerSyncIM.send(Enums.ReceiverType.VISITOR,
                    Enums.ChannelType.toValue(agentUser.getChannel()),
                    agentUser.getAppid(),
                    Enums.MessageType.MESSAGE,
                    agentUser.getUserid(), outMessage, true);

            // ?????????????????????
            peerSyncIM.send(Enums.ReceiverType.AGENT,
                    Enums.ChannelType.WEBIM,
                    agentUser.getAppid(),
                    Enums.MessageType.MESSAGE,
                    agentUser.getAgentno(), outMessage, true);

        } else {
            logger.warn("[sendFileMessageByAgent] agent user chat is end, disable forward files.");
        }
    }


    /**
     * ???http???multipart??????????????????
     *
     * @param creator
     * @param multipart
     * @return
     * @throws IOException
     * @throws ServerException
     */
    public StreamingFile saveFileIntoMySQLBlob(User creator, MultipartFile multipart) throws IOException, ServerException {

        String fileid = UUIDUtils.getUUID();
        StreamingFile sf = new StreamingFile();

        // ???????????????
        if (multipart.getContentType() != null && multipart.getContentType().contains(Constants.ATTACHMENT_TYPE_IMAGE)) {
            // ??????
            // process thumbnail
            File original = new File(uploadService.getUploadPath(), fileid + "_original");
            File thumbnail = new File(uploadService.getUploadPath(), fileid);
            FileCopyUtils.copy(multipart.getBytes(), original);
            ThumbnailUtils.processImage(thumbnail, original);
            sf.setThumbnail(jpaBlobHelper.createBlobWithFile(thumbnail));
            sf.setFileUrl("/res/image.html?id=" + fileid);
        } else {
            // ?????????????????????
            if (multipart.getSize() == 0) {
                throw new ServerException("Empty upload file size.");
            }

            AttachmentFile attachmentFile = attachmentService.processAttachmentFile(multipart, fileid, creator.getOrgi(), creator.getId());
            sf.setFileUrl("/res/file.html?id=" + attachmentFile.getId());
        }

        // ???????????????MySQL?????????
        sf.setId(fileid);
        sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
        sf.setName(multipart.getOriginalFilename());
        sf.setMime(multipart.getContentType());

        streamingFileRepository.save(sf);

        return sf;
    }

    /**
     * ????????????User???AgentStatus
     * ?????????????????????????????????????????????????????????
     *
     * @param agentno
     * @param orgi
     * @return
     */
    public AgentStatus resolveAgentStatusByAgentnoAndOrgi(final String agentno, final String orgi, final Map<String, String> skills) {
        logger.info("[resolveAgentStatusByAgentnoAndOrgi] agentno {}, skills {}", agentno,
                String.join("|", skills.keySet()));
        AgentStatus agentStatus = cacheService.findOneAgentStatusByAgentnoAndOrig(agentno, orgi);

        if (agentStatus == null) {
            agentStatus = agentStatusRes.findOneByAgentnoAndOrgi(agentno, orgi).orElseGet(AgentStatus::new);
        }

        agentStatus.setSkills(skills);

        return agentStatus;
    }

}
