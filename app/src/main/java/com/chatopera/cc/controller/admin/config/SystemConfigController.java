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
package com.chatopera.cc.controller.admin.config;

import com.chatopera.cc.basic.Constants;
import com.github.xiaobo9.commons.enums.Enums;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.util.Dict;
import com.chatopera.cc.util.Menu;
import com.corundumstudio.socketio.SocketIOServer;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.Secret;
import com.github.xiaobo9.entity.SysDic;
import com.github.xiaobo9.entity.SystemConfig;
import com.github.xiaobo9.repository.SecretRepository;
import com.github.xiaobo9.repository.SystemConfigRepository;
import com.github.xiaobo9.repository.SystemMessageRepository;
import com.github.xiaobo9.repository.TemplateRepository;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Controller
@RequestMapping("/admin/config")
public class SystemConfigController extends Handler {

    @Value("${web.upload-path}")
    private String path;

    @Autowired
    private SocketIOServer server;

    @Autowired
    private SystemConfigRepository systemConfigRes;

    @Autowired
    private SystemMessageRepository systemMessageRes;

    @Autowired
    private SecretRepository secRes;

    @Autowired
    private TemplateRepository templateRes;

    @Autowired
    private CacheService cacheService;

    @RequestMapping("/index.html")
    @Menu(type = "admin", subtype = "config", admin = true)
    public ModelAndView index(ModelMap map, HttpServletRequest request, @Valid String execute) throws SQLException {
        map.addAttribute("server", server);
        if (MainContext.hasModule(Constants.CSKEFU_MODULE_ENTIM)) {
            map.addAttribute(Constants.CSKEFU_MODULE_ENTIM, true);
        }
        if (request.getSession().getAttribute(Constants.CSKEFU_SYSTEM_INFOACQ) != null) {
            map.addAttribute(
                    Constants.CSKEFU_MODULE_ENTIM, request.getSession().getAttribute(Constants.CSKEFU_SYSTEM_INFOACQ));
        }
        map.addAttribute("server", server);
        map.addAttribute("imServerStatus", MainContext.getIMServerStatus());
        List<Secret> secretConfig = secRes.findByOrgi(super.getOrgi(request));
        if (secretConfig != null && secretConfig.size() > 0) {
            map.addAttribute("secret", secretConfig.get(0));
        }
        List<SysDic> dicList = Dict.getInstance().getDic(Constants.CSKEFU_SYSTEM_DIC);
        SysDic callCenterDic = null, workOrderDic = null, smsDic = null;
        for (SysDic dic : dicList) {
            if (dic.getCode().equals(Constants.CSKEFU_SYSTEM_CALLCENTER)) {
                callCenterDic = dic;
            }
            if (dic.getCode().equals(Constants.CSKEFU_SYSTEM_WORKORDEREMAIL)) {
                workOrderDic = dic;
            }
            if (dic.getCode().equals(Constants.CSKEFU_SYSTEM_SMSEMAIL)) {
                smsDic = dic;
            }
        }
        if (callCenterDic != null) {
            map.addAttribute(
                    "templateList",
                    templateRes.findByTemplettypeAndOrgi(callCenterDic.getId(), super.getOrgi(request)));
        }
        if (workOrderDic != null) {
            map.addAttribute(
                    "workOrderList",
                    templateRes.findByTemplettypeAndOrgi(workOrderDic.getId(), super.getOrgi(request)));
        }
        if (smsDic != null) {
            map.addAttribute("smsList", templateRes.findByTemplettypeAndOrgi(smsDic.getId(), super.getOrgi(request)));
        }

        map.addAttribute(
                "sysMessageList", systemMessageRes.findByMsgtypeAndOrgi(Enums.SystemMessageType.EMAIL.toString(),
                        super.getOrgi(request)));

        if (StringUtils.isNotBlank(execute) && execute.equals("false")) {
            map.addAttribute("execute", execute);
        }
        if (StringUtils.isNotBlank(request.getParameter("msg"))) {
            map.addAttribute("msg", request.getParameter("msg"));
        }
        return request(super.createAdminTemplateResponse("/admin/config/index"));
    }

    @RequestMapping("/stopimserver.html")
    @Menu(type = "admin", subtype = "stopimserver", admin = true)
    public ModelAndView stopimserver(HttpServletRequest request, @Valid String confirm) {
        List<Secret> secretConfig = secRes.findByOrgi(super.getOrgi(request));
        boolean execute = MainUtils.secConfirm(confirm, secretConfig);
        if (execute) {
            server.stop();
            MainContext.setIMServerStatus(false);
        }
        return request(super.pageTplResponse("redirect:/admin/config/index.html?execute=" + execute));
    }

    @RequestMapping("/startentim.html")
    @Menu(type = "admin", subtype = "startentim", admin = true)
    public ModelAndView startentim() throws SQLException {
        MainContext.enableModule(Constants.CSKEFU_MODULE_ENTIM);
        return request(super.pageTplResponse("redirect:/admin/config/index.html"));
    }

    @RequestMapping("/stopentim.html")
    @Menu(type = "admin", subtype = "stopentim", admin = true)
    public ModelAndView stopentim() throws SQLException {
        MainContext.removeModule(Constants.CSKEFU_MODULE_ENTIM);
        return request(super.pageTplResponse("redirect:/admin/config/index.html"));
    }

    /**
     * 危险操作，请谨慎调用 ， WebLogic/WebSphere/Oracle等中间件服务器禁止调用
     */
    @RequestMapping("/stop.html")
    @Menu(type = "admin", subtype = "stop", admin = true)
    public ModelAndView stop(HttpServletRequest request, @Valid String confirm) throws SQLException {
        List<Secret> secretConfig = secRes.findByOrgi(super.getOrgi(request));
        boolean execute = MainUtils.secConfirm(confirm, secretConfig);
        if (execute) {
            server.stop();
            MainContext.setIMServerStatus(false);
            System.exit(0);
        }
        return request(super.pageTplResponse("redirect:/admin/config/index.html?execute=" + execute));
    }


    @RequestMapping("/save.html")
    @Menu(type = "admin", subtype = "save", admin = true)
    public ModelAndView save(
            ModelMap map, HttpServletRequest request, @Valid SystemConfig config,
            @RequestParam(value = "keyfile", required = false) MultipartFile keyfile,
            @RequestParam(value = "loginlogo", required = false) MultipartFile loginlogo,
            @RequestParam(value = "consolelogo", required = false) MultipartFile consolelogo,
            @RequestParam(value = "favlogo", required = false) MultipartFile favlogo,
            @Valid Secret secret) throws SQLException, IOException, NoSuchAlgorithmException {
        SystemConfig systemConfig = systemConfigRes.findByOrgi(Constants.SYSTEM_ORGI);
        config.setOrgi(Constants.SYSTEM_ORGI);
        String msg = "0";
        if (StringUtils.isBlank(config.getJkspassword())) {
            config.setJkspassword(null);
        }
        if (systemConfig == null) {
            systemConfig = config;
            config.setCreater(super.getUser(request).getId());
            config.setCreatetime(new Date());
        } else {
            MainUtils.copyProperties(config, systemConfig);
        }
        File sslDir = new File(path, "ssl");
        if (config.isEnablessl()) {
            if (keyfile != null) {
                byte[] bytes = keyfile.getBytes();
                String filename = keyfile.getOriginalFilename();
                if (bytes.length > 0 && filename != null && filename.length() > 0) {
                    FileUtils.forceMkdirParent(sslDir);
                    FileUtils.writeByteArrayToFile(new File(sslDir, filename), bytes);
                    systemConfig.setJksfile(filename);
                    Properties prop = new Properties();
                    try (FileOutputStream oFile = new FileOutputStream(new File(sslDir, "https.properties"))) {
                        prop.setProperty("key-store-password", MainUtils.encryption(systemConfig.getJkspassword()));
                        prop.setProperty("key-store", systemConfig.getJksfile());
                        prop.store(oFile, "SSL Properties File");
                    }
                }
            }
        } else {
            if (sslDir.exists()) {
                FileUtils.cleanDirectory(sslDir);
            }
        }

        if (validLogoFile(loginlogo)) {
            systemConfig.setLoginlogo(super.saveImageFileWithMultipart(loginlogo));
        }
        if (validLogoFile(consolelogo)) {
            systemConfig.setConsolelogo(super.saveImageFileWithMultipart(consolelogo));
        }
        if (validLogoFile(favlogo)) {
            systemConfig.setFavlogo(super.saveImageFileWithMultipart(favlogo));
        }

        String orgi = super.getOrgi(request);
        if (secret != null && StringUtils.isNotBlank(secret.getPassword())) {
            String repassword = request.getParameter("repassword");
            if (StringUtils.isNotBlank(repassword) && repassword.equals(secret.getPassword())) {
                List<Secret> secretConfig = secRes.findByOrgi(orgi);
                if (secretConfig != null && secretConfig.size() > 0) {
                    Secret tempSecret = secretConfig.get(0);
                    String oldpass = request.getParameter("oldpass");
                    if (StringUtils.isNotBlank(oldpass) && MD5Utils.md5(oldpass).equals(tempSecret.getPassword())) {
                        tempSecret.setPassword(MD5Utils.md5(secret.getPassword()));
                        msg = "1";
                        tempSecret.setEnable(true);
                        secRes.save(tempSecret);
                    } else {
                        msg = "3";
                    }
                } else {
                    secret.setOrgi(orgi);
                    secret.setCreater(super.getUser(request).getId());
                    secret.setCreatetime(new Date());
                    secret.setPassword(MD5Utils.md5(secret.getPassword()));
                    secret.setEnable(true);
                    msg = "1";
                    secRes.save(secret);
                }
            } else {
                msg = "2";
            }
            map.addAttribute("msg", msg);
        }
        systemConfigRes.save(systemConfig);

        cacheService.putSystemByIdAndOrgi("systemConfig", orgi, systemConfig);
        map.addAttribute("imServerStatus", MainContext.getIMServerStatus());

        return request(super.pageTplResponse("redirect:/admin/config/index.html?msg=" + msg));
    }

    private boolean validLogoFile(MultipartFile file) {
        return file != null && StringUtils.isNotBlank(file.getOriginalFilename()) && file.getOriginalFilename().lastIndexOf(".") > 0;
    }
}