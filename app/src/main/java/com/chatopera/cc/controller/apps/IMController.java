/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2020 Chatopera Inc, <https://www.chatopera.com>
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

package com.chatopera.cc.controller.apps;

import com.chatopera.cc.acd.ACDPolicyService;
import com.chatopera.cc.acd.ACDWorkMonitor;
import com.chatopera.cc.basic.*;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.github.xiaobo9.model.UploadStatus;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import com.chatopera.cc.persistence.blob.JpaBlobHelper;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.persistence.repository.ChatMessageRepository;
import com.chatopera.cc.service.OnlineUserService;
import com.chatopera.cc.service.SystemConfigService;
import com.chatopera.cc.service.UploadService;
import com.chatopera.cc.socketio.util.RichMediaUtils;
import com.chatopera.cc.util.*;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.Base62Utils;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import com.github.xiaobo9.commons.utils.BrowserClient;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import freemarker.template.TemplateException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * FIXME method tooooooo long
 */
@Controller
@RequestMapping("/im")
@EnableAsync
public class IMController extends Handler {
    private final static Logger logger = LoggerFactory.getLogger(IMController.class);

    @Autowired
    private SystemConfigService configService;

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    @Autowired
    private ACDPolicyService acdPolicyService;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Value("${uk.im.server.port}")
    private Integer port;

    @Value("${cs.im.server.ssl.port}")
    private Integer sslPort;

    @Autowired
    private UploadService uploadService;

    @Value("${cskefu.settings.webim.visitor-separate}")
    private Boolean channelWebIMVisitorSeparate;

    @Autowired
    private StreamingFileRepository streamingFileRepository;

    @Autowired
    private JpaBlobHelper jpaBlobHelper;

    @Autowired
    private ConsultInviteRepository inviteRepository;

    @Autowired
    private ChatMessageRepository chatMessageRes;

    @Autowired
    private AgentServiceSatisRepository agentServiceSatisRes;

    @Autowired
    private AgentServiceRepository agentServiceRepository;

    @Autowired
    private InviteRecordRepository inviteRecordRes;

    @Autowired
    private LeaveMsgRepository leaveMsgRes;

    @Autowired
    private AgentUserRepository agentUserRepository;

    @Autowired
    private AttachmentRepository attachementRes;

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private AgentUserContactsRepository agentUserContactsRes;

    @Autowired
    private SNSAccountRepository snsAccountRepository;

    @Autowired
    private SNSAccountRepository snsAccountRes;

    @Autowired
    private UserHistoryRepository userHistoryRes;

    @Autowired
    private ChatbotRepository chatbotRes;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private OnlineUserService onlineUserService;

    @PostConstruct
    private void init() {
    }

    /**
     * 在客户或第三方网页内，写入聊天控件
     *
     * @param request
     * @param id
     * @param userid
     * @param title
     * @param aiid
     * @return
     */
    @RequestMapping("/{id}.html")
    @Menu(type = "im", subtype = "point", access = true)
    public ModelAndView point(
            HttpServletRequest request,
            @PathVariable String id,
            @Valid String userid,
            @Valid String title,
            @Valid String aiid) {
        ModelAndView view = request(super.pageTplResponse("/apps/im/point"));
        view.addObject("channelVisitorSeparate", channelWebIMVisitorSeparate);

        final String sessionid = UUIDUtils.removeHyphen(request.getSession().getId());
        logger.info("[point] session snsid {}, session {}", id, sessionid);

        if (StringUtils.isNotBlank(id)) {
            boolean webimexist = false;
            view.addObject("hostname", request.getServerName());
            logger.info("[point] new website is : {}", request.getServerName());
            SNSAccount SnsAccountList = snsAccountRes.findBySnsidAndOrgi(id, super.getUser(request).getOrgi());
            if (SnsAccountList != null) {
                webimexist = true;
            }
            view.addObject("webimexist", webimexist);

            SystemConfig systemConfig = configService.getSystemConfig();
            if (systemConfig.isEnablessl()) {
                view.addObject("schema", "https");
                if (request.getServerPort() == 80) {
                    view.addObject("port", 443);
                } else {
                    view.addObject("port", request.getServerPort());
                }
            } else {
                view.addObject("schema", request.getScheme());
                String header = request.getHeader("X-Forwarded-Proto");
                if (header != null) {
                    view.addObject("schema", header);
                }
                view.addObject("port", request.getServerPort());
            }
            BrowserClient client = BrowserClient.parseClient(request.getHeader(BrowserClient.USER_AGENT));

            view.addObject("appid", id);
            view.addObject("client", UUIDUtils.getUUID());
            view.addObject("sessionid", sessionid);
            view.addObject("ip", MD5Utils.md5(request.getRemoteAddr()));
            view.addObject("mobile", client.isMobile());

            CousultInvite invite = onlineUserService.consult(id, Constants.SYSTEM_ORGI);
            if (invite != null) {
                logger.info("[point] find CousultInvite {}", invite.getId());
                view.addObject("inviteData", invite);
                view.addObject("orgi", invite.getOrgi());
                view.addObject("appid", id);

                if (StringUtils.isNotBlank(aiid)) {
                    view.addObject("aiid", aiid);
                } else if (StringUtils.isNotBlank(invite.getAiid())) {
                    view.addObject("aiid", invite.getAiid());
                }

                // 记录用户行为日志
                // 每次有一个新网页加载出聊天控件，都会生成一个userHistory
                UserHistory userHistory = new UserHistory();
                String url = request.getHeader("referer");
                if (StringUtils.isNotBlank(url)) {
                    if (url.length() > 255) {
                        userHistory.setUrl(url.substring(0, 255));
                    } else {
                        userHistory.setUrl(url);
                    }
                    userHistory.setReferer(userHistory.getUrl());
                }
                userHistory.setParam(MainUtils.getParameter(request));
                userHistory.setMaintype("send");
                userHistory.setSubtype("point");
                userHistory.setName("online");
                userHistory.setAdmin(false);
                userHistory.setAccessnum(true);
                userHistory.setModel(Enums.ChannelType.WEBIM.toString());

                final User imUser = super.getIMUser(request, userid, null);
                if (imUser != null) {
                    userHistory.setCreater(imUser.getId());
                    userHistory.setUsername(imUser.getUsername());
                    userHistory.setOrgi(Constants.SYSTEM_ORGI);
                }

                if (StringUtils.isNotBlank(title)) {
                    if (title.length() > 255) {
                        userHistory.setTitle(title.substring(0, 255));
                    } else {
                        userHistory.setTitle(title);
                    }
                }

                userHistory.setOrgi(invite.getOrgi());
                userHistory.setAppid(id);
                userHistory.setSessionid(sessionid);

                String ip = IPUtils.getIpAddress(request);
                userHistory.setHostname(ip);
                userHistory.setIp(ip);
                IP ipdata = IPTools.findGeography(ip);
                userHistory.setCountry(ipdata.getCountry());
                userHistory.setProvince(ipdata.getProvince());
                userHistory.setCity(ipdata.getCity());
                userHistory.setIsp(ipdata.getIsp());

                userHistory.setOstype(client.getOs());
                userHistory.setBrowser(client.getBrowser());
                userHistory.setMobile(client.isMobile() ? "1" : "0");

                if (invite.isSkill() && !invite.isConsult_skill_fixed()) { // 展示所有技能组
                    // 查询 技能组 ， 缓存？
                    view.addObject("skillGroups", onlineUserService.organ(Constants.SYSTEM_ORGI, ipdata, invite, true));
                    // 查询坐席 ， 缓存？
                    view.addObject("agentList", onlineUserService.agents(Constants.SYSTEM_ORGI));
                }

                view.addObject("traceid", userHistory.getId());

                // 用户的浏览历史会有很大的数据量，目前强制开启
                userHistoryRes.save(userHistory);

                view.addObject(
                        "pointAd",
                        MainUtils.getPointAdv(Enums.AdPosEnum.POINT.toString(), invite.getConsult_skill_fixed_id(), Constants.SYSTEM_ORGI));
                view.addObject(
                        "inviteAd",
                        MainUtils.getPointAdv(Enums.AdPosEnum.INVITE.toString(), invite.getConsult_skill_fixed_id(), Constants.SYSTEM_ORGI));
            } else {
                logger.info("[point] invite id {}, orgi {} not found", id, Constants.SYSTEM_ORGI);
            }
        }

        return view;
    }

    private void createContacts(
            final String gid,
            final String uid,
            final String cid,
            final String sid,
            final String username,
            final String company_name,
            final String system_name) {
        if (StringUtils.isNotBlank(uid) && StringUtils.isNotBlank(sid) && StringUtils.isNotBlank(cid)) {
            Contacts data = contactsRes.findOneByWluidAndWlsidAndWlcidAndDatastatus(uid, sid, cid, false);
            if (data == null) {
                data = new Contacts();
                data.setCreater(gid);
                data.setOrgi(Constants.SYSTEM_ORGI);
                data.setWluid(uid);
                data.setWlusername(username);
                data.setWlcid(cid);
                data.setWlcompany_name(company_name);
                data.setWlsid(sid);
                data.setWlsystem_name(system_name);
                data.setName(username + '@' + company_name);
                data.setShares("all");

                data.setPinyin(PinYinTools.getInstance().getFirstPinYin(username));
                contactsRes.save(data);
            }
        }
    }

    @ResponseBody
    @RequestMapping("/chatoperainit")
    @Menu(type = "im", subtype = "chatoperainit")
    public String chatoperaInit(
            HttpServletRequest request,
            String userid,
            String uid,
            String username,
            String cid,
            String company_name,
            String sid,
            String system_name,
            Boolean whitelist_mode,
            @RequestParam String sessionid) throws IOException, TemplateException {
        final User logined = super.getUser(request);

        request.getSession().setAttribute("Sessionuid", uid);

        Map<String, String> sessionMessage = new HashMap<>();
        sessionMessage.put("username", username);
        sessionMessage.put("cid", cid);
        sessionMessage.put("company_name", company_name);
        sessionMessage.put("sid", sid);
        sessionMessage.put("Sessionsystem_name", system_name);
        sessionMessage.put("sessionid", sessionid);
        sessionMessage.put("uid", uid);
        cacheService.putSystemMapByIdAndOrgi(sessionid, Constants.SYSTEM_ORGI, sessionMessage);

        OnlineUser onlineUser = onlineUserRes.findById(userid).orElse(null);
        if (onlineUser != null) {
            String updateusername = username + "@" + company_name;
            onlineUser.setUsername(updateusername);
            onlineUser.setUpdateuser(updateusername);
            onlineUser.setUpdatetime(new Date());
            onlineUserRes.save(onlineUser);
        }

        Contacts usc = contactsRes.findOneByWluidAndWlsidAndWlcidAndDatastatus(uid, sid, cid, false);
        if (usc != null) {
            return "usc";
        } else {
            if (!whitelist_mode) {
                createContacts(logined.getId(), uid, cid, sid, username, company_name, system_name);
            }
        }

        return "ok";
    }


    @RequestMapping("/{id}/userlist")
    @Menu(type = "im", subtype = "inlist", access = true)
    public void inlist(HttpServletResponse response, @PathVariable String id, @Valid String userid) throws IOException {
        response.setHeader("Content-Type", "text/html;charset=utf-8");
        if (StringUtils.isNotBlank(userid)) {
            BlackEntity black = cacheService.findOneSystemByIdAndOrgi(userid, Constants.SYSTEM_ORGI);
            if ((black != null && (black.getEndtime() == null || black.getEndtime().after(new Date())))) {
                response.getWriter().write("in");
            }
        }
    }

    /**
     * 延时获取用户端浏览器的跟踪ID
     *
     * @param request
     * @param orgi
     * @param appid
     * @param userid
     * @param sign
     * @return
     */
    @RequestMapping("/online")
    @Menu(type = "im", subtype = "online", access = true)
    public SseEmitter callable(
            HttpServletRequest request,
            final @Valid String orgi,
            final @Valid String sessionid,
            @Valid String appid,
            final @Valid String userid,
            @Valid String sign,
            final @Valid String client,
            final @Valid String traceid) throws InterruptedException {
        Optional<BlackEntity> blackOpt = cacheService.findOneBlackEntityByUserIdAndOrgi(userid, orgi);
        if (blackOpt.isPresent() && (blackOpt.get().getEndtime() == null || blackOpt.get().getEndtime().after(new Date()))) {
            logger.info("[online] online user {} is in black list.", userid);
            // 该访客被拉黑
            return null;
        }

        final SseEmitter emitter = new SseEmitter(30000L);
        if (StringUtils.isNotBlank(userid)) {
            emitter.onCompletion(() -> {
                try {
                    onlineUserService.removeClient(userid, client, false); // 执行了 邀请/再次邀请后终端的
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            emitter.onTimeout(() -> {
                try {
                    emitter.complete();
                    onlineUserService.removeClient(userid, client, true); // 正常的超时断开
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            CousultInvite invite = onlineUserService.consult(appid, orgi);

            // TODO 该contacts的识别并不准确，因为不能关联
//                if (invite != null && invite.isTraceuser()) {
//                    contacts = OnlineUserProxy.OnlineUserProxy.processContacts(orgi, contacts, appid, userid);
//                }
//
//                if (StringUtils.isNotBlank(sign)) {
//                    OnlineUserProxy.online(
//                            super.getIMUser(request, sign, contacts != null ? contacts.getName() : null, sessionid),
//                            orgi,
//                            sessionid,
//                            Enums.OnlineUserType.WEBIM.toString(),
//                            request,
//                            Enums.ChannelType.WEBIM.toString(),
//                            appid,
//                            contacts,
//                            invite);
            // END 取消关联contacts

            if (StringUtils.isNotBlank(sign)) {
                onlineUserService.online(
                        super.getIMUser(request, sign, null, sessionid),
                        orgi,
                        sessionid,
                        Enums.OnlineUserType.WEBIM.toString(),
                        request,
                        Enums.ChannelType.WEBIM.toString(),
                        appid,
                        null,
                        invite);
            }
            onlineUserService.webIMClients.putClient(userid, new WebIMClient(userid, client, emitter, traceid));
            Thread.sleep(500);
        }

        return emitter;
    }

    /**
     * 访客与客服聊天小窗口
     * <p>
     * 此处返回给访客新的页面：根据访客/坐席/机器人的情况进行判断
     * 如果此处返回的是人工服务，那么此处并不寻找服务的坐席信息，而是在返回的页面中查找
     *
     * @param map
     * @param request
     * @param response
     * @param orgi
     * @param aiid
     * @param traceid
     * @param exchange
     * @param title
     * @param url
     * @param mobile
     * @param ai
     * @param client
     * @param type
     * @param appid
     * @param userid
     * @param sessionid
     * @param skill
     * @param agent
     * @param contacts
     * @param product
     * @param description
     * @param imgurl
     * @param pid
     * @param purl
     * @return
     * @throws Exception
     */
    @RequestMapping("/index.html")
    @Menu(type = "im", subtype = "index", access = true)
    public ModelAndView index(
            ModelMap map,
            HttpServletRequest request,
            HttpServletResponse response,
            @Valid Contacts contacts,
            @Valid final String orgi,
            @Valid final String aiid,
            @Valid final String traceid,
            @Valid final String exchange,
            @Valid final String title,
            @Valid final String url,
            @Valid final String mobile,
            @Valid final String ai,
            @Valid final String client,
            @Valid final String type,
            @Valid final String appid,
            @Valid final String userid,
            @Valid final String sessionid,
            @Valid final String skill,
            @Valid final String agent,
            @Valid final String product,
            @Valid final String description,
            @Valid final String imgurl,
            @Valid final String pid,
            @Valid final String purl,
            @Valid final boolean isInvite) throws Exception {
        logger.info("[index] orgi {}, skill {}, agent {}, traceid {}, isInvite {}, exchange {}", orgi, skill, agent, traceid, isInvite, exchange);
        Map<String, String> sessionMsg = cacheService.findOneSystemMapByIdAndOrgi(sessionid, orgi);

        HttpSession session = request.getSession();
        if (sessionMsg != null) {
            session.setAttribute("Sessionusername", sessionMsg.get("username"));
            session.setAttribute("Sessioncid", sessionMsg.get("cid"));
            session.setAttribute("Sessioncompany_name", sessionMsg.get("company_name"));
            session.setAttribute("Sessionsid", sessionMsg.get("sid"));
            session.setAttribute("Sessionsystem_name", sessionMsg.get("system_name"));
            session.setAttribute("sessionid", sessionMsg.get("sessionid"));
            session.setAttribute("Sessionuid", sessionMsg.get("uid"));
        }

        ModelAndView view = request(super.pageTplResponse("/apps/im/index"));
        BlackEntity blackEntity = cacheService.findOneBlackEntityByUserIdAndOrgi(userid, Constants.SYSTEM_ORGI).orElse(null);
        CousultInvite invite = onlineUserService.consult(appid, orgi);
        // appid 或者 用户在黑名单里直接返回
        if (StringUtils.isBlank(appid) || (blackEntity != null && blackEntity.inBlackStatus())) {
            view.addObject("inviteData", invite);
            logger.info("[index] return view");
            return view;
        }

        // 随机生成OnlineUser的用户名，使用了浏览器指纹做唯一性KEY
        String randomUserId = Base62Utils.genIDByKey(StringUtils.isNotBlank(userid) ? userid : sessionid);
        String nickname;

        if (sessionMsg != null) {
            nickname = sessionMsg.get("username") + "@" + sessionMsg.get("company_name");
        } else if (session.getAttribute("Sessionusername") != null) {
            String struname = (String) session.getAttribute("Sessionusername");
            String strcname = (String) session.getAttribute("Sessioncompany_name");
            nickname = struname + "@" + strcname;
        } else {
            nickname = "Guest_" + "@" + randomUserId;
        }

        view.addObject("nickname", nickname);

        boolean consult = true;                //是否已收集用户信息
        SessionConfig sessionConfig = acdPolicyService.initSessionConfig(skill, orgi);

        // 强制开启满意调查问卷
        sessionConfig.setSatisfaction(true);

        map.addAttribute("sessionConfig", sessionConfig);
        map.addAttribute("hostname", request.getServerName());

        if (sslPort != null) {
            map.addAttribute("port", sslPort);
        } else {
            map.addAttribute("port", port);
        }

        map.addAttribute("appid", appid);
        map.addAttribute("userid", userid);
        map.addAttribute("schema", request.getScheme());
        map.addAttribute("sessionid", sessionid);
        map.addAttribute("isInvite", isInvite);


        view.addObject("product", product);
        view.addObject("description", description);
        view.addObject("imgurl", imgurl);
        view.addObject("pid", pid);
        view.addObject("purl", purl);

        map.addAttribute("ip", MD5Utils.md5(request.getRemoteAddr()));

        addAttribute(map, "traceid", traceid);
        addAttribute(map, "exchange", exchange);
        addAttribute(map, "title", title);
        addAttribute(map, "url", traceid);

        map.addAttribute("cskefuport", request.getServerPort());

        // 先检查 invite
        if (invite == null) {
            logger.info("[index] can not invite for appid {}, orgi {}", appid, orgi);
            return view;
        }
        logger.info("[index] invite id {}, orgi {}", invite.getId(), invite.getOrgi());
        map.addAttribute("orgi", invite.getOrgi());
        map.addAttribute("inviteData", invite);

        addAttribute(map, "aiid", StringUtils.isNotBlank(aiid) ? aiid : invite.getAiid());

        AgentReport report;
        if (invite.isSkill() && invite.isConsult_skill_fixed()) { // 绑定技能组
            report = acdWorkMonitor.getAgentReport(invite.getConsult_skill_fixed_id(), invite.getOrgi());
        } else {
            report = acdWorkMonitor.getAgentReport(invite.getOrgi());
        }

        boolean isLeavemsg = false;
        if (report.getAgents() == 0 ||
                (sessionConfig.isHourcheck() &&
                        !MainUtils.isInWorkingHours(sessionConfig.getWorkinghours()) &&
                        invite.isLeavemessage())) {
            // 没有坐席在线，进入留言
            isLeavemsg = true;
            boolean isInWorkingHours = MainUtils.isInWorkingHours(sessionConfig.getWorkinghours());
            map.addAttribute("isInWorkingHours", isInWorkingHours);
            view = request(super.pageTplResponse("/apps/im/leavemsg"));
        } else if (invite.isConsult_info()) {    //启用了信息收集，从Request获取， 或从 Cookies 里去
            // 验证 OnlineUser 信息
            if (contacts != null && StringUtils.isNotBlank(contacts.getName())) {    //contacts用于传递信息，并不和 联系人表发生 关联，contacts信息传递给 Socket.IO，然后赋值给 AgentUser，最终赋值给 AgentService永久存储
                consult = true;
                //存入 Cookies
                if (invite.isConsult_info_cookies()) {
                    addCookie(response, "name", contacts.getName());
                    addCookie(response, "phone", contacts.getPhone());
                    addCookie(response, "email", contacts.getEmail());
                    addCookie(response, "skypeid", contacts.getSkypeid());
                    addCookie(response, "memo", contacts.getMemo());
                }
            } else {
                //从 Cookies里尝试读取
                if (invite.isConsult_info_cookies()) {
                    Cookie[] cookies = request.getCookies();//这样便可以获取一个cookie数组
                    contacts = createContacts(cookies);
                }
                if (StringUtils.isBlank(contacts.getName())) {
                    consult = false;
                    view = request(super.pageTplResponse("/apps/im/collecting"));
                }
            }
        } else {
            // TODO 该contacts的识别并不准确，因为不能关联
//                    contacts = OnlineUserProxy.processContacts(invite.getOrgi(), contacts, appid, userid);
            String uid = (String) session.getAttribute("Sessionuid");
            String sid = (String) session.getAttribute("Sessionsid");
            String cid = (String) session.getAttribute("Sessioncid");

            if (StringUtils.isNotBlank(uid) && StringUtils.isNotBlank(sid) && StringUtils.isNotBlank(cid)) {
                Contacts contacts1 = contactsRes.findOneByWluidAndWlsidAndWlcidAndDatastatus(
                        uid, sid, cid, false);
                if (contacts1 != null) {
                    agentUserRepository.findOneByUseridAndOrgi(userid, orgi).ifPresent(p -> {
                        // 关联AgentService的联系人
                        if (StringUtils.isNotBlank(p.getAgentserviceid())) {
                            AgentService agentService = agentServiceRepository.findById(p.getAgentserviceid()).orElseThrow(EntityNotFoundEx::new);
                            agentService.setContactsid(contacts1.getId());
                        }

                        saveAgentUserContacts(request, orgi, appid, userid, session, contacts1, p);
                    });
                }
            }
        }

        addAttribute(map, "client", client);
        addAttribute(map, "skill", skill);
        addAttribute(map, "agent", agent);
        addAttribute(map, "type", type);

        map.addAttribute("contacts", contacts);

        IP ipdata = IPTools.findGeography(IPUtils.getIpAddress(request));
        map.addAttribute("skillGroups", onlineUserService.organ(invite.getOrgi(), ipdata, invite, true));

        if (consult) {
            if (contacts != null && StringUtils.isNotBlank(contacts.getName())) {
                nickname = contacts.getName();
            }

            map.addAttribute("username", nickname);
            boolean isChatbotAgentFirst = false;
            boolean isEnableExchangeAgentType = false;

            // 是否使用机器人客服
            if (invite.isAi() && MainContext.hasModule(Constants.CSKEFU_MODULE_CHATBOT)) {
                // 查找机器人
                Chatbot bot = chatbotRes.findById(invite.getAiid()).orElse(null);
                if (bot != null) {
                    // 判断是否接受访客切换坐席类型
                    isEnableExchangeAgentType = !StringUtils.equals(bot.getWorkmode(), Constants.CHATBOT_CHATBOT_ONLY);

                    // 判断是否机器人客服优先
                    if (StringUtils.equals(ai, "true") || (invite.isAifirst() && ai == null)) {
                        isChatbotAgentFirst = true;
                    }
                }
            }

            map.addAttribute("exchange", isEnableExchangeAgentType);

            if (isChatbotAgentFirst) {
                // 机器人坐席
                HashMap<String, String> chatbotConfig = new HashMap<>();
                chatbotConfig.put("botname", invite.getAiname());
                chatbotConfig.put("botid", invite.getAiid());
                chatbotConfig.put("botwelcome", invite.getAimsg());
                chatbotConfig.put("botfirst", Boolean.toString(invite.isAifirst()));
                chatbotConfig.put("isai", Boolean.toString(invite.isAi()));

                map.addAttribute("chatbotConfig", chatbotConfig);
                view = request(super.pageTplResponse("/apps/im/chatbot/index"));
                if (BrowserClient.isMobile(request.getHeader("User-Agent")) || StringUtils.isNotBlank(mobile)) {
                    view = request(super.pageTplResponse("/apps/im/chatbot/mobile"));        // 智能机器人 移动端
                }
            } else {
                // 维持人工坐席的设定，检查是否进入留言
                if (!isLeavemsg && (BrowserClient.isMobile(request.getHeader("User-Agent")) || StringUtils.isNotBlank(mobile))) {
                    view = request(super.pageTplResponse("/apps/im/mobile"));    // WebIM移动端。再次点选技能组？
                }
            }

            PageRequest pageRequest = super.page(request, Direction.DESC, "updatetime");
            map.addAttribute("chatMessageList", chatMessageRes.findByUsessionAndOrgi(userid, orgi, pageRequest));
        }
        view.addObject("commentList", Dict.getInstance().getDic(Constants.CSKEFU_SYSTEM_COMMENT_DIC));
        view.addObject("commentItemList", Dict.getInstance().getDic(Constants.CSKEFU_SYSTEM_COMMENT_ITEM_DIC));
        view.addObject("welcomeAd", MainUtils.getPointAdv(Enums.AdPosEnum.WELCOME.toString(), skill, orgi));
        view.addObject("imageAd", MainUtils.getPointAdv(Enums.AdPosEnum.IMAGE.toString(), skill, orgi));

        // 确定"接受邀请"被处理后，通知浏览器关闭弹出窗口
        onlineUserService.sendWebIMClients(userid, "accept");

        // 更新InviteRecord
        updateInviteRecord(orgi, traceid, title, url, userid);
        logger.info("[index] return view");
        return view;
    }

    private void addAttribute(ModelMap map, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            map.addAttribute(key, value);
        }
    }

    private void addCookie(HttpServletResponse response, String key, String value) throws UnsupportedEncodingException {
        if (StringUtils.isNotBlank(value)) {
            Cookie cookie = new Cookie(key, MainUtils.encryption(URLEncoder.encode(value, "UTF-8")));
            cookie.setMaxAge(3600);
            response.addCookie(cookie);
        }
    }

    private Contacts createContacts(Cookie[] cookies) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        if (cookies == null) {
            return new Contacts();
        }
        Contacts contacts = new Contacts();
        Map<String, String> map = new HashMap<>();

        for (Cookie cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            if (StringUtils.isNotBlank(cookie.getName()) && StringUtils.isNotBlank(cookie.getValue())) {
                map.put(cookie.getName(), cookie.getValue());
            }
        }
        contacts.setName(decode(map.get("name")));
        contacts.setPhone(decode(map.get("phone")));
        contacts.setEmail(decode(map.get("email")));
        contacts.setMemo(decode(map.get("memo")));
        contacts.setSkypeid(decode(map.get("skypeid")));
        return contacts;
    }

    private String decode(String value) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return URLDecoder.decode(MainUtils.decryption(value), "UTF-8");
    }

    private void saveAgentUserContacts(HttpServletRequest request, String orgi, String appid, String userid, HttpSession session, Contacts contacts1, AgentUser p) {
        // 关联AgentUserContact的联系人
        // NOTE: 如果该userid已经有了关联的Contact则忽略，继续使用之前的
        agentUserContactsRes.findOneByUseridAndOrgi(userid, orgi)
                .ifPresent(a -> {
                    AgentUserContacts agentUserContacts = new AgentUserContacts();
                    agentUserContacts.setOrgi(orgi);
                    agentUserContacts.setAppid(appid);
                    agentUserContacts.setChannel(p.getChannel());
                    agentUserContacts.setContactsid(contacts1.getId());
                    agentUserContacts.setUserid(userid);
                    agentUserContacts.setUsername((String) session.getAttribute("Sessionusername"));
                    agentUserContacts.setCreater(super.getUser(request).getId());
                    agentUserContacts.setCreatetime(new Date());
                    agentUserContactsRes.save(agentUserContacts);
                });
    }

    private void updateInviteRecord(String orgi, String traceid, String title, String url, String userid) {
        logger.info("[index] update inviteRecord for user {}", userid);
        final Date threshold = new Date(System.currentTimeMillis() - Constants.WEBIM_AGENT_INVITE_TIMEOUT);
        PageRequest page = PageRequest.of(0, 1, Direction.DESC, "createtime");
        Page<InviteRecord> records = inviteRecordRes.findByUseridAndOrgiAndResultAndCreatetimeGreaterThan(
                userid, orgi, Enums.OnlineUserInviteStatus.DEFAULT.toString(), threshold, page);
        if (records.getContent().size() > 0) {
            final InviteRecord record = records.getContent().get(0);
            record.setUpdatetime(new Date());
            record.setTraceid(traceid);
            record.setTitle(title);
            record.setUrl(url);
            record.setResponsetime((int) (System.currentTimeMillis() - record.getCreatetime().getTime()));
            record.setResult(Enums.OnlineUserInviteStatus.ACCEPT.toString());
            logger.info("[index] re-save inviteRecord id {}", record.getId());
            inviteRecordRes.save(record);
        }
    }

    @RequestMapping("/text/{appid}.html")
    @Menu(type = "im", subtype = "index", access = true)
    public ModelAndView text(
            HttpServletRequest request,
            @PathVariable String appid,
            @Valid String traceid,
            @Valid String aiid,
            @Valid String exchange,
            @Valid String title,
            @Valid String url,
            @Valid String skill,
            @Valid String id,
            @Valid String userid,
            @Valid String agent,
            @Valid String name,
            @Valid String email,
            @Valid String phone,
            @Valid String ai,
            @Valid String orgi,
            @Valid String product,
            @Valid String description,
            @Valid String imgurl,
            @Valid String pid,
            @Valid String purl) throws Exception {
        ModelAndView view = request(super.pageTplResponse("/apps/im/text"));
        CousultInvite invite = onlineUserService.consult(
                appid, StringUtils.isBlank(orgi) ? Constants.SYSTEM_ORGI : orgi);

        view.addObject("hostname", request.getServerName());
        view.addObject("port", request.getServerPort());
        view.addObject("schema", request.getScheme());
        view.addObject("appid", appid);
        view.addObject("channelVisitorSeparate", channelWebIMVisitorSeparate);
        view.addObject("ip", MD5Utils.md5(request.getRemoteAddr()));

        if (invite.isSkill() && invite.isConsult_skill_fixed()) { // 添加技能组ID
            // 忽略前端传入的技能组ID
            view.addObject("skill", invite.getConsult_skill_fixed_id());
        } else if (StringUtils.isNotBlank(skill)) {
            view.addObject("skill", skill);
        }

        if (StringUtils.isNotBlank(agent)) {
            view.addObject("agent", agent);
        }

        view.addObject("client", UUIDUtils.getUUID());
        view.addObject("sessionid", request.getSession().getId());

        view.addObject("id", id);
        if (StringUtils.isNotBlank(ai)) {
            view.addObject("ai", ai);
        }
        if (StringUtils.isNotBlank(exchange)) {
            view.addObject("exchange", exchange);
        }

        view.addObject("name", name);
        view.addObject("email", email);
        view.addObject("phone", phone);
        view.addObject("userid", userid);

        view.addObject("product", product);
        view.addObject("description", description);
        view.addObject("imgurl", imgurl);
        view.addObject("pid", pid);
        view.addObject("purl", purl);

        if (StringUtils.isNotBlank(traceid)) {
            view.addObject("traceid", traceid);
        }
        if (StringUtils.isNotBlank(title)) {
            view.addObject("title", title);
        }
        if (StringUtils.isNotBlank(traceid)) {
            view.addObject("url", url);
        }

        view.addObject("inviteData", invite);
        view.addObject("orgi", invite.getOrgi());
        view.addObject("appid", appid);

        if (StringUtils.isNotBlank(aiid)) {
            view.addObject("aiid", aiid);
        } else if (StringUtils.isNotBlank(invite.getAiid())) {
            view.addObject("aiid", invite.getAiid());
        }

        return view;
    }


    @RequestMapping("/leavemsg/save.html")
    @Menu(type = "admin", subtype = "user")
    public ModelAndView leavemsgsave(@Valid String appid,
                                     @Valid LeaveMsg msg,
                                     @Valid String skillId) {
        if (StringUtils.isNotBlank(appid)) {
            snsAccountRepository.findBySnsid(appid).ifPresent(p -> {
                CousultInvite invite = inviteRepository.findBySnsaccountidAndOrgi(appid, Constants.SYSTEM_ORGI);
                // TODO 增加策略防止恶意刷消息
                //  List<LeaveMsg> msgList = leaveMsgRes.findByOrgiAndUserid(invite.getOrgi(), msg.getUserid());
                // if(msg!=null && msgList.size() == 0){
                if (msg != null) {
                    msg.setOrgi(invite.getOrgi());
                    msg.setSkill(skillId);
                    msg.setChannel(p);
                    msg.setSnsId(appid);
                    leaveMsgRes.save(msg);
                }
            });
        }
        return request(super.pageTplResponse("/apps/im/leavemsgsave"));
    }

    @RequestMapping("/refuse")
    @Menu(type = "im", subtype = "refuse", access = true)
    public void refuse(@Valid String orgi, @Valid String userid) throws Exception {
        onlineUserService.refuseInvite(userid);
        final Date threshold = new Date(System.currentTimeMillis() - Constants.WEBIM_AGENT_INVITE_TIMEOUT);
        Page<InviteRecord> inviteRecords = inviteRecordRes.findByUseridAndOrgiAndResultAndCreatetimeGreaterThan(
                userid,
                orgi,
                Enums.OnlineUserInviteStatus.DEFAULT.toString(),
                threshold,
                new PageRequest(
                        0,
                        1,
                        Direction.DESC,
                        "createtime"));
        if (inviteRecords.getContent() != null && inviteRecords.getContent().size() > 0) {
            InviteRecord record = inviteRecords.getContent().get(0);
            record.setUpdatetime(new Date());
            record.setResponsetime((int) (System.currentTimeMillis() - record.getCreatetime().getTime()));
            record.setResult(Enums.OnlineUserInviteStatus.REFUSE.toString());
            inviteRecordRes.save(record);
        }
    }

    @RequestMapping("/satis")
    @Menu(type = "im", subtype = "satis", access = true)
    public void satis(@Valid AgentServiceSatis satis) throws Exception {
        if (satis != null && StringUtils.isNotBlank(satis.getId())) {
            int count = agentServiceSatisRes.countById(satis.getId());
            if (count == 1) {
                if (StringUtils.isNotBlank(satis.getSatiscomment()) && satis.getSatiscomment().length() > 255) {
                    satis.setSatiscomment(satis.getSatiscomment().substring(0, 255));
                }
                satis.setSatisfaction(true);
                satis.setSatistime(new Date());
                agentServiceSatisRes.save(satis);
            }
        }
    }

    @RequestMapping("/image/upload.html")
    @Menu(type = "im", subtype = "image", access = true)
    public ModelAndView upload(
            ModelMap map, HttpServletRequest request,
            @RequestParam(value = "imgFile", required = false) MultipartFile multipart,
            @Valid String channel,
            @Valid String userid,
            @Valid String username,
            @Valid String appid,
            @Valid String orgi,
            @Valid String paste) throws IOException {
        ModelAndView view = request(super.pageTplResponse("/apps/im/upload"));
        final User logined = super.getUser(request);

        if (multipart == null || multipart.getOriginalFilename().lastIndexOf(".") <= 0 || StringUtils.isBlank(userid)) {
            UploadStatus upload = new UploadStatus("请选择文件");
            map.addAttribute("upload", upload);
            return view;
        }

        UploadStatus upload;
        String fileid = UUIDUtils.getUUID();
        StreamingFile sf = new StreamingFile();
        sf.setId(fileid);
        sf.setName(multipart.getOriginalFilename());
        sf.setMime(multipart.getContentType());
        if (multipart.getContentType() != null && multipart.getContentType().contains(Constants.ATTACHMENT_TYPE_IMAGE)) {
            String invalid = StreamingFileUtil.validate(Constants.ATTACHMENT_TYPE_IMAGE, multipart.getOriginalFilename());
            if (invalid == null) {
                File imageFile = new File(uploadService.getUploadPath(), fileid + "_original");
                FileCopyUtils.copy(multipart.getBytes(), imageFile);
                File thumbnail = new File(uploadService.getUploadPath(), fileid);
                ThumbnailUtils.processImage(thumbnail, imageFile);

                //  存储数据库
                sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
                sf.setThumbnail(jpaBlobHelper.createBlobWithFile(thumbnail));
                streamingFileRepository.save(sf);
                String fileUrl = "/res/image.html?id=" + fileid;
                upload = new UploadStatus("0", fileUrl);

                if (paste == null) {
                    if (StringUtils.isNotBlank(channel)) {
                        RichMediaUtils.uploadImageWithChannel(
                                fileUrl, fileid, (int) multipart.getSize(), multipart.getName(), channel, userid,
                                username, appid, orgi);
                    } else {
                        RichMediaUtils.uploadImage(fileUrl, fileid, (int) multipart.getSize(), multipart.getName(), userid);
                    }
                }
            } else {
                upload = new UploadStatus(invalid);
            }
        } else {
            String invalid = StreamingFileUtil.validate(Constants.ATTACHMENT_TYPE_FILE, multipart.getOriginalFilename());
            if (invalid == null) {
                // 存储数据库
                sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
                streamingFileRepository.save(sf);

                // 存储到本地硬盘
                String id = processAttachmentFile(multipart, fileid, logined.getOrgi(), logined.getId());
                upload = new UploadStatus("0", "/res/file.html?id=" + id);
                String file = "/res/file.html?id=" + id;

                File tempFile = new File(multipart.getOriginalFilename());
                if (StringUtils.isNotBlank(channel)) {
                    RichMediaUtils.uploadFileWithChannel(
                            file, (int) multipart.getSize(), tempFile.getName(), channel, userid, username, appid,
                            orgi, id);
                } else {
                    RichMediaUtils.uploadFile(file, (int) multipart.getSize(), tempFile.getName(), userid, id);
                }
            } else {
                upload = new UploadStatus(invalid);
            }
        }
        map.addAttribute("upload", upload);
        return view;
    }


    private String processAttachmentFile(
            final MultipartFile file,
            final String fileid,
            final String orgi,
            final String creator) throws IOException {
        String id = null;

        if (file.getSize() > 0) {            //文件尺寸 限制 ？在 启动 配置中 设置 的最大值，其他地方不做限制
            AttachmentFile attachmentFile = new AttachmentFile();
            attachmentFile.setCreater(creator);
            attachmentFile.setOrgi(orgi);
            attachmentFile.setModel(Enums.ModelType.WEBIM.toString());
            attachmentFile.setFilelength((int) file.getSize());
            if (file.getContentType() != null && file.getContentType().length() > 255) {
                attachmentFile.setFiletype(file.getContentType().substring(0, 255));
            } else {
                attachmentFile.setFiletype(file.getContentType());
            }
            String originalFilename = URLDecoder.decode(file.getOriginalFilename(), "utf-8");
            File uploadFile = new File(originalFilename);
            String fileName = uploadFile.getName();
            attachmentFile.setTitle(fileName.length() > 255 ? fileName.substring(0, 255) : fileName);
            if (StringUtils.isNotBlank(attachmentFile.getFiletype()) && attachmentFile.getFiletype().contains("image")) {
                attachmentFile.setImage(true);
            }
            attachmentFile.setFileid(fileid);
            attachementRes.save(attachmentFile);
            FileUtils.writeByteArrayToFile(new File(uploadService.getUploadPath(), fileid), file.getBytes());
            id = attachmentFile.getId();
        }
        return id;
    }
}