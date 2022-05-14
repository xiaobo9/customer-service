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
import com.chatopera.cc.controller.vo.IMUploadFileBO;
import com.chatopera.cc.controller.vo.IMVO;
import com.chatopera.cc.persistence.blob.JpaBlobHelper;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.persistence.repository.ChatMessageRepository;
import com.chatopera.cc.service.OnlineUserService;
import com.chatopera.cc.util.*;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import com.github.xiaobo9.commons.kit.CookiesKit;
import com.github.xiaobo9.commons.kit.StringKit;
import com.github.xiaobo9.commons.utils.Base62Utils;
import com.github.xiaobo9.commons.utils.BrowserClient;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.model.UploadStatus;
import com.github.xiaobo9.repository.*;
import com.github.xiaobo9.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * FIXME method tooooooo long
 */
@Slf4j
@Controller
@RequestMapping("/im")
@EnableAsync
public class IMController extends Handler {

    @Autowired
    private SystemConfigService configService;

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    @Autowired
    private ACDPolicyService acdPolicyService;

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private IMService imService;
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
    private ContactsRepository contactsRes;

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

    @Autowired
    private DictService dictService;

    /**
     * 在客户或第三方网页内，写入聊天控件
     */
    @RequestMapping("/{id}.html")
    @Menu(type = "im", subtype = "point", access = true)
    public ModelAndView point(
            HttpServletRequest request, @PathVariable String id,
            @Valid String userid, @Valid String title, @Valid String aiid) {

        ModelAndView view = request(super.pageTplResponse("/apps/im/point"));
        view.addObject("channelVisitorSeparate", channelWebIMVisitorSeparate);
        if (StringUtils.isBlank(id)) {
            return view;
        }

        final String sessionid = UUIDUtils.removeHyphen(request.getSession().getId());
        log.info("[point] session snsid {}, session {}", id, sessionid);

        SNSAccount SnsAccountList = snsAccountRes.findBySnsidAndOrgi(id, super.getUser(request).getOrgi());
        view.addObject("webimexist", SnsAccountList != null);

        buildServerUrl(request, view);

        BrowserClient client = BrowserClient.parseClient(request.getHeader(BrowserClient.USER_AGENT));

        view.addObject("appid", id);
        view.addObject("client", UUIDUtils.getUUID());
        view.addObject("sessionid", sessionid);
        view.addObject("ip", MD5Utils.md5(request.getRemoteAddr()));
        view.addObject("mobile", client.isMobile());

        CousultInvite invite = onlineUserService.consult(id, Constants.SYSTEM_ORGI);
        if (invite != null) {
            log.info("[point] find CousultInvite {}", invite.getId());
            inviteMessage(id, aiid, view, invite);

            // 记录用户行为日志
            // 每次有一个新网页加载出聊天控件，都会生成一个userHistory
            UserHistory userHistory = new UserHistory();
            userHistory.setUrl(StringKit.subLongString(request.getHeader("referer"), 255));
            userHistory.setReferer(userHistory.getUrl());
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

            userHistory.setTitle(StringKit.subLongString(title, 255));

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

            AdType pointAdv = imService.getPointAdv(Enums.AdPosEnum.POINT.toString(), invite.getConsult_skill_fixed_id(), Constants.SYSTEM_ORGI);
            view.addObject("pointAd", pointAdv);
            AdType inviteAd = imService.getPointAdv(Enums.AdPosEnum.INVITE.toString(), invite.getConsult_skill_fixed_id(), Constants.SYSTEM_ORGI);
            view.addObject("inviteAd", inviteAd);
        } else {
            log.info("[point] invite id {}, orgi {} not found", id, Constants.SYSTEM_ORGI);
        }
        return view;
    }

    private void inviteMessage(String id, String aiid, ModelAndView view, CousultInvite invite) {
        view.addObject("inviteData", invite);
        view.addObject("orgi", invite.getOrgi());
        view.addObject("appid", id);

        if (StringUtils.isNotBlank(aiid)) {
            view.addObject("aiid", aiid);
        } else if (StringUtils.isNotBlank(invite.getAiid())) {
            view.addObject("aiid", invite.getAiid());
        }
    }

    private void buildServerUrl(HttpServletRequest request, ModelAndView view) {
        SystemConfig systemConfig = configService.getSystemConfig();
        String hostName = request.getServerName();
        String schema = request.getScheme();
        int port = request.getServerPort();
        if (systemConfig.isEnablessl()) {
            schema = "https";
            port = port == 80 ? 443 : port;
        } else {
            String header = request.getHeader("X-Forwarded-Proto");
            schema = header == null ? schema : header;
        }
        view.addObject("serverUrl", schema + "://" + hostName + ":" + port);
    }

    @ResponseBody
    @RequestMapping("/chatoperainit.html")
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
            @RequestParam String sessionid) {
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
        }
        if (whitelist_mode) {
            return "ok";
        }
        if (StringUtils.isNotBlank(uid) && StringUtils.isNotBlank(sid) && StringUtils.isNotBlank(cid)) {
            Contacts data = contactsRes.findOneByWluidAndWlsidAndWlcidAndDatastatus(uid, sid, cid, false);
            if (data == null) {
                data = new Contacts();
                data.setCreater(logined.getId());
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
            log.info("[online] online user {} is in black list.", userid);
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
     */
    @RequestMapping("/index.html")
    @Menu(type = "im", subtype = "index", access = true)
    public ModelAndView index(
            ModelMap modelMap,
            HttpServletRequest request,
            HttpServletResponse response,
            @Valid Contacts contacts,
            @Valid IMVO imvo) throws Exception {
        log.info("{}", imvo);
        Map<String, String> sessionMsg = cacheService.findOneSystemMapByIdAndOrgi(imvo.getSessionid(), imvo.getOrgi());

        HttpSession session = request.getSession();
        someSession(sessionMsg, session);

        ModelAndView view = request(super.pageTplResponse("/apps/im/index"));
        BlackEntity blackEntity = cacheService.findOneBlackEntityByUserIdAndOrgi(imvo.getUserid(), Constants.SYSTEM_ORGI).orElse(null);
        CousultInvite invite = onlineUserService.consult(imvo.getAppid(), imvo.getOrgi());
        // appid 或者 用户在黑名单里直接返回
        if (StringUtils.isBlank(imvo.getAppid()) || (blackEntity != null && blackEntity.inBlackStatus())) {
            view.addObject("inviteData", invite);
            log.info("[index] return view");
            return view;
        }

        String nickname;
        if (sessionMsg != null) {
            nickname = sessionMsg.get("username") + "@" + sessionMsg.get("company_name");
        } else if (session.getAttribute("Sessionusername") != null) {
            String struname = (String) session.getAttribute("Sessionusername");
            String strcname = (String) session.getAttribute("Sessioncompany_name");
            nickname = struname + "@" + strcname;
        } else {
            // 随机生成OnlineUser的用户名，使用了浏览器指纹做唯一性KEY
            String key = StringUtils.isNotBlank(imvo.getUserid()) ? imvo.getUserid() : imvo.getSessionid();
            nickname = "Guest_" + "@" + Base62Utils.genIDByKey(key);
        }
        view.addObject("nickname", nickname);

        boolean consult = true;                //是否已收集用户信息
        SessionConfig sessionConfig = acdPolicyService.initSessionConfig(imvo.getSkill(), imvo.getOrgi());

        // 强制开启满意调查问卷
        sessionConfig.setSatisfaction(true);

        modelMap.addAttribute("sessionConfig", sessionConfig);
        modelMap.addAttribute("schema", request.getScheme());
        modelMap.addAttribute("hostname", request.getServerName());
        modelMap.addAttribute("port", sslPort != null ? sslPort : port);

        modelMap.addAttribute("appid", imvo.getAppid());
        modelMap.addAttribute("userid", imvo.getUserid());
        modelMap.addAttribute("sessionid", imvo.getSessionid());
        modelMap.addAttribute("isInvite", imvo.isInvite());


        view.addObject("product", imvo.getProduct());
        view.addObject("description", imvo.getDescription());
        view.addObject("imgurl", imvo.getImgurl());
        view.addObject("pid", imvo.getPid());
        view.addObject("purl", imvo.getPurl());

        modelMap.addAttribute("ip", MD5Utils.md5(request.getRemoteAddr()));

        addAttribute(modelMap, "traceid", imvo.getTraceid());
        addAttribute(modelMap, "exchange", imvo.getExchange());
        addAttribute(modelMap, "title", imvo.getTitle());
        addAttribute(modelMap, "url", imvo.getUrl());

        modelMap.addAttribute("cskefuport", request.getServerPort());

        // 先检查 invite
        if (invite == null) {
            log.info("[index] can not invite for appid {}, orgi {}", imvo.getAppid(), imvo.getOrgi());
            return view;
        }
        log.info("[index] invite id {}, orgi {}", invite.getId(), invite.getOrgi());
        modelMap.addAttribute("orgi", invite.getOrgi());
        modelMap.addAttribute("inviteData", invite);

        addAttribute(modelMap, "aiid", StringUtils.isNotBlank(imvo.getAiid()) ? imvo.getAiid() : invite.getAiid());

        AgentReport report;
        if (invite.isSkill() && invite.isConsult_skill_fixed()) { // 绑定技能组
            report = acdWorkMonitor.getAgentReport(invite.getConsult_skill_fixed_id(), invite.getOrgi());
        } else {
            report = acdWorkMonitor.getAgentReport(invite.getOrgi());
        }

        boolean isLeavemsg = false;
        boolean inWorkingHours = MainUtils.isInWorkingHours(sessionConfig.getWorkinghours());
        if (report.getAgents() == 0 || (sessionConfig.isHourcheck() && !inWorkingHours && invite.isLeavemessage())) {
            // 没有坐席在线，进入留言
            isLeavemsg = true;
            modelMap.addAttribute("isInWorkingHours", inWorkingHours);
            view = request(super.pageTplResponse("/apps/im/leavemsg"));
        } else if (invite.isConsult_info()) {    //启用了信息收集，从Request获取， 或从 Cookies 里去
            // 验证 OnlineUser 信息
            // contacts用于传递信息，并不和 联系人表发生 关联，contacts信息传递给 Socket.IO，然后赋值给 AgentUser，最终赋值给 AgentService永久存储
            if (StringUtils.isNotBlank(contacts.getName())) {
                consult = true;
                contacts2Cookie(response, contacts, invite);
            } else {
                //从 Cookies里尝试读取
                if (invite.isConsult_info_cookies()) {
                    contacts = createContacts(request);
                }
                if (StringUtils.isBlank(contacts.getName())) {
                    consult = false;
                    view = request(super.pageTplResponse("/apps/im/collecting"));
                }
            }
        } else {
            saveAgentUserContacts(request, imvo, session);
        }

        vo2ModelMap(imvo, modelMap);

        modelMap.addAttribute("contacts", contacts);

        IP ipdata = IPTools.findGeography(IPUtils.getIpAddress(request));
        modelMap.addAttribute("skillGroups", onlineUserService.organ(invite.getOrgi(), ipdata, invite, true));

        if (consult) {
            if (StringUtils.isNotBlank(contacts.getName())) {
                nickname = contacts.getName();
            }

            modelMap.addAttribute("username", nickname);
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
                    if (StringUtils.equals(imvo.getAi(), "true") || (invite.isAifirst() && imvo.getAi() == null)) {
                        isChatbotAgentFirst = true;
                    }
                }
            }

            modelMap.addAttribute("exchange", isEnableExchangeAgentType);

            if (isChatbotAgentFirst) {
                // 机器人坐席
                HashMap<String, String> chatbotConfig = new HashMap<>();
                chatbotConfig.put("botname", invite.getAiname());
                chatbotConfig.put("botid", invite.getAiid());
                chatbotConfig.put("botwelcome", invite.getAimsg());
                chatbotConfig.put("botfirst", Boolean.toString(invite.isAifirst()));
                chatbotConfig.put("isai", Boolean.toString(invite.isAi()));

                modelMap.addAttribute("chatbotConfig", chatbotConfig);
                view = request(super.pageTplResponse("/apps/im/chatbot/index"));
                if (BrowserClient.isMobile(request.getHeader("User-Agent")) || StringUtils.isNotBlank(imvo.getMobile())) {
                    view = request(super.pageTplResponse("/apps/im/chatbot/mobile"));        // 智能机器人 移动端
                }
            } else {
                // 维持人工坐席的设定，检查是否进入留言
                if (!isLeavemsg && (BrowserClient.isMobile(request.getHeader("User-Agent")) || StringUtils.isNotBlank(imvo.getMobile()))) {
                    view = request(super.pageTplResponse("/apps/im/mobile"));    // WebIM移动端。再次点选技能组？
                }
            }

            PageRequest pageRequest = super.page(request, Direction.DESC, "updatetime");
            modelMap.addAttribute("chatMessageList", chatMessageRes.findByUsessionAndOrgi(imvo.getUserid(), imvo.getOrgi(), pageRequest));
        }
        view.addObject("commentList", dictService.getDic(Constants.CSKEFU_SYSTEM_COMMENT_DIC));
        view.addObject("commentItemList", dictService.getDic(Constants.CSKEFU_SYSTEM_COMMENT_ITEM_DIC));
        view.addObject("welcomeAd", imService.getPointAdv(Enums.AdPosEnum.WELCOME.toString(), imvo.getSkill(), imvo.getOrgi()));
        view.addObject("imageAd", imService.getPointAdv(Enums.AdPosEnum.IMAGE.toString(), imvo.getSkill(), imvo.getOrgi()));

        // 确定"接受邀请"被处理后，通知浏览器关闭弹出窗口
        onlineUserService.sendWebIMClients(imvo.getUserid(), "accept");

        // 更新InviteRecord
        imService.updateInviteRecord(imvo);
        log.info("[index] return view");
        return view;
    }

    private Contacts createContacts(HttpServletRequest request) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        Contacts contacts = new Contacts();
        Map<String, String> map = CookiesKit.cookie2Map(request);
        if (map.isEmpty()) {
            return contacts;
        }
        contacts.setName(decode(map.get("name")));
        contacts.setPhone(decode(map.get("phone")));
        contacts.setEmail(decode(map.get("email")));
        contacts.setMemo(decode(map.get("memo")));
        contacts.setSkypeid(decode(map.get("skypeid")));
        return contacts;
    }

    private void contacts2Cookie(HttpServletResponse response, Contacts contacts, CousultInvite invite) throws UnsupportedEncodingException {
        //存入 Cookies
        if (invite.isConsult_info_cookies()) {
            addCookie(response, "name", contacts.getName());
            addCookie(response, "phone", contacts.getPhone());
            addCookie(response, "email", contacts.getEmail());
            addCookie(response, "skypeid", contacts.getSkypeid());
            addCookie(response, "memo", contacts.getMemo());
        }
    }

    private void saveAgentUserContacts(HttpServletRequest request, IMVO imvo, HttpSession session) {
        // TODO 该contacts的识别并不准确，因为不能关联
        // contacts = OnlineUserProxy.processContacts(invite.getOrgi(), contacts, appid, userid);
        String uid = (String) session.getAttribute("Sessionuid");
        String sid = (String) session.getAttribute("Sessionsid");
        String cid = (String) session.getAttribute("Sessioncid");

        if (StringUtils.isBlank(uid) || StringUtils.isBlank(sid) || StringUtils.isBlank(cid)) {
            return;
        }
        Contacts contacts = contactsRes.findOneByWluidAndWlsidAndWlcidAndDatastatus(uid, sid, cid, false);
        if (contacts == null) {
            return;
        }
        agentUserRepository.findOneByUseridAndOrgi(imvo.getUserid(), imvo.getOrgi()).ifPresent(p -> {
            // 关联AgentService的联系人
            if (StringUtils.isNotBlank(p.getAgentserviceid())) {
                AgentService agentService = agentServiceRepository.findById(p.getAgentserviceid())
                        .orElseThrow(EntityNotFoundEx::new);
                agentService.setContactsid(contacts.getId());
            }

            String username = (String) session.getAttribute("Sessionusername");
            imService.saveAgentUserContacts(imvo, contacts, p, super.getUser(request), username);
        });
    }


    private void vo2ModelMap(IMVO imvo, ModelMap map) {
        addAttribute(map, "client", imvo.getClient());
        addAttribute(map, "skill", imvo.getSkill());
        addAttribute(map, "agent", imvo.getAgent());
        addAttribute(map, "type", imvo.getType());
    }

    private void someSession(Map<String, String> sessionMsg, HttpSession session) {
        if (sessionMsg != null) {
            session.setAttribute("Sessionusername", sessionMsg.get("username"));
            session.setAttribute("Sessioncid", sessionMsg.get("cid"));
            session.setAttribute("Sessioncompany_name", sessionMsg.get("company_name"));
            session.setAttribute("Sessionsid", sessionMsg.get("sid"));
            session.setAttribute("Sessionsystem_name", sessionMsg.get("system_name"));
            session.setAttribute("sessionid", sessionMsg.get("sessionid"));
            session.setAttribute("Sessionuid", sessionMsg.get("uid"));
        }
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

    private String decode(String value) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return URLDecoder.decode(MainUtils.decryption(value), "UTF-8");
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

        inviteMessage(appid, aiid, view, invite);

        return view;
    }


    @RequestMapping("/leavemsg/save.html")
    @Menu(type = "admin", subtype = "user")
    public ModelAndView leavemsgsave(@Valid String appid, @Valid LeaveMsg msg, @Valid String skillId) {
        if (StringUtils.isNotBlank(appid)) {
            snsAccountRepository.findBySnsid(appid).ifPresent(p -> {
                CousultInvite invite = inviteRepository.findBySnsaccountidAndOrgi(appid, Constants.SYSTEM_ORGI);
                // TODO 增加策略防止恶意刷消息
                //  List<LeaveMsg> msgList = leaveMsgRes.findByOrgiAndUserid(invite.getOrgi(), msg.getUserid());
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
    public void refuse(@Valid String orgi, @Valid String userid) {
        onlineUserService.refuseInvite(userid);
        final Date threshold = new Date(System.currentTimeMillis() - Constants.WEBIM_AGENT_INVITE_TIMEOUT);
        PageRequest page = PageRequest.of(0, 1, Direction.DESC, "createtime");
        Page<InviteRecord> inviteRecords = inviteRecordRes.findByUseridAndOrgiAndResultAndCreatetimeGreaterThan(
                userid,
                orgi,
                Enums.OnlineUserInviteStatus.DEFAULT.toString(),
                threshold, page);
        if (inviteRecords.getContent().size() > 0) {
            InviteRecord record = inviteRecords.getContent().get(0);
            record.setUpdatetime(new Date());
            record.setResponsetime((int) (System.currentTimeMillis() - record.getCreatetime().getTime()));
            record.setResult(Enums.OnlineUserInviteStatus.REFUSE.toString());
            inviteRecordRes.save(record);
        }
    }

    @RequestMapping("/satis")
    @Menu(type = "im", subtype = "satis", access = true)
    public void satis(@Valid AgentServiceSatis satis) {
        if (satis != null && StringUtils.isNotBlank(satis.getId())) {
            int count = agentServiceSatisRes.countById(satis.getId());
            if (count == 1) {
                satis.setSatiscomment(StringKit.subLongString(satis.getSatiscomment(), 255));
                satis.setSatisfaction(true);
                satis.setSatistime(new Date());
                agentServiceSatisRes.save(satis);
            }
        }
    }

    @PostMapping("/image/upload.html")
    @Menu(type = "im", subtype = "image", access = true)
    public ModelAndView upload(
            ModelMap map, HttpServletRequest request,
            @RequestParam(value = "imgFile") MultipartFile multipart,
            @Valid String channel,
            @Valid String userid,
            @Valid String username,
            @Valid String appid,
            @Valid String orgi,
            @Valid String paste) throws IOException {
        ModelAndView view = request(super.pageTplResponse("/apps/im/upload"));

        String originalFilename = multipart.getOriginalFilename();
        if (originalFilename == null || originalFilename.lastIndexOf(".") <= 0 || StringUtils.isBlank(userid)) {
            map.addAttribute("upload", new UploadStatus("请选择文件"));
            return view;
        }

        final User user = super.getUser(request);

        String fileid = UUIDUtils.getUUID();
        StreamingFile sf = new StreamingFile();
        sf.setId(fileid);
        sf.setName(originalFilename);
        sf.setMime(multipart.getContentType());
        // 图片
        long size = multipart.getSize();
        if (multipart.getContentType() != null && multipart.getContentType().contains(Constants.ATTACHMENT_TYPE_IMAGE)) {
            String invalid = StreamingFileUtil.validate(Constants.ATTACHMENT_TYPE_IMAGE, originalFilename);
            if (invalid != null) {
                map.addAttribute("upload", new UploadStatus(invalid));
                return view;
            }
            File imageFile = new File(uploadService.getUploadPath(), fileid + "_original");
            FileCopyUtils.copy(multipart.getBytes(), imageFile);
            File thumbnail = new File(uploadService.getUploadPath(), fileid);
            ThumbnailUtils.processImage(thumbnail, imageFile);

            //  存储数据库
            sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), size));
            sf.setThumbnail(jpaBlobHelper.createBlobWithFile(thumbnail));
            streamingFileRepository.save(sf);
            String fileUrl = "/res/image.html?id=" + fileid;

            if (paste == null) {
                IMUploadFileBO bo = IMUploadFileBO.of(fileid, multipart.getName(), size, fileUrl, userid)
                        .setName(username);
                if (StringUtils.isNotBlank(channel)) {
                    imService.uploadMediaMessageWithChannel(bo, Enums.MediaType.IMAGE, channel, appid, orgi);
                } else {
                    imService.uploadMediaMessage(bo, Enums.MediaType.IMAGE);
                }
            }
            map.addAttribute("upload", new UploadStatus("0", fileUrl));
            return view;
        }

        // 文件
        String invalid = StreamingFileUtil.validate(Constants.ATTACHMENT_TYPE_FILE, originalFilename);
        if (invalid != null) {
            map.addAttribute("upload", new UploadStatus(invalid));
            return view;
        }
        // 存储数据库
        sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), size));
        streamingFileRepository.save(sf);

        // 存储到本地硬盘
        String id = attachmentService.processAttachmentFile(multipart, fileid, user.getOrgi(), user.getId()).getId();
        String fileUrl = "/res/file.html?id=" + id;
        UploadStatus upload = new UploadStatus("0", fileUrl);

        IMUploadFileBO bo = IMUploadFileBO.of(id, originalFilename, size, fileUrl, userid)
                .setUserName(username);
        if (StringUtils.isNotBlank(channel)) {
            imService.uploadMediaMessageWithChannel(bo, Enums.MediaType.FILE, channel, appid, orgi);
        } else {
            imService.uploadMediaMessage(bo, Enums.MediaType.FILE);
        }
        map.addAttribute("upload", upload);
        return view;
    }
}
