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

import com.chatopera.cc.acd.ACDPolicyService;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.cache.CacheService;
import com.github.xiaobo9.commons.exception.ServerException;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import com.chatopera.cc.peer.PeerSyncIM;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.kit.CookiesKit;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.AgentStatusRepository;
import com.chatopera.cc.socketio.message.Message;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.repository.*;
import freemarker.template.TemplateException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Service
public class AgentUserService {
    private final static Logger logger = LoggerFactory.getLogger(AgentUserService.class);

    // ????????????
    private final static String AUDIT_PERMISSION_READ = "R";
    private final static String AUDIT_PERMISSION_WRITE = "W";
    private final static String AUDIT_PERMISSION_TRANS = "T";
    private final static String AUDIT_PERMISSION_TOTAL = "RWT";

    // ????????????
    private final static String AUTH_KEY_AUDIT_READ = "A13_A01_A01";

    // ????????????
    private final static String AUTH_KEY_AUDIT_WRITE = "A13_A01_A02";

    // ????????????
    private final static String AUTH_KEY_AUDIT_TRANS = "A13_A01_A03";

    @Autowired
    private ACDPolicyService acdPolicyService;

    @Autowired
    private AgentUserRepository agentUserRes;

    @Autowired
    private RoleAuthRepository roleAuthRes;

    @Autowired
    private UserRepository userRes;

    @Autowired
    private UserRoleRepository userRoleRes;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private SNSAccountRepository snsAccountRes;

    @Autowired
    private AgentServiceProxy agentServiceProxy;

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private AgentUserContactsRepository agentUserContactsRes;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AgentStatusRepository agentStatusRes;

    @Autowired
    private PeerSyncIM peerSyncIM;

    @Autowired
    private OnlineUserService onlineUserService;

    /**
     * ???????????????????????????????????????AgentUser
     *
     * @param channels
     * @param contactid
     * @param logined
     * @return
     * @throws ServerException
     */
    public AgentUser figureAgentUserBeforeChatWithContactInfo(final String channels, final String contactid, final User logined) throws ServerException {
        // ?????????????????????
        AgentUser agentUser = null;
        OnlineUser onlineUser;

        // ??????????????????????????????
        if (StringUtils.isNotBlank(channels)) {
            // ???????????????????????????????????????
            String channel = StringUtils.split(channels, ",")[0];

            // ???????????????
            final Contacts contact = contactsRes.findById(contactid).orElseThrow(EntityNotFoundEx::new);

            // ?????? OnlineUser
            onlineUser = onlineUserRes.findOneByContactidAndOrigAndChannel(
                    contactid, logined.getOrgi(), channel).orElseGet(() -> {
                return onlineUserService.createNewOnlineUserWithContactAndChannel(contact, logined, channel);
            });

            // ????????????????????? AgentUser
            final Optional<AgentUser> op = agentUserRes.findOneByContactIdAndChannelAndOrgi(contactid, channel,
                    logined.getOrgi());
            if (op.isPresent()) {
                if (StringUtils.equals(op.get().getAgentno(), logined.getId())) {
                    agentUser = op.get();
                } else {
                    logger.info(
                            "[chat] can not bind to current agentno due to inserv status with another AgentUser id {}, Agent {}",
                            op.get().getId(), op.get().getUserid());
                    // do nothing
                }
            } else {
                // ????????????AgentUser
                agentUser = createAgentUserWithContactAndAgentAndChannelAndStatus(
                        onlineUser, contact, logined, channel, AgentUserStatusEnum.INSERVICE.toString(),
                        logined);
            }
        }

        if (agentUser != null) {
            logger.info("[figureAgentUserBeforeChatWithContactInfo] agent user is not null");
            // ????????????
            // ?????????????????????
            Message outMessage = new Message();
            outMessage.setChannelMessage(agentUser);
            outMessage.setAgentUser(agentUser);
            peerSyncIM.send(Enums.ReceiverType.AGENT,
                    Enums.ChannelType.WEBIM,
                    agentUser.getAppid(),
                    Enums.MessageType.NEW,
                    logined.getId(),
                    outMessage, true);
        } else {
            logger.info("[figureAgentUserBeforeChatWithContactInfo] agent user is null");
        }


        return agentUser;
    }

    /**
     * ??????????????????
     *
     * @param view
     * @param map
     * @param request
     * @param response
     * @param sort
     * @param user
     * @param orgi
     */
    public void buildIndexViewWithModels(
            final ModelAndView view,
            final ModelMap map,
            final HttpServletRequest request,
            final HttpServletResponse response,
            String sort,
            final User user,
            final String orgi,
            final AgentUser agentUser) {
        Sort defaultSort = null;
        if (StringUtils.isBlank(sort)) {
            Optional<Cookie> optional = CookiesKit.getCookie(request, "sort");
            if (optional.isPresent()) {
                sort = optional.get().getValue();
            }
        }
        if (StringUtils.isNotBlank(sort)) {
            List<Sort.Order> list = new ArrayList<>();
            if (sort.equals("lastmessage")) {
                list.add(new Sort.Order(Sort.Direction.DESC, "status"));
                list.add(new Sort.Order(Sort.Direction.DESC, "lastmessage"));
            } else if (sort.equals("logintime")) {
                list.add(new Sort.Order(Sort.Direction.DESC, "status"));
                list.add(new Sort.Order(Sort.Direction.DESC, "createtime"));
            } else if (sort.equals("default")) {
                defaultSort = new Sort(Sort.Direction.DESC, "status");
                Cookie name = new Cookie("sort", null);
                name.setMaxAge(0);
                response.addCookie(name);
            }
            if (list.size() > 0) {
                defaultSort = Sort.by(list);
                Cookie name = new Cookie("sort", sort);
                name.setMaxAge(60 * 60 * 24 * 365);
                response.addCookie(name);
                map.addAttribute("sort", sort);
            }
        } else {
            defaultSort = new Sort(Sort.Direction.DESC, "status");
        }

        List<AgentUser> agentUserList = agentUserRes.findByAgentnoAndOrgi(user.getId(), user.getOrgi(), defaultSort);

        if (agentUserList.size() > 0) {
            if (agentUser != null) {
                List<AgentUser> agentUserListSimple = new ArrayList<>();
                agentUserListSimple.add(agentUser);

                for (final AgentUser x : agentUserList) {
                    if (StringUtils.equals(x.getId(), agentUser.getId())) {
                        continue;
                    }
                    agentUserListSimple.add(x);
                }

                agentUserList = agentUserListSimple;
            }
        } else if (agentUser != null) {
            agentUserList.add(agentUser);
        }

//        SessionConfig sessionConfig = acdPolicyService.initSessionConfig(agentUser.getSkill(), user.getOrgi());
//        view.addObject("sessionConfig", sessionConfig);
//       if (sessionConfig.isOtherquickplay()) {
//           view.addObject("topicList", onlineUserProxy.search(null, user.getOrgi(), user));
//       }

        if (agentUserList.size() > 0) {
            view.addObject("agentUserList", agentUserList);
            agentServiceProxy.bundleDialogRequiredDataInView(view, map, agentUserList.get(0), orgi, user);
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     *
     * @param orgi
     * @param agentUser
     * @return
     */
    public Map<String, String> getAgentUserSubscribers(final String orgi, final AgentUser agentUser) {
        Map<String, String> result = new HashMap<>();
        Set<String> bypass = new HashSet<>();

        // ????????????
        if (StringUtils.isNotBlank(agentUser.getAgentno())) {
            bypass.add(agentUser.getAgentno());
        }

        // ?????????????????????
        List<User> admins = userRes.findByAdminAndOrgi(true, orgi);

        for (final User user : admins) {
            if (bypass.contains(user.getId())) continue;
            addPermissions(user.getId(), AUDIT_PERMISSION_TOTAL, result);
            bypass.add(user.getId());
        }

        // ??????????????????
        loadPermissionsFromDB(orgi, AUTH_KEY_AUDIT_READ, bypass, result);

        // ??????????????????
        loadPermissionsFromDB(orgi, AUTH_KEY_AUDIT_WRITE, bypass, result);

        // ??????????????????
        loadPermissionsFromDB(orgi, AUTH_KEY_AUDIT_TRANS, bypass, result);

        // DEBUG
        for (final String userId : result.keySet()) {
            logger.info("[getAgentUserSubscribers] agentUserId {} user {} permissions {}",
                    agentUser.getId(), userId, result.get(userId));
        }

        return result;
    }


    /**
     * ???????????????KEY??????????????????????????????
     *
     * @param key
     * @param bypass
     * @param result
     */
    private void loadPermissionsFromDB(final String orgi, final String key, final Set<String> bypass, final Map<String, String> result) {
        List<RoleAuth> roleAuths = roleAuthRes.findByDicvalueAndOrgi(key, orgi);
        for (final RoleAuth roleAuth : roleAuths) {
            List<String> users = userRoleRes.findByOrgiAndRoleId(orgi, roleAuth.getRoleid());
            for (String user : users) {
                if (!bypass.contains(user)) {
                    addPermission(user, key, result);
                }
            }
        }
    }

    /**
     * ??????????????????
     *
     * @param user
     * @param permissions
     * @param result
     */
    private void addPermissions(final String user, final String permissions, final Map<String, String> result) {
        result.put(user, permissions);
    }

    /**
     * ????????????
     *
     * @param userId
     * @param authKey
     * @param result
     */
    private void addPermission(final String userId, final String authKey, final Map<String, String> result) {
        if (!result.containsKey(userId)) {
            result.put(userId, "");
        }

        String value = result.get(userId);
        switch (authKey) {
            case AUTH_KEY_AUDIT_READ:
                if (!value.contains(AUDIT_PERMISSION_READ)) {
                    result.put(userId, value + AUDIT_PERMISSION_READ);
                }
                break;
            case AUTH_KEY_AUDIT_WRITE:
                if (!value.contains(AUDIT_PERMISSION_WRITE)) {
                    result.put(userId, value + AUDIT_PERMISSION_WRITE);
                }
                break;
            case AUTH_KEY_AUDIT_TRANS:
                if (!value.contains(AUDIT_PERMISSION_TRANS)) {
                    result.put(userId, value + AUDIT_PERMISSION_TRANS);
                }
                break;
            default:
                logger.info("[addPermission] invalid key {}", authKey);
        }

    }


    /**
     * ??????AgentUser
     *
     * @param onlineUser
     * @param contact
     * @param agent
     * @param channel
     * @param status
     * @param creator
     * @return
     * @throws ServerException
     */
    public AgentUser createAgentUserWithContactAndAgentAndChannelAndStatus(
            final OnlineUser onlineUser,
            final Contacts contact,
            final User agent,
            final String channel,
            final String status, final User creator) throws ServerException {
        logger.info("[createAgentUserWithContactAndAgentAndChannelAndStatus] create new agent user");
        final Date now = new Date();
        AgentUser agentUser = new AgentUser();
        agentUser.setNickname(contact.getNickname());
        agentUser.setUsername(contact.getName());
        agentUser.setAgentno(agent.getId());
        agentUser.setAgentname(agent.getUname());
        agentUser.setCreatetime(now);
        agentUser.setUpdatetime(now);
        agentUser.setChannel(channel);
        agentUser.setLogindate(now);
        agentUser.setUserid(onlineUser.getId());
        agentUser.setCreater(creator.getId());
        agentUser.setStatus(status);
        agentUser.setServicetime(now);
        agentUser.setOrgi(creator.getOrgi());

        // ?????? appId
        if (StringUtils.equals(channel, Enums.ChannelType.SKYPE.toString())) {
            final SNSAccount snsAccount = snsAccountRes.findOneBySnstypeAndOrgi(
                    Enums.ChannelType.SKYPE.toString(), agent.getOrgi());
            if (snsAccount != null) {
                agentUser.setAppid(snsAccount.getSnsid());
            } else {
                throw new ServerException("Skype Channel is not available.");
            }
        }

        // ?????? AgentService
        AgentService agentService = new AgentService();
        MainUtils.copyProperties(agentUser, agentService);
        agentUser.setAgentserviceid(agentService.getId());
        agentService.setAgentuserid(agentUser.getId());
        agentService.setAgentusername(agentUser.getAgentname());

        agentServiceRes.save(agentService);
        agentUserRes.save(agentUser);

        // ??????AgentUserContact
        AgentUserContacts agentUserContact = new AgentUserContacts();
        agentUserContact.setAppid(agentUser.getAppid());
        agentUserContact.setChannel(agentUser.getChannel());
        agentUserContact.setContactsid(contact.getId());
        agentUserContact.setUserid(onlineUser.getId());
        agentUserContact.setCreater(creator.getId());
        agentUserContact.setOrgi(creator.getOrgi());
        agentUserContact.setUsername(contact.getUsername());
        agentUserContact.setCreatetime(now);
        agentUserContactsRes.save(agentUserContact);

        return agentUser;
    }

    /**
     * ??????AgentUser
     * ??????????????????????????????????????????
     *
     * @param userid
     * @param agentuserid
     * @param orgi
     * @return
     * @throws ServerException
     */
    public AgentUser resolveAgentUser(final String userid, final String agentuserid, final String orgi) throws ServerException {
        Optional<AgentUser> opt = cacheService.findOneAgentUserByUserIdAndOrgi(userid, orgi);
        if (!opt.isPresent()) {
            return agentUserRes.findById(agentuserid).orElseThrow(() -> new EntityNotFoundEx("Invalid transfer request, agent user not exist."));
        }
        return opt.get();
    }

    /**
     * ??????????????????????????????????????????
     * #TODO ??????????????????
     *
     * @param agentStatus
     * @param orgi
     */
    public synchronized void updateAgentStatus(AgentStatus agentStatus, String orgi) {
        int users = cacheService.getInservAgentUsersSizeByAgentnoAndOrgi(agentStatus.getAgentno(), orgi);
        agentStatus.setUsers(users);
        agentStatus.setUpdatetime(new Date());
        agentStatusRes.save(agentStatus);
    }

    /**
     * ??????AgentUser??????
     *
     * @param id
     * @return
     */
    public Optional<AgentUser> findById(final String id) {
        return agentUserRes.findById(id);
    }

    /**
     * ??????
     *
     * @param agentUser
     */
    public void save(final AgentUser agentUser) {
        agentUserRes.save(agentUser);
    }


}
