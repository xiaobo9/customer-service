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

package com.github.xiaobo9.service;

import com.chatopera.cc.acd.ACDWorkMonitor;
import com.chatopera.cc.basic.auth.AuthToken;
import com.chatopera.cc.service.AgentProxyService;
import com.chatopera.cc.service.AgentSessionService;
import com.chatopera.cc.service.UserService;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.entity.UserRole;
import com.github.xiaobo9.repository.UserRepository;
import com.github.xiaobo9.repository.UserRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class LoginService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRes;

    @Autowired
    private AuthToken authToken;

    @Autowired
    private AgentProxyService agentServiceService;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private UserService userService;

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    /**
     * 登录成功后的处理
     *
     * @param loginUser user
     * @param sessionId session id
     */
    public void processLogin(User loginUser, String sessionId) {
        // 设置登录用户的状态
        loginUser.setLogin(true);
        // 更新redis session信息，用以支持sso
        agentSessionService.updateUserSession(loginUser.getId(), UUIDUtils.removeHyphen(sessionId), loginUser.getOrgi());
        loginUser.setSessionid(UUIDUtils.removeHyphen(sessionId));

        List<UserRole> userRoleList = userRoleRes.findByOrgiAndUser(loginUser.getOrgi(), loginUser);
        if (userRoleList != null && userRoleList.size() > 0) {
            for (UserRole userRole : userRoleList) {
                loginUser.getRoleList().add(userRole.getRole());
            }
        }

        // 获取用户部门以及下级部门
        userService.attachOrgansPropertiesForUser(loginUser);

        // 添加角色信息
        userService.attachRolesMap(loginUser);

        loginUser.setLastlogintime(new Date());
        if (StringUtils.isNotBlank(loginUser.getId())) {
            userRepository.save(loginUser);
        }
    }

    public String validUser(User user) {
        User tempUser = userRepository.findByUsernameAndDatastatus(user.getUsername(), false);
        if (tempUser != null) {
            return "username_exist";
        }
        tempUser = userRepository.findByEmailAndDatastatus(user.getEmail(), false);
        if (tempUser != null) {
            return "email_exist";
        }
        tempUser = userRepository.findByMobileAndDatastatus(user.getMobile(), false);
        if (tempUser != null) {
            return "mobile_exist";
        }
        return "";
    }
}
