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
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.kit.ParameterKit;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import com.github.xiaobo9.commons.utils.BrowserClient;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.CharacterCodingException;
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
     * 更新cache
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
     * 在Cache中查询OnlineUser，或者从数据库中根据UserId，Orgi和Invite查询
     *
     * @param userid
     * @param orgi
     * @return
     */
    public OnlineUser onlineuser(String userid, String orgi) {
        // 从Cache中查找
        return cacheService.findOneOnlineUserByUserIdAndOrgi(userid, orgi);
    }


    /**
     * @param orgi
     * @param ipdata
     * @param invite
     * @param isJudgeShare 是否判断是否共享租户
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Organ> organ(String orgi, final IP ipdata, final CousultInvite invite, boolean isJudgeShare) {
        String origOrig = orgi;
        boolean isShare = false;
        if (isJudgeShare) {
            SystemConfig systemConfig = configService.getSystemConfig();
            if (systemConfig.isEnabletneant() && systemConfig.isTenantshare()) {
                orgi = Constants.SYSTEM_ORGI;
                isShare = true;
            }
        }
        List<Organ> skillGroups = cacheService.findOneSystemByIdAndOrgi(Constants.CACHE_SKILL + origOrig, origOrig);
        if (skillGroups == null) {
            skillGroups = organRepository.findByOrgiAndSkill(orgi, true);
            // 租户共享时 查出该租住要显的绑定的技能组
            if (isShare && !(Constants.SYSTEM_ORGI.equals((invite == null ? origOrig : invite.getOrgi())))) {
                List<OrgiSkillRel> orgiSkillRelList = null;
                orgiSkillRelList = skillRelRepository.findByOrgi((invite == null ? origOrig : invite.getOrgi()));
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

            if (skillGroups.size() > 0) {
                cacheService.putSystemListByIdAndOrgi(Constants.CACHE_SKILL + origOrig, origOrig, skillGroups);
            }
        }

        if (ipdata == null && invite == null) {
            return skillGroups;
        }

        List<Organ> regOrganList = new ArrayList<>();
        for (Organ organ : skillGroups) {
            String area = organ.getArea();
            if (StringUtils.isNotBlank(area)) {
                if (area.contains(ipdata.getProvince()) || area.contains(ipdata.getCity())) {
                    regOrganList.add(organ);
                }
            } else {
                regOrganList.add(organ);
            }
        }
        return regOrganList;
    }


    /**
     * @param orgi
     * @param isJudgeShare
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Organ> organ(String orgi, boolean isJudgeShare) {
        return organ(orgi, null, null, isJudgeShare);
    }

    private List<AreaType> getAreaTypeList(String area, List<AreaType> areaTypeList) {
        List<AreaType> atList = new ArrayList<AreaType>();
        if (areaTypeList != null && areaTypeList.size() > 0) {
            for (AreaType areaType : areaTypeList) {
                if (StringUtils.isNotBlank(area) && area.indexOf(areaType.getId()) >= 0) {
                    atList.add(areaType);
                }
            }
        }
        return atList;
    }

    /**
     * 只要有一级 地区命中就就返回
     *
     * @param orgi
     * @param ipdata
     * @param topicTypeList
     * @return
     */
    public List<KnowledgeType> topicType(String orgi, IP ipdata, List<KnowledgeType> topicTypeList) {
        List<KnowledgeType> tempTopicTypeList = new ArrayList<KnowledgeType>();
        for (KnowledgeType topicType : topicTypeList) {
            if (getParentArea(ipdata, topicType, topicTypeList) != null) {
                tempTopicTypeList.add(topicType);
            }
        }
        return tempTopicTypeList;
    }

    /**
     * @param topicType
     * @param topicTypeList
     * @return
     */
    private KnowledgeType getParentArea(IP ipdata, KnowledgeType topicType, List<KnowledgeType> topicTypeList) {
        KnowledgeType area = null;
        if (StringUtils.isNotBlank(topicType.getArea())) {
            if ((topicType.getArea().indexOf(ipdata.getProvince()) >= 0 || topicType.getArea().indexOf(
                    ipdata.getCity()) >= 0)) {
                area = topicType;
            }
        } else {
            if (StringUtils.isNotBlank(topicType.getParentid()) && !topicType.getParentid().equals("0")) {
                for (KnowledgeType temp : topicTypeList) {
                    if (temp.getId().equals(topicType.getParentid())) {
                        if (StringUtils.isNotBlank(temp.getArea())) {
                            if ((temp.getArea().indexOf(ipdata.getProvince()) >= 0 || temp.getArea().indexOf(
                                    ipdata.getCity()) >= 0)) {
                                area = temp;
                                break;
                            } else {
                                break;
                            }
                        } else {
                            area = getParentArea(ipdata, temp, topicTypeList);
                        }
                    }
                }
            } else {
                area = topicType;
            }
        }
        return area;
    }

    public List<Topic> topic(String orgi, List<KnowledgeType> topicTypeList, List<Topic> topicList) {
        List<Topic> tempTopicList = new ArrayList<Topic>();
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
     * 根据热点知识找到 非空的 分类
     *
     * @param topicTypeList
     * @param topicList
     * @return
     */
    public List<KnowledgeType> filterTopicType(List<KnowledgeType> topicTypeList, List<Topic> topicList) {
        List<KnowledgeType> tempTopicTypeList = new ArrayList<KnowledgeType>();
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
     * 找到知识点对应的 分类
     *
     * @param cate
     * @param topicTypeList
     * @return
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

    /**
     * @param orgi
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<User> agents(String orgi) {
        String origOrig = orgi;

        List<User> agentList = userRes.findByOrgiAndAgentAndDatastatus(orgi, true, false);
        List<User> agentTempList = new ArrayList<User>();
        List<Organ> skillOrgansByOrgi = organRepository.findByOrgiAndSkill(origOrig, true);

        if (!skillOrgansByOrgi.isEmpty()) {
            for (User user : agentList) {
                // 跳过管理员角色用户，不显示在技能组列表
                if (user.isAdmin() || user.isSuperadmin()) continue;

                // 只显示在线的客服，跳过离线的客服
                if (cacheService.findOneAgentStatusByAgentnoAndOrig(user.getId(), origOrig) == null) continue;

                // 一个用户可隶属于多个组织
                userService.attachOrgansPropertiesForUser(user);
                for (Organ organ : skillOrgansByOrgi) {
                    if (user.getOrgans().size() > 0 && user.inAffiliates(organ.getId())) {
                        agentTempList.add(user);
                    }
                }
            }
        }
        agentList = agentTempList;

        return agentList;
    }

    public Contacts processContacts(
            final String orgi,
            Contacts contacts,
            final String appid,
            final String userid) {
        if (contacts != null) {
            if (contacts != null &&
                    (StringUtils.isNotBlank(contacts.getName()) ||
                            StringUtils.isNotBlank(contacts.getPhone()) ||
                            StringUtils.isNotBlank(contacts.getEmail()))) {
                StringBuffer query = new StringBuffer();
                query.append(contacts.getName());
                if (StringUtils.isNotBlank(contacts.getPhone())) {
                    query.append(" OR ").append(contacts.getPhone());
                }
                if (StringUtils.isNotBlank(contacts.getEmail())) {
                    query.append(" OR ").append(contacts.getEmail());
                }
                Page<Contacts> contactsList = contactsRes.findByOrgi(
                        orgi, false, query.toString(), new PageRequest(0, 1));
                if (contactsList.getContent().size() > 0) {
                    contacts = contactsList.getContent().get(0);
                } else {
//					contactsRes.save(contacts) ;	//需要增加签名验证，避免随便产生垃圾信息，也可以自行修改？
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
     * 创建OnlineUser并上线
     * 根据user判断追踪，在浏览器里，用fingerprint2生成的ID作为唯一标识
     *
     * @param user
     * @param orgi
     * @param sessionid
     * @param optype
     * @param request
     * @param channel
     * @param appid
     * @param contacts
     * @param invite
     * @return
     * @throws CharacterCodingException
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
//        logger.info(
//                "[online] user {}, orgi {}, sessionid {}, optype {}, channel {}", user.getId(), orgi, sessionid, optype,
//                channel);
        OnlineUser onlineUser = null;
        final Date now = new Date();
        if (invite != null) {
            // resolve user from cache or db.
            onlineUser = onlineuser(user.getId(), orgi);

            if (onlineUser == null) {
//                logger.info("[online] create new online user.");
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

                // 从Server session信息中查找该用户相关的历史信息
                String cookie = getCookie(request, "R3GUESTUSEKEY");
                if ((StringUtils.isBlank(cookie))
                        || (StringUtils.equals(user.getSessionid(), cookie))) {
                    onlineUser.setOlduser("0");
                } else {
                    // 之前有session的访客
                    onlineUser.setOlduser("1");
                }
                onlineUser.setMobile(BrowserClient.isMobile(request
                        .getHeader("User-Agent")) ? "1" : "0");

                // onlineUser.setSource(user.getId());

                String url = request.getHeader("referer");
                onlineUser.setUrl(url);
                if (StringUtils.isNotBlank(url)) {
                    try {
                        URL referer = new URL(url);
                        onlineUser.setSource(referer.getHost());
                    } catch (MalformedURLException e) {
                        log.info("[online] error when parsing URL", e);
                    }
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

                // 地理信息
                String ip = IPUtils.getIpAddress(request);
                onlineUser.setIp(ip);
                IP ipdata = IPTools.findGeography(ip);
                onlineUser.setCountry(ipdata.getCountry());
                onlineUser.setProvince(ipdata.getProvince());
                onlineUser.setCity(ipdata.getCity());
                onlineUser.setIsp(ipdata.getIsp());
                onlineUser.setRegion(ipdata.toString() + "（"
                        + ip + "）");

                onlineUser.setDatestr(new SimpleDateFormat("yyyMMdd")
                        .format(now));

                onlineUser.setHostname(ip);
                onlineUser.setSessionid(sessionid);
                onlineUser.setOptype(optype);
                onlineUser.setStatus(Enums.OnlineUserStatusEnum.ONLINE.toString());
                final BrowserClient client = BrowserClient.parseClient(request.getHeader(BrowserClient.USER_AGENT));

                // 浏览器信息
                onlineUser.setOpersystem(client.getOs());
                onlineUser.setBrowser(client.getBrowser());
                onlineUser.setUseragent(client.getUseragent());

                log.info("[online] new online user is created but not persisted.");
            } else {
                // 从DB或缓存找到OnlineUser
                onlineUser.setCreatetime(now); // 刷新创建时间
                if ((StringUtils.isNotBlank(onlineUser.getSessionid()) && !StringUtils.equals(
                        onlineUser.getSessionid(), sessionid)) ||
                        !StringUtils.equals(
                                Enums.OnlineUserStatusEnum.ONLINE.toString(), onlineUser.getStatus())) {
                    // 当新的session与从DB或缓存查找的session不一致时，或者当数据库或缓存的OnlineUser状态不是ONLINE时
                    // 代表该用户登录了新的Session或从离线变为上线！

                    onlineUser.setStatus(Enums.OnlineUserStatusEnum.ONLINE.toString()); // 设置用户到上线
                    onlineUser.setChannel(channel);          // 设置渠道
                    onlineUser.setAppid(appid);
                    onlineUser.setUpdatetime(now);           // 刷新更新时间
                    if (StringUtils.isNotBlank(onlineUser.getSessionid()) && !StringUtils.equals(
                            onlineUser.getSessionid(), sessionid)) {
                        onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.DEFAULT.toString());
                        onlineUser.setSessionid(sessionid);  // 设置新的session信息
                        onlineUser.setLogintime(now);        // 设置更新时间
                        onlineUser.setInvitetimes(0);        // 重置邀请次数
                    }
                }

                // 处理联系人关联信息
                if (contacts != null) {
                    // 当关联到联系人
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

            // 完成获取及更新OnlineUser, 将信息加入缓存
            if (onlineUser != null && StringUtils.isNotBlank(onlineUser.getUserid())) {
//                logger.info(
//                        "[online] onlineUser id {}, status {}, invite status {}", onlineUser.getId(),
//                        onlineUser.getStatus(), onlineUser.getInvitestatus());
                // 存储到缓存及数据库

                onlineUserRes.save(onlineUser);
            }
        }
        return onlineUser;
    }

    /**
     * @param request
     * @param key
     * @return
     */
    public String getCookie(HttpServletRequest request, String key) {
        Cookie data = null;
        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals(key)) {
                    data = cookie;
                    break;
                }
            }
        }
        return data != null ? data.getValue() : null;
    }

    /**
     * @param user
     * @param orgi
     * @throws Exception
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

    /**
     * 设置onlineUser为离线
     *
     * @param onlineUser
     * @throws Exception
     */
    public void offline(OnlineUser onlineUser) {
        if (onlineUser != null) {
            offline(onlineUser.getId(), onlineUser.getOrgi());
        }
    }

    /**
     * @param user
     * @throws Exception
     */
    public void refuseInvite(final String user) {

        onlineUserRes.findById(user)
                .ifPresent(onlineUser -> {
                    onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.REFUSE.toString());
                    onlineUser.setRefusetimes(onlineUser.getRefusetimes() + 1);

                    onlineUserRes.save(onlineUser);
                });
    }

    public String unescape(String src) {
        StringBuilder tmp = new StringBuilder();
        try {
            tmp.append(java.net.URLDecoder.decode(src, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    public String getKeyword(String url) {
        Map<String, String[]> values = new HashMap<>();
        try {
            ParameterKit.parseParameters(values, url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        StringBuilder strb = new StringBuilder();
        String[] data = values.get("q");
        if (data != null) {
            for (String v : data) {
                strb.append(v);
            }
        }
        return strb.toString();
    }

    public String getSource(String url) {
        String source = "0";
        try {
            URL addr = new URL(url);
            source = addr.getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return source;
    }

    /**
     * 发送邀请
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
                    // 一些连接断开在服务器端没有清除
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
     * 创建Skype联系人的onlineUser记录
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

        log.info(
                "[createNewOnlineUserWithContactAndChannel] onlineUser id {}, userId {}", onlineUser.getId(),
                onlineUser.getUserid());
        // TODO 此处没有创建 onlineUser 的 appid

        onlineUserRes.save(onlineUser);
        return onlineUser;

    }

    public void removeClient(String userid, String client, boolean timeout) throws Exception {
        webIMClients.removeClient(userid, client, timeout);
    }

    @PostConstruct
    public void postConstruct() {
        webIMClients = new WebSseEmitterClient(this);
    }
}
