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
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.cache.CacheService;
import com.github.xiaobo9.commons.exception.ServerException;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.entity.AgentUser;
import com.github.xiaobo9.entity.Contacts;
import com.github.xiaobo9.entity.OnlineUser;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.repository.AgentUserRepository;
import com.github.xiaobo9.repository.OnlineUserRepository;
import com.github.xiaobo9.repository.SNSAccountRepository;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;

import java.util.*;

/**
 * 向联系人发送消息
 */
@Service
public class ContactsService {

    private final static Logger logger = LoggerFactory.getLogger(ContactsService.class);

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AgentUserRepository agentUserRes;

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Value("${web.upload-path}")
    private String path;

    @Autowired
    private SNSAccountRepository snsAccountRes;
    @Autowired
    private OnlineUserService onlineUserService;

    /**
     * 在传输SkypeId中有操作混入了非法字符
     *
     * @param dirty
     * @return
     */
    public String sanitizeSkypeId(final String dirty) {
        if (dirty != null) {
            return dirty.replace(":", "\\:");
        }
        return null;
    }


    /**
     * 根据联系人ID获得一个当前可以触达联系人的方式（们）
     * 检查时过滤掉和其它坐席聊天中的联系人
     *
     * @param logined   当前查询该信息的访客
     * @param contactid 目标联系人ID
     * @return
     */
    public List<Enums.ChannelType> liveApproachChannelsByContactid(
            final User logined,
            final String contactid,
            final boolean isCheckSkype) throws ServerException {
//        logger.info("[liveApproachChannelsByContactid] contact id {}", contactid);

        List<Enums.ChannelType> result = new ArrayList<>();

        Optional<Contacts> contactOpt = contactsRes.findOneById(contactid).filter(p -> !p.isDatastatus());

        if (contactOpt.isPresent()) {
            final Contacts contact = contactOpt.get();

            // 查看 WebIM 渠道
            agentUserRes.findOneByContactIdAndStatusNotAndChannelAndOrgi(
                            contact.getId(),
                            AgentUserStatusEnum.END.toString(),
                            Enums.ChannelType.WEBIM.toString(),
                            contact.getOrgi())
                    .filter(p -> StringUtils.equals(p.getAgentno(), logined.getId()))
                    .ifPresent(p -> {
                        if (!cacheService.existBlackEntityByUserIdAndOrgi(p.getUserid(), logined.getOrgi())) {
                            // 访客在线 WebIM，排队或服务中
                            result.add(Enums.ChannelType.WEBIM);
                        } else {
                            // 该访客被拉黑
                        }
                    });

            // 查看 Skype 渠道
            if (isCheckSkype && StringUtils.isNotBlank(
                    contact.getSkypeid())) {
                // 查找匹配的OnlineUser
                Optional<OnlineUser> opt = onlineUserRes.findOneByContactidAndOrigAndChannel(
                        contactid,
                        logined.getOrgi(),
                        Constants.CSKEFU_MODULE_SKYPE);
                if (opt.isPresent()) {
                    // 联系人存在访客信息
                    // 并且该访客没有被拉黑
                    if (!cacheService.existBlackEntityByUserIdAndOrgi(
                            opt.get().getId(), logined.getOrgi())) {
                        Optional<AgentUser> agentUserOpt = cacheService.findOneAgentUserByUserIdAndOrgi(
                                opt.get().getId(), logined.getOrgi());
                        if (agentUserOpt.isPresent()) {
                            AgentUser agentUser = agentUserOpt.get();
                            if ((StringUtils.equals(
                                    agentUser.getStatus(), AgentUserStatusEnum.INSERVICE.toString())) &&
                                    (StringUtils.equals(agentUser.getAgentno(), logined.getId()))) {
                                // 该联系人的Skype账号被服务中
                                // TODO 此处可能是因为该联系的Skype对应的AgentUser没有被结束，长期被一个坐席占有
                                // 并不合理，后期需要加机制维护Skype的离线信息（1，Skype Agent查询;2, 加入最大空闲时间限制）
                                result.add(Enums.ChannelType.SKYPE);
                            }
                        } else {
                            // 该联系人的Skype OnlineUser存在，而且未被其它坐席占用
                            // 并且该联系人没有被拉黑
                            result.add(Enums.ChannelType.SKYPE);
                        }
                    }
                } else {
                    // 该联系人的Skype账号对应的OnlineUser不存在
                    // TODO 新建OnlineUser
                    onlineUserService.createNewOnlineUserWithContactAndChannel(
                            contact, logined, Constants.CSKEFU_MODULE_SKYPE);
                    result.add(Enums.ChannelType.SKYPE);
                }
            }
        } else {
            // can not find contact, may is deleted.
            throw new ServerException("Contact does not available.");
        }

//        logger.info("[liveApproachChannelsByContactid] get available list {}", StringUtils.join(result, "|"));

        return result;
    }

    /**
     * 批量查询联系人的可触达状态
     *
     * @param contacts
     * @param map
     * @param user
     */
    public void bindContactsApproachableData(final Page<Contacts> contacts, final ModelMap map, final User user) {
        Set<String> approachable = new HashSet<>();
        for (final Contacts c : contacts.getContent()) {
            try {
                if (liveApproachChannelsByContactid(user, c.getId(), isSkypeSetup(user.getOrgi())).size() > 0) {
                    approachable.add(c.getId());
                }
            } catch (ServerException e) {
                logger.warn("[bindContactsApproachableData] error", e);
            }
        }

        map.addAttribute("approachable", approachable);
    }

    /**
     * 检查Skype渠道是否被建立
     *
     * @return
     */
    public boolean isSkypeSetup(final String orgi) {
        if (MainContext.hasModule(Constants.CSKEFU_MODULE_SKYPE) && snsAccountRes.countBySnstypeAndOrgi(
                Constants.CSKEFU_MODULE_SKYPE, orgi) > 0) {
            return true;
        }
        return false;
    }

    public boolean match(Contacts newValue, Contacts oldValue) {
        return newValue.getName().equals(oldValue.getName()) &&
                newValue.getPhone().equals(oldValue.getPhone()) &&
                newValue.getEmail().equals(oldValue.getEmail()) &&
                newValue.getCity().equals(oldValue.getCity()) &&
                newValue.getAddress().equals(oldValue.getAddress()) &&
                newValue.getGender().equals(oldValue.getGender()) &&
                newValue.getProvince().equals(oldValue.getProvince()) &&
                newValue.getMemo().equals(oldValue.getMemo()) &&
                newValue.getMobileno().equals(oldValue.getMobileno()) &&
                StringUtils.equals(newValue.getSkypeid(), oldValue.getSkypeid()) &&
                StringUtils.equals(newValue.getCusbirthday(), oldValue.getCusbirthday())
                ;
    }

}
