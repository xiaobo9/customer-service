/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
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
package com.chatopera.cc.controller;

import com.chatopera.cc.acd.ACDWorkMonitor;
import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.basic.auth.AuthToken;
import com.chatopera.cc.service.AgentProxyService;
import com.chatopera.cc.service.LoginService;
import com.chatopera.cc.service.SystemConfigService;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.commons.enums.AgentStatusEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.AgentStatus;
import com.github.xiaobo9.entity.SystemConfig;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.repository.UserRepository;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import org.apache.commons.lang.StringUtils;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * @author CSKefu
 * @version 1.0.1
 */
@Controller
public class LoginController extends Handler {
    private final static Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private LoginService service;

    @Autowired
    private SystemConfigService configService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthToken authToken;

    @Autowired
    private AgentProxyService agentServiceService;

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    @Value("${tongji.baidu.sitekey}")
    private String tongjiBaiduSiteKey;

    /**
     * 登录页面
     *
     * @param request
     * @param referer
     * @param msg
     * @return
     * @throws NoSuchAlgorithmException
     */
    @RequestMapping(value = "/login.html", method = RequestMethod.GET)
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView login(HttpServletRequest request, @RequestHeader(value = "referer", required = false) String referer, @Valid String msg) {
        ModelAndView view = new ModelAndView("redirect:/");
        if (request.getSession(true).getAttribute(Constants.USER_SESSION_NAME) == null) {
            view = new ModelAndView("/login");
            if (StringUtils.isNotBlank(request.getParameter("referer"))) {
                referer = request.getParameter("referer");
            }
            if (StringUtils.isNotBlank(referer)) {
                view.addObject("referer", referer);
            }
            Cookie[] cookies = request.getCookies(); // 这样便可以获取一个cookie数组
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie != null && StringUtils.isNotBlank(cookie.getName()) && StringUtils.isNotBlank(
                            cookie.getValue())) {
                        if (cookie.getName().equals(Constants.CSKEFU_SYSTEM_COOKIES_FLAG)) {
                            try {
                                String flagid = MainUtils.decryption(cookie.getValue());
                                if (StringUtils.isNotBlank(flagid)) {
                                    User user = userRepository.findById(flagid).orElse(null);
                                    if (user != null) {
                                        view = this.processLogin(request, user, referer);
                                    }
                                }
                            } catch (EncryptionOperationNotPossibleException e) {
                                logger.error("[login] error:", e);
                                view = request(super.pageTplResponse("/public/clearcookie"));
                                return view;
                            } catch (NoSuchAlgorithmException e) {
                                logger.error("[login] error:", e);
                            }
                        }
                    }
                }
            }
        }
        if (StringUtils.isNotBlank(msg)) {
            view.addObject("msg", msg);
        }
        SystemConfig systemConfig = configService.getSystemConfig();
        view.addObject("show", systemConfig.isEnableregorgi());
        view.addObject("systemConfig", systemConfig);

        if (StringUtils.isNotBlank(tongjiBaiduSiteKey) && !StringUtils.equalsIgnoreCase(tongjiBaiduSiteKey, "placeholder")) {
            view.addObject("tongjiBaiduSiteKey", tongjiBaiduSiteKey);
        }

        return view;
    }

    /**
     * 提交登录表单
     *
     * @param request
     * @param response
     * @param user
     * @param referer
     * @param sla
     * @return
     * @throws NoSuchAlgorithmException
     */
    @RequestMapping(value = "/login.html", method = RequestMethod.POST)
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView login(
            final HttpServletRequest request,
            final HttpServletResponse response,
            @Valid User user,
            @Valid String referer,
            @Valid String sla) throws NoSuchAlgorithmException {
        ModelAndView view = new ModelAndView("redirect:/");
        HttpSession session = request.getSession(true);
        if (session.getAttribute(Constants.USER_SESSION_NAME) != null) {
            SystemConfig systemConfig = configService.getSystemConfig();
            view.addObject("show", systemConfig.isEnableregorgi());
            view.addObject("systemConfig", systemConfig);
            return view;
        }
        if (user != null && user.getUsername() != null) {
            final User loginUser = userRepository.findByUsernameAndPasswordAndDatastatus(user.getUsername(), MD5Utils.md5(user.getPassword()), false);
            if (loginUser != null && StringUtils.isNotBlank(loginUser.getId())) {
                view = this.processLogin(request, loginUser, referer);

                // 自动登录
                if (StringUtils.equals("1", sla)) {
                    Cookie flagid = new Cookie(Constants.CSKEFU_SYSTEM_COOKIES_FLAG, MainUtils.encryption(loginUser.getId()));
                    flagid.setMaxAge(7 * 24 * 60 * 60);
                    response.addCookie(flagid);
                }

                // add authorization code for rest api
                final String orgi = loginUser.getOrgi();
                String auth = UUIDUtils.getUUID();
                authToken.putUserByAuth(auth, loginUser);
                userRepository.save(loginUser); // 更新登录状态到数据库
                response.addCookie((new Cookie("authorization", auth)));

                // 该登录用户是坐席，并且具有坐席对话的角色
                Map<String, Object> roleAuthMap = loginUser.getRoleAuthMap();
                if ((loginUser.isAgent() &&
                        roleAuthMap.containsKey("A01") &&
                        ((boolean) roleAuthMap.get("A01")))
                        || loginUser.isAdmin()) {
                    try {
                        /****************************************
                         * 登录成功，设置该坐席为就绪状态（默认）
                         ****************************************/
                        // https://gitlab.chatopera.com/chatopera/cosinee.w4l/issues/306
                        final AgentStatus agentStatus = agentServiceService.resolveAgentStatusByAgentnoAndOrgi(
                                loginUser.getId(), orgi, loginUser.getSkills());
                        agentStatus.setBusy(false);
                        agentServiceService.ready(loginUser, agentStatus, false);

                        // 工作状态记录
                        acdWorkMonitor.recordAgentStatus(agentStatus.getAgentno(),
                                agentStatus.getUsername(),
                                agentStatus.getAgentno(),
                                user.isAdmin(), // 0代表admin
                                agentStatus.getAgentno(),
                                AgentStatusEnum.OFFLINE.toString(),
                                AgentStatusEnum.READY.toString(),
                                Enums.AgentWorkType.MEIDIACHAT.toString(),
                                orgi, null);

                    } catch (Exception e) {
                        logger.error("[login] set agent status", e);
                    }
                }
            } else {
                view = request(super.pageTplResponse("/login"));
                if (StringUtils.isNotBlank(referer)) {
                    view.addObject("referer", referer);
                }
                view.addObject("msg", "0");
            }
        }
        SystemConfig systemConfig = configService.getSystemConfig();
        view.addObject("show", systemConfig.isEnableregorgi());
        view.addObject("systemConfig", systemConfig);
        return view;
    }

    /**
     * 处理登录事件
     *
     * @param request
     * @param loginUser
     * @param referer
     * @return
     */
    private ModelAndView processLogin(final HttpServletRequest request, @NotNull final User loginUser, String referer) {
        ModelAndView view;
        if (StringUtils.isNotBlank(referer)) {
            view = new ModelAndView("redirect:" + referer);
        } else {
            view = new ModelAndView("redirect:/");
        }
        // 登录成功 判断是否进入多租户页面
        SystemConfig systemConfig = configService.getSystemConfig();
        if (systemConfig.isEnabletneant() && systemConfig.isTenantconsole() && !loginUser.isAdmin()) {
            view = new ModelAndView("redirect:/apps/tenant/index");
        }

        service.processLogin(loginUser, request.getSession().getId());

        super.setUser(request, loginUser);
        return view;
    }


    /**
     * 登出用户
     * code代表登出的原因
     *
     * @param request
     * @param response
     * @param code     登出的代码
     * @return
     */
    @RequestMapping("/logout.html")
    public String logout(HttpServletRequest request, HttpServletResponse response, @RequestParam(value = "code", required = false) String code) throws UnsupportedEncodingException {
        final User user = super.getUser(request);
        request.getSession().removeAttribute(Constants.USER_SESSION_NAME);
        request.getSession().invalidate();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie != null && StringUtils.isNotBlank(cookie.getName()) && StringUtils.isNotBlank(
                        cookie.getValue())) {
                    if (cookie.getName().equals(Constants.CSKEFU_SYSTEM_COOKIES_FLAG)) {
                        cookie.setMaxAge(0);
                        response.addCookie(cookie);
                    }
                }
            }
        }

        if (StringUtils.isNotBlank(code)) {
            return "redirect:/?msg=" + code;
        }

        return "redirect:/";
    }

    @RequestMapping(value = "/register.html")
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView register(HttpServletRequest request, HttpServletResponse response, @Valid String msg) {
        ModelAndView view = request(super.pageTplResponse("redirect:/"));
        if (request.getSession(true).getAttribute(Constants.USER_SESSION_NAME) == null) {
            view = request(super.pageTplResponse("/register"));
        }
        if (StringUtils.isNotBlank(msg)) {
            view.addObject("msg", msg);
        }
        return view;
    }

    @RequestMapping("/addAdmin.html")
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView addAdmin(HttpServletRequest request, @Valid User user) {
        String msg = service.validUser(user);
        if (StringUtils.isNotBlank(msg)) {
            return request(super.pageTplResponse("redirect:/register.html?msg=" + msg));
        }
        user.setUname(user.getUsername());
        user.setAdmin(true);
        if (StringUtils.isNotBlank(user.getPassword())) {
            user.setPassword(MD5Utils.md5(user.getPassword()));
        }
        user.setOrgi(super.getOrgi());
        userRepository.save(user);
        return this.processLogin(request, user, "");
    }

}
