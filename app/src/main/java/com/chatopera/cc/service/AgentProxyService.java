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
     * 设置一个坐席为就绪状态
     * 不牵扯ACD
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
        // TODO 对于busy的判断，其实可以和AgentStatus maxuser以及users结合
        // 现在为了配合前端的行为：从未就绪到就绪设置为置闲
        agentStatus.setBusy(busy);
//        SessionConfig sessionConfig = acdPolicyService.initSessionConfig(agentStatus.getOrgi());
//        agentStatus.setMaxusers(sessionConfig.getMaxuser());

        // 更新当前用户状态
        agentStatus.setUsers(cacheService.getInservAgentUsersSizeByAgentnoAndOrgi(agentStatus.getAgentno(), agentStatus.getOrgi()));
        agentStatus.setStatus(AgentStatusEnum.READY.toString());

        logger.info("[ready] set agent {}, status {}", agentStatus.getAgentno(), AgentStatusEnum.READY);

        // 更新数据库
        agentStatusRes.save(agentStatus);
    }


    /**
     * 将消息发布到接收端
     *
     * @param chatMessage
     * @param agentUser
     */
    public void sendChatMessageByAgent(final ChatMessage chatMessage, final AgentUser agentUser) {
        Message outMessage = new Message();
        outMessage.setMessage(chatMessage.getMessage());
        outMessage.setCalltype(chatMessage.getCalltype());
        outMessage.setAgentUser(agentUser);

        // 设置SNSAccount信息
        if (StringUtils.isNotBlank(agentUser.getAppid())) {
            snsAccountRes.findOneBySnsTypeAndSnsIdAndOrgi(agentUser.getChannel(), agentUser.getAppid(), agentUser.getOrgi())
                    .ifPresent(outMessage::setSnsAccount);
        }

        outMessage.setContextid(chatMessage.getContextid());
        outMessage.setAttachmentid(chatMessage.getAttachmentid());
        outMessage.setMessageType(chatMessage.getMsgtype());
        outMessage.setCreatetime(Constants.DISPLAY_DATE_FORMATTER.format(chatMessage.getCreatetime()));
        outMessage.setChannelMessage(chatMessage);

        // 处理超时回复
        AgentUserTask agentUserTask = agentUserTaskRes.getOne(agentUser.getId());
        agentUserTask.setWarnings("0");
        agentUserTask.setWarningtime(null);

        agentUserTask.setReptime(null);
        agentUserTask.setReptimes("1");
        agentUserTask.setLastmessage(new Date());

        agentUserTaskRes.save(agentUserTask);

        // 发送消息给在线访客(此处也会生成对话聊天历史和会话监控消息)
        peerSyncIM.send(
                Enums.ReceiverType.VISITOR,
                Enums.ChannelType.toValue(agentUser.getChannel()),
                agentUser.getAppid(),
                Enums.MessageType.MESSAGE,
                chatMessage.getTouser(),
                outMessage,
                true);

        // 发送消息给坐席（返回消息给坐席自己）
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
     * 发送坐席的图片消息给访客和坐席自己
     *
     * @param creator
     * @param agentUser
     * @param multipart
     * @param sf
     */
    public void sendFileMessageByAgent(final User creator, final AgentUser agentUser, final MultipartFile multipart, final StreamingFile sf) {
        // 消息体
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
            // 发送消息
            outMessage.setFilename(multipart.getOriginalFilename());
            outMessage.setFilesize((int) multipart.getSize());
            outMessage.setChannelMessage(chatMessage);
            outMessage.setAgentUser(agentUser);
            outMessage.setCreatetime(Constants.DISPLAY_DATE_FORMATTER.format(new Date()));
            outMessage.setMessageType(chatMessage.getMsgtype());

            /**
             * 通知文件上传消息
             */
            // 发送消息给访客
            peerSyncIM.send(Enums.ReceiverType.VISITOR,
                    Enums.ChannelType.toValue(agentUser.getChannel()),
                    agentUser.getAppid(),
                    Enums.MessageType.MESSAGE,
                    agentUser.getUserid(), outMessage, true);

            // 发送给坐席自己
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
     * 将http的multipart保存到数据库
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

        // 保存到本地
        if (multipart.getContentType() != null && multipart.getContentType().contains(Constants.ATTACHMENT_TYPE_IMAGE)) {
            // 图片
            // process thumbnail
            File original = new File(uploadService.getUploadPath(), fileid + "_original");
            File thumbnail = new File(uploadService.getUploadPath(), fileid);
            FileCopyUtils.copy(multipart.getBytes(), original);
            ThumbnailUtils.processImage(thumbnail, original);
            sf.setThumbnail(jpaBlobHelper.createBlobWithFile(thumbnail));
            sf.setFileUrl("/res/image.html?id=" + fileid);
        } else {
            // 其它类型的文件
            if (multipart.getSize() == 0) {
                throw new ServerException("Empty upload file size.");
            }

            AttachmentFile attachmentFile = attachmentService.processAttachmentFile(multipart, fileid, creator.getOrgi(), creator.getId());
            sf.setFileUrl("/res/file.html?id=" + attachmentFile.getId());
        }

        // 保存文件到MySQL数据库
        sf.setId(fileid);
        sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
        sf.setName(multipart.getOriginalFilename());
        sf.setMime(multipart.getContentType());

        streamingFileRepository.save(sf);

        return sf;
    }

    /**
     * 获得一个User的AgentStatus
     * 先从缓存读取，再从数据库，还没有就新建
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
