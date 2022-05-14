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
import com.github.xiaobo9.commons.kit.CookiesKit;
import com.github.xiaobo9.service.LoginService;
import com.github.xiaobo9.service.SystemConfigService;
import com.chatopera.cc.service.UserService;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.commons.enums.AgentStatusEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.AgentStatus;
import com.github.xiaobo9.entity.SystemConfig;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.repository.UserRepository;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
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
    private UserService userService;

    @Autowired
    private AuthToken authToken;

    @Autowired
    private AgentProxyService agentServiceService;

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    /**
     * 登录页面
     */
    @GetMapping(value = "/login.html")
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView login(HttpServletRequest request, @RequestHeader(value = "referer", required = false) String referer, @Valid String msg) {
        Map<String, Object> configModel = getConfigModel();
        if (StringUtils.isNotBlank(msg)) {
            configModel.put("msg", msg);
        }

        // 已经登录，直接跳转到首页
        if (request.getSession(true).getAttribute(Constants.USER_SESSION_NAME) != null) {
            return new ModelAndView("redirect:/", configModel);
        }

        ModelAndView view = new ModelAndView("/login");
        String paramReferer = request.getParameter("referer");
        referer = StringUtils.isNotBlank(paramReferer) ? paramReferer : referer;
        if (StringUtils.isNotBlank(referer)) {
            view.addObject("referer", referer);
        }

        Cookie cookie = CookiesKit.getCookie(request, Constants.CSKEFU_SYSTEM_COOKIES_FLAG).orElse(null);
        try {
            if (cookie != null) {
                User user = userRepository.findById(MainUtils.decryption(cookie.getValue())).orElse(null);
                if (user != null) {
                    view = this.processLogin(request, user, referer);
                }
            }
        } catch (EncryptionOperationNotPossibleException e) {
            logger.error("[login] error:", e);
            return request(super.pageTplResponse("/public/clearcookie"));
        } catch (NoSuchAlgorithmException e) {
            logger.error("[login] error:", e);
        }

        view.addAllObjects(configModel);
        return view;
    }

    /**
     * 提交登录表单
     */
    @PostMapping(value = "/login.html")
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView login(
            final HttpServletRequest request,
            final HttpServletResponse response,
            @Valid User user,
            @Valid String referer,
            @Valid String sla) throws NoSuchAlgorithmException {
        HttpSession session = request.getSession(true);
        // 已经登录，直接跳转
        if (session.getAttribute(Constants.USER_SESSION_NAME) != null) {
            return new ModelAndView("redirect:/", getConfigModel());
        }
        // 没有提供登录信息，跳转到登录页面
        if (user == null || user.getUsername() == null) {
            ModelAndView view = request(super.pageTplResponse("/login"));
            view.addAllObjects(getConfigModel());
            return view;
        }

        User loginUser = userService.getUserByUserNameAndPassword(user.getUsername(), user.getPassword());
        // 用户信息不匹配
        if (loginUser == null) {
            ModelAndView view = request(super.pageTplResponse("/login"));
            view.addAllObjects(getConfigModel());
            if (StringUtils.isNotBlank(referer)) {
                view.addObject("referer", referer);
            }
            view.addObject("msg", "0");
            return view;
        }
        ModelAndView view = this.processLogin(request, loginUser, referer);
        view.addAllObjects(getConfigModel());

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
        if ((loginUser.isAgent() && roleAuthMap.containsKey("A01") && ((boolean) roleAuthMap.get("A01"))) || loginUser.isAdmin()) {
            try {
                /****************************************
                 * 登录成功，设置该坐席为就绪状态（默认）
                 ****************************************/
                // https://gitlab.chatopera.com/chatopera/cosinee.w4l/issues/306
                AgentStatus agentStatus = agentServiceService.resolveAgentStatusByAgentnoAndOrgi(
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
        request.getSession().removeAttribute(Constants.USER_SESSION_NAME);
        request.getSession().invalidate();

        CookiesKit.getCookie(request, Constants.CSKEFU_SYSTEM_COOKIES_FLAG)
                .ifPresent(cookie -> {
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                });

        if (StringUtils.isNotBlank(code)) {
            return "redirect:/?msg=" + code;
        }

        return "redirect:/";
    }

    @RequestMapping(value = "/register.html")
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView register(HttpServletRequest request, @Valid String msg) {
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

    private Map<String, Object> getConfigModel() {
        Map<String, Object> configModel = Maps.newHashMap();
        configModel.put("systemConfig", configService.getSystemConfig());
        return configModel;
    }

}
