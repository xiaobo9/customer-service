/*
 * Copyright (C) 2018-2019 Chatopera Inc, All rights reserved.
 * <https://www.chatopera.com>
 * This software and related documentation are provided under a license agreement containing
 * restrictions on use and disclosure and are protected by intellectual property laws.
 * Except as expressly permitted in your license agreement or allowed by law, you may not use,
 * copy, reproduce, translate, broadcast, modify, license, transmit, distribute, exhibit, perform,
 * publish, or display any part, in any form, or by any means. Reverse engineering, disassembly,
 * or decompilation of this software, unless required by law for interoperability, is prohibited.
 */
package com.chatopera.cc.controller.api;

import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.controller.api.request.RestUtils;
import com.chatopera.cc.service.OnlineUserService;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.entity.InviteRecord;
import com.github.xiaobo9.entity.OnlineUser;
import com.github.xiaobo9.repository.InviteRecordRepository;
import com.github.xiaobo9.repository.OnlineUserRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/apps")
public class ApiAppsController extends Handler {
    private final static Logger logger = LoggerFactory.getLogger(ApiAppsController.class);

    @Autowired
    private OnlineUserRepository onlineUserRes;

    @Autowired
    private InviteRecordRepository inviteRecordRes;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private OnlineUserService onlineUserService;

    @RequestMapping(method = RequestMethod.POST)
    @Menu(type = "apps", subtype = "apps", access = true)
    public ResponseEntity<String> operations(HttpServletRequest request, @RequestBody final String body, @Valid String q) {
        logger.info("[operations] body {}, q {}", body, q);
        final JsonObject j = StringUtils.isBlank(body) ? (new JsonObject()) : (new JsonParser()).parse(
                body).getAsJsonObject();

        JsonObject json = new JsonObject();
        HttpHeaders headers = RestUtils.header();

        if (!j.has("ops")) {
            json.addProperty(RestUtils.RESP_KEY_RC, RestUtils.RESP_RC_FAIL_1);
            json.addProperty(RestUtils.RESP_KEY_ERROR, "???????????????????????????");
        } else {
            switch (StringUtils.lowerCase(j.get("ops").getAsString())) {
                case "invite":
                    json = invite(request, j);
                    break;
                default:
                    json.addProperty(RestUtils.RESP_KEY_RC, RestUtils.RESP_RC_FAIL_2);
                    json.addProperty(RestUtils.RESP_KEY_ERROR, "?????????????????????");
            }
        }

        return new ResponseEntity<String>(json.toString(), headers, HttpStatus.OK);

    }

    /**
     * ????????????????????????
     *
     * @param request
     * @param j
     * @return
     */
    private JsonObject invite(final HttpServletRequest request, final JsonObject j) {
        JsonObject resp = new JsonObject();
        final String orgi = super.getOrgi(request);
        final String agentno = super.getUser(request).getId();

        final String userid = j.get("userid").getAsString();

        logger.info("[invite] agentno {} invite onlineUser {}", agentno, userid);
        OnlineUser onlineUser = onlineUserService.onlineuser(userid, orgi);

        if (onlineUser != null) {
            logger.info("[invite] userid {}, agentno {}, orgi {}", userid, agentno, orgi);
            onlineUser.setInvitestatus(Enums.OnlineUserInviteStatus.INVITE.toString());
            onlineUser.setInvitetimes(onlineUser.getInvitetimes() + 1);
            onlineUserRes.save(onlineUser);

            InviteRecord record = new InviteRecord();
            record.setAgentno(super.getUser(request).getId());
            // ??????OnlineUser, ???userId???id????????????
            record.setUserid(onlineUser.getUserid());
            record.setAppid(onlineUser.getAppid());
            record.setOrgi(super.getOrgi(request));
            inviteRecordRes.save(record);
            logger.info("[invite] new invite record {} of onlineUser id {} saved.", record.getId(), onlineUser.getId());

            try {
                onlineUserService.sendWebIMClients(onlineUser.getUserid(), "invite:" + agentno);
                resp.addProperty(RestUtils.RESP_KEY_RC, RestUtils.RESP_RC_SUCC);
            } catch (Exception e) {
                logger.error("[invite] error", e);
                resp.addProperty(RestUtils.RESP_KEY_RC, RestUtils.RESP_RC_FAIL_3);
                resp.addProperty(RestUtils.RESP_KEY_ERROR, "online user is offline.");
            }
        } else {
            // ?????????????????????????????????????????????????????????Onlineuser?????????????????????????????????
            logger.info("[invite] can not find onlineUser {} in database.", userid);
            resp.addProperty(RestUtils.RESP_KEY_RC, RestUtils.RESP_RC_FAIL_4);
            resp.addProperty(RestUtils.RESP_KEY_ERROR, "online user is invalid, not found in db or cache.");
        }

        return resp;
    }
}
