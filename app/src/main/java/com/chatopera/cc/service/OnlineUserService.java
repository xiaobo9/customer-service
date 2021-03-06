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
import com.chatopera.cc.basic.IPUtils;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.persistence.interfaces.DataExchangeInterface;
import com.chatopera.cc.util.*;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.kit.CookiesKit;
import com.github.xiaobo9.commons.kit.ParameterKit;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import com.github.xiaobo9.commons.utils.BrowserClient;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.github.xiaobo9.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class OnlineUserService {
    public WebSseEmitterClient webIMClients;
    @Autowired
    private OnlineUserRepository onlineUserRes;
    @Autowired
    private UserRepository userRes;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private ConsultInviteRepository consultInviteRes;
    @Autowired
    private OnlineUserHisRepository onlineUserHisRes;
    @Autowired
    private UserTraceRepository userTraceRes;
    @Autowired
    private AgentUserContactsRepository agentUserContactsRes;
    @Autowired
    private ContactsRepository contactsRes;
    @Autowired
    private UserService userService;
    @Autowired
    private OrganRepository organRepository;

    @Autowired
    private OrgiSkillRelRepository skillRelRepository;

    @Autowired
    private SystemConfigService configService;

    public OnlineUser user(final String id) {

        return onlineUserRes.findById(id).orElse(null);
    }

    /**
     * ??????cache
     */
    public void cacheConsult(final CousultInvite consultInvite) {
        log.info("[cacheConsult] snsid {}, orgi {}", consultInvite.getSnsaccountid(), consultInvite.getOrgi());
        cacheService.putConsultInviteByOrgi(consultInvite.getOrgi(), consultInvite);
    }

    public CousultInvite consult(final String snsid, final String orgi) {
        CousultInvite consultInvite = cacheService.findOneConsultInviteBySnsidAndOrgi(snsid, orgi);
        if (consultInvite == null) {
            consultInvite = consultInviteRes.findBySnsaccountidAndOrgi(snsid, orgi);
            if (consultInvite != null) {
                cacheService.putConsultInviteByOrgi(orgi, consultInvite);
            }
        }
        return consultInvite;
    }

    /**
     * ???Cache?????????OnlineUser??????????????????????????????UserId???Orgi???Invite??????
     */
    public OnlineUser onlineuser(String userid, String orgi) {
        // ???Cache?????????
        return cacheService.findOneOnlineUserByUserIdAndOrgi(userid, orgi);
    }


    /**
     * @param orgi
     * @param ipdata       ?????? ip ?????????????????????
     * @param invite
     * @param isJudgeShare ??????????????????????????????
     * @return
     */
    public List<Organ> organ(String orgi, final IP ipdata, final CousultInvite invite, boolean isJudgeShare) {
        List<Organ> skillGroups = organ(orgi, invite, isJudgeShare);
        List<Organ> regOrganList = new ArrayList<>();
        for (Organ organ : skillGroups) {
            String area = organ.getArea();
            if (StringUtils.isBlank(area) || area.contains(ipdata.getProvince()) || area.contains(ipdata.getCity())) {
                regOrganList.add(organ);
            }
        }
        return regOrganList;
    }

    private List<Organ> organ(String orgi, CousultInvite invite, boolean isJudgeShare) {
        String origOrig = orgi;
        boolean isShare = false;
        SystemConfig systemConfig = configService.getSystemConfig();
        if (isJudgeShare && systemConfig.isEnabletneant() && systemConfig.isTenantshare()) {
            orgi = Constants.SYSTEM_ORGI;
            isShare = true;
        }
        List<Organ> skillGroups = cacheService.findOneSystemByIdAndOrgi(Constants.CACHE_SKILL + origOrig, origOrig);
        // ?????????????????????????????????
        if (skillGroups != null) {
            return skillGroups;
        }
        skillGroups = organRepository.findByOrgiAndSkill(orgi, true);
        // ??????????????? ??????????????????????????????????????????
        String whichOrgan = invite == null ? origOrig : invite.getOrgi();
        if (isShare && !(Constants.SYSTEM_ORGI.equals(whichOrgan))) {
            List<OrgiSkillRel> orgiSkillRelList = skillRelRepository.findByOrgi(whichOrgan);
            List<Organ> skillTempList = new ArrayList<>();
            if (!orgiSkillRelList.isEmpty()) {
                for (Organ organ : skillGroups) {
                    for (OrgiSkillRel rel : orgiSkillRelList) {
                        if (organ.getId().equals(rel.getSkillid())) {
                            skillTempList.add(organ);
                        }
                    }
                }
            }
            skillGroups = skillTempList;
        }
        // ????????????
        if (skillGroups.size() > 0) {
            cacheService.putSystemListByIdAndOrgi(Constants.CACHE_SKILL + origOrig, origOrig, skillGroups);
        }
        return skillGroups;
    }

    public List<Topic> topic(List<KnowledgeType> topicTypeList, List<Topic> topicList) {
        List<Topic> tempTopicList = new ArrayList<>();
        if (topicList != null) {
            for (Topic topic : topicList) {
                if (StringUtils.isBlank(topic.getCate()) || Constants.DEFAULT_TYPE.equals(
                        topic.getCate()) || getTopicType(topic.getCate(), topicTypeList) != null) {
                    tempTopicList.add(topic);
                }
            }
        }
        return tempTopicList;
    }

    /**
     * ???????????????????????? ????????? ??????
     */
    public List<KnowledgeType> filterTopicType(List<KnowledgeType> topicTypeList, List<Topic> topicList) {
        List<KnowledgeType> tempTopicTypeList = new ArrayList<>();
        if (topicTypeList != null) {
            for (KnowledgeType knowledgeType : topicTypeList) {
                boolean hasTopic = false;
                for (Topic topic : topicList) {
                    if (knowledgeType.getId().equals(topic.getCate())) {
                        hasTopic = true;
                        break;
                    }
                }
                if (hasTopic) {
                    tempTopicTypeList.add(knowledgeType);
                }
            }
        }
        return tempTopicTypeList;
    }

    /**
     * ???????????????????????? ??????
     */
    private KnowledgeType getTopicType(String cate, List<KnowledgeType> topicTypeList) {
        KnowledgeType kt = null;
        for (KnowledgeType knowledgeType : topicTypeList) {
            if (knowledgeType.getId().equals(cate)) {
                kt = knowledgeType;
                break;
            }
        }
        return kt;
    }

    @SuppressWarnings("unchecked")
    public List<User> agents(String orgi) {
        String origOrig = orgi;

        List<User> agentList = userRes.findByOrgiAndAgentAndDatastatus(orgi, true, false);
        List<User> agentTempList = new ArrayList<User>();
        List<Organ> skillOrgansByOrgi = organRepository.findByOrgiAndSkill(origOrig, true);

        if (!skillOrgansByOrgi.isEmpty()) {
            for (User user : agentList) {
                // ?????????????????????????????????????????????????????????
                if (user.isAdmin() || user.isSuperadmin()) continue;

                // ????????????????????????????????????????????????
                if (cacheService.findOneAgentStatusByAgentnoAndOrig(user.getId(), origOrig) == null) continue;

                // ????????????????????????????????????
                userService.attachOrgansPropertiesForUser(user);
                for (Organ organ : skillOrgansByOrgi) {
                    if (user.getOrgans().size() > 0 && user.inAffiliates(organ.getId())) {
                        agentTempList.add(user);
                    }
                }
            }
        }
        return agentTempList;
    }

    public Contacts processContacts(
            final String orgi,
            Contacts contacts,
            final String appid,
            final String userid) {
        if (contacts != null) {
            if (StringUtils.isNotBlank(contacts.getName()) || StringUtils.isNotBlank(contacts.getPhone()) || StringUtils.isNotBlank(contacts.getEmail())) {
                StringBuilder query = new StringBuilder();
                query.append(contacts.getName());
                if (StringUtils.isNotBlank(contacts.getPhone())) {
                    query.append(" OR ").append(contacts.getPhone());
                }
                if (StringUtils.isNotBlank(contacts.getEmail())) {
                    query.append(" OR ").append(contacts.getEmail());
                }
                Page<Contacts> contactsList = contactsRes.findByOrgi(
                        orgi, false, query.toString(), PageRequest.of(0, 1));
                if (contactsList.getContent().size() > 0) {
                    contacts = contactsList.getContent().get(0);
                } else {
//					contactsRes.save(contacts) ;	//????????????????????????????????????????????????????????????????????????????????????
                    contacts.setId(null);
                }
            } else {
                contacts.setId(null);
            }

            if (contacts != null && StringUtils.isNotBlank(contacts.getId())) {
                if (!agentUserContactsRes.findOneByUseridAndOrgi(userid, orgi).isPresent()) {
                    AgentUserContacts agentUserContacts = new AgentUserContacts();
                    agentUserContacts.setAppid(appid);
                    agentUserContacts.setChannel(Enums.ChannelType.WEBIM.toString());
                    agentUserContacts.setContactsid(contacts.getId());
                    agentUserContacts.setUserid(userid);
                    agentUserContacts.setOrgi(orgi);
                    agentUserContacts.setCreatetime(new Date());
                    agentUserContactsRes.save(agentUserContacts);
                }
            } else if (StringUtils.isNotBlank(userid)) {
                Optional<AgentUserContacts> agentUserContactOpt = agentUserContactsRes.findOneByUseridAndOrgi(
                        userid, orgi);
                if (agentUserContactOpt.isPresent()) {
                    contacts = contactsRes.findById(agentUserContactOpt.get().getContactsid()).orElse(null);
                }
            }
        }
        return contacts;
    }

    /**
     * ??????OnlineUser?????????
     * ??????user????????????????????????????????????fingerprint2?????????ID??????????????????
     */
    public OnlineUser online(
            final User user,
            final String orgi,
            final String sessionid,
            final String optype,
            final HttpServletRequest request,
            final String channel,
            final String appid,
            final Contacts contacts,
            final CousultInvite invite) {
        if (invite == null) {
            return null;
        }
        final Date now = new Date();
        // resolve user from cache or db.
        OnlineUser onlineUser = onlineuser(user.getId(), orgi);
        if (onlineUser == null) {
            onlineUser = new OnlineUser();
            onlineUser.setId(user.getId());
            onlineUser.setCreater(user.getId());
            onlineUser.setUsername(user.getUsername());
            onlineUser.setCreatetime(now);
            onlineUser.setUpdatetime(now);
            onlineUser.setUpdateuser(user.getUsername());
            onlineUser.setSessionid(sessionid);

            if (contacts != null) {
                onlineUser.setContactsid(contacts.getId());
            }

            onlineUser.setOrgi(orgi);
            onlineUser.setChannel(channel);

            // ???Server session?????????????????????????????????????????????
            String cookie = CookiesKit.getCookie(request, "R3GUESTUSEKEY")
                    .map(Cookie::getValue).orElse(null);
            // 1 ?????? ?????????session????????????
            onlineUser.setOlduser(StringUtils.equals(user.getSessionid(), cookie) ? "0" : "1");
            onlineUser.setMobile(BrowserClient.isMobile(request.getHeader("User-Agent")) ? "1" : "0");

            String url = request.getHeader("referer");
            onlineUser.setUrl(url);
            if (StringUtils.isNotBlank(url)) {
                onlineUser.setSource(this.getSource(url));
            }
            onlineUser.setAppid(appid);
            onlineUser.setUserid(user.getId());
            onlineUser.setUsername(user.getUsername());

            if (StringUtils.isNotBlank(request.getParameter("title"))) {
                String title = request.getParameter("title");
                if (title.length() > 255) {
                    onlineUser.setTitle(title.substring(0, 255));
                } else {
                    onlineUser.setTitle(title);
                }
            }

            onlineUser.setLogintime(now);

            // ????????????
            String ip = IPUtils.getIpAddress(request);
            onlineUser.setIp(ip);
            IP ipdata = IPTools.findGeography(ip);
            onlineUser.setCountry(ipdata.getCountry());
            onlineUser.setProvince(ipdata.getProvince());
            onlineUser.setCity(ipdata.getCity());
            onlineUser.setIsp(ipdata.getIsp());
            onlineUser.setRegion(ipdata + "???" + ip + "???");

            onlineUser.setDatestr(new SimpleDateFormat("yyyMMdd")
                    .format(now));

            onlineUser.setHostname(ip);
            onlineUser.setSessionid(sessionid);
            onlineUser.setOptype(optype);
            onlineUser.setStatus(Enums.OnlineUserStatusEnum.ONLINE.toString());
            final BrowserClient client = BrowserClient.parseClient(request.getHeader(BrowserClient.USER_AGENT));

            // ???????????????
            onlineUser.setOpersystem(client.getOs());
            onlineUser.setBrowser(client.getBrowser());
            onlineUser.setUseragent(client.getUseragent());

            log.info("[online] new online user is created but not persisted.");
        } else {
            // ???DB???????????????OnlineUser
            onlineUser.setCreatetime(now); // ??????????????????
            if ((StringUtils.isNotBlank(onlineUser.getSessionid()) && !StringUtils.equals(
                    onlineUser.getSessionid(), sessionid)) ||
                    !StringUtils.equals(
                            Enums.OnlineUserStatusEnum.ONLINE.toString(), onlineUser.getStatus())) {
                // ?????????session??????DB??????????????????session?????????????????????????????????????????????OnlineUser????????????ONLINE???
                // ??????????????????????????????Session???????????????????????????

                onlineUser.setStatus(Enums.OnlineUserStatusEnum.ONLINE.toString()); // ?????????????????????
                onlineUser.setChannel(channel);          // ????????????
                onlineUser.setAppid(appid);
                onlineUser.setUpdatetime(now);           // ??????????????????
                if (StringUtils.isNotBlank(onlineUser.getSessionid()) && !StringUtils.equals(
                        onlineUser.getSessionid(), sessionid)) {
                    onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.DEFAULT.toString());
                    onlineUser.setSessionid(sessionid);  // ????????????session??????
                    onlineUser.setLogintime(now);        // ??????????????????
                    onlineUser.setInvitetimes(0);        // ??????????????????
                }
            }

            // ???????????????????????????
            if (contacts != null) {
                // ?????????????????????
                if (StringUtils.isNotBlank(contacts.getId()) && StringUtils.isNotBlank(
                        contacts.getName()) && (StringUtils.isBlank(
                        onlineUser.getContactsid()) || !contacts.getName().equals(onlineUser.getUsername()))) {
                    if (StringUtils.isBlank(onlineUser.getContactsid())) {
                        onlineUser.setContactsid(contacts.getId());
                    }
                    if (!contacts.getName().equals(onlineUser.getUsername())) {
                        onlineUser.setUsername(contacts.getName());
                    }
                    onlineUser.setUpdatetime(now);
                }
            }

            if (StringUtils.isBlank(onlineUser.getUsername()) && StringUtils.isNotBlank(user.getUsername())) {
                onlineUser.setUseragent(user.getUsername());
                onlineUser.setUpdatetime(now);
            }
        }

        if (invite.isRecordhis() && StringUtils.isNotBlank(request.getParameter("traceid"))) {
            UserTraceHistory trace = new UserTraceHistory();
            trace.setId(request.getParameter("traceid"));
            trace.setTitle(request.getParameter("title"));
            trace.setUrl(request.getParameter("url"));
            trace.setOrgi(invite.getOrgi());
            trace.setUpdatetime(new Date());
            trace.setUsername(onlineUser.getUsername());
            userTraceRes.save(trace);
        }

        // ?????????????????????OnlineUser, ?????????????????????
        if (StringUtils.isNotBlank(onlineUser.getUserid())) {
            // ???????????????????????????
            onlineUserRes.save(onlineUser);
        }
        return onlineUser;
    }

    /**
     * ??????onlineUser?????????
     */
    public void offline(String user, String orgi) {
        OnlineUser onlineUser = cacheService.findOneOnlineUserByUserIdAndOrgi(user, orgi);
        if (onlineUser != null) {
            onlineUser.setStatus(Enums.OnlineUserStatusEnum.OFFLINE.toString());
            onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.DEFAULT.toString());
            onlineUser.setBetweentime((int) (new Date().getTime() - onlineUser.getLogintime().getTime()));
            onlineUser.setUpdatetime(new Date());

            onlineUserRes.save(onlineUser);

            final OnlineUserHis his = onlineUserHisRes.findOneBySessionidAndOrgi(
                    onlineUser.getSessionid(), onlineUser.getOrgi()).orElseGet(OnlineUserHis::new);
            MainUtils.copyProperties(onlineUser, his);
            his.setDataid(onlineUser.getId());
            onlineUserHisRes.save(his);
        }
        cacheService.deleteOnlineUserByIdAndOrgi(user, orgi);
    }

    public void refuseInvite(final String user) {
        onlineUserRes.findById(user)
                .ifPresent(onlineUser -> {
                    onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.REFUSE.toString());
                    onlineUser.setRefusetimes(onlineUser.getRefusetimes() + 1);

                    onlineUserRes.save(onlineUser);
                });
    }

    public String getSource(String url) {
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            log.info("[online] error when parsing URL", e);
            return null;
        }
    }

    /**
     * ????????????
     *
     * @param userid
     * @throws Exception
     */
    public void sendWebIMClients(String userid, String msg) throws Exception {
//        logger.info("[sendWebIMClients] userId {}, msg {}", userid, msg);
        List<WebIMClient> clients = webIMClients.getClients(userid);

        if (clients != null && clients.size() > 0) {
            for (WebIMClient client : clients) {
                try {
                    client.getSse().send(SseEmitter.event().reconnectTime(0).data(msg));
//                    logger.info("[sendWebIMClients] sent done with client {}", client.getClient());
                } catch (Exception ex) {
                    // ?????????????????????????????????????????????
//                    logger.info("[sendWebIMClients] lost connection", ex);
                    // cleanup connections hold in server side
                    webIMClients.removeClient(userid, client.getClient(), false);
                } finally {
                    client.getSse().complete();
                }
            }
        }
    }

    public void resetHotTopic(DataExchangeInterface dataExchange, User user, String orgi, String aiid) {
        cacheService.deleteSystembyIdAndOrgi("xiaoeTopic", orgi);
        cacheHotTopic(dataExchange, user, orgi, aiid);
    }

    public List<Topic> cacheHotTopic(DataExchangeInterface dataExchange, User user, String orgi, String aiid) {
        List<Topic> topicList = null;
        if ((topicList = cacheService.findOneSystemListByIdAndOrgi("xiaoeTopic", orgi)) == null) {
            topicList = (List<Topic>) dataExchange.getListDataByIdAndOrgi(aiid, null, orgi);
            cacheService.putSystemListByIdAndOrgi("xiaoeTopic", orgi, topicList);
        }
        return topicList;
    }

    public void resetHotTopicType(DataExchangeInterface dataExchange, User user, String orgi, String aiid) {
        if (cacheService.existSystemByIdAndOrgi("xiaoeTopicType" + "." + orgi, orgi)) {
            cacheService.deleteSystembyIdAndOrgi("xiaoeTopicType" + "." + orgi, orgi);
        }
        cacheHotTopicType(dataExchange, user, orgi, aiid);
    }

    @SuppressWarnings("unchecked")
    public List<KnowledgeType> cacheHotTopicType(DataExchangeInterface dataExchange, User user, String orgi, String aiid) {
        List<KnowledgeType> topicTypeList = null;
        if ((topicTypeList = cacheService.findOneSystemListByIdAndOrgi("xiaoeTopicType" + "." + orgi, orgi)) == null) {
            topicTypeList = (List<KnowledgeType>) dataExchange.getListDataByIdAndOrgi(aiid, null, orgi);
            cacheService.putSystemListByIdAndOrgi("xiaoeTopicType" + "." + orgi, orgi, topicTypeList);
        }
        return topicTypeList;
    }

    /**
     * ??????Skype????????????onlineUser??????
     */
    public OnlineUser createNewOnlineUserWithContactAndChannel(final Contacts contact, final User logined, final String channel) {
        final Date now = new Date();
        OnlineUser onlineUser = new OnlineUser();
        onlineUser.setId(UUIDUtils.getUUID());
        onlineUser.setUserid(onlineUser.getId());
        onlineUser.setLogintime(now);
        onlineUser.setUpdateuser(logined.getId());
        onlineUser.setContactsid(contact.getId());
        onlineUser.setUsername(contact.getName());
        onlineUser.setChannel(channel);
        onlineUser.setCity(contact.getCity());
        onlineUser.setOrgi(logined.getOrgi());
        onlineUser.setCreater(logined.getId());

        log.info("[createNewOnlineUserWithContactAndChannel] onlineUser id {}, userId {}", onlineUser.getId(), onlineUser.getUserid());
        // TODO ?????????????????? onlineUser ??? appid

        onlineUserRes.save(onlineUser);
        return onlineUser;

    }

    public void removeClient(String userid, String client, boolean timeout) throws Exception {
        webIMClients.removeClient(userid, client, timeout);
    }

    public void onlineUserInfo(User user, ModelMap map, String orgi) {
        String userId = user.getId();
        Date startTime = MainUtils.getStartTime();
        Date endTime = MainUtils.getEndTime();
        map.put("inviteResult", MainUtils.getWebIMInviteResult(
                onlineUserRes.findByOrgiAndAgentnoAndCreatetimeRange(orgi, userId, startTime, endTime)));

        map.put("agentUserCount", onlineUserRes.countByAgentForAgentUser(
                orgi, AgentUserStatusEnum.INSERVICE.toString(), userId, startTime, endTime));

        map.put("agentServicesCount", onlineUserRes.countByAgentForAgentUser(
                orgi, AgentUserStatusEnum.END.toString(), userId, startTime, endTime));

        map.put("agentServicesAvg", onlineUserRes.countByAgentForAvagTime(
                orgi, AgentUserStatusEnum.END.toString(), userId, startTime, endTime));
    }

    @PostConstruct
    public void postConstruct() {
        webIMClients = new WebSseEmitterClient(this);
    }
}
