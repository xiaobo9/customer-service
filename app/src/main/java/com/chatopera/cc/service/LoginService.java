package com.chatopera.cc.service;

import com.chatopera.cc.acd.ACDWorkMonitor;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.basic.auth.AuthToken;
import com.chatopera.cc.proxy.AgentProxy;
import com.chatopera.cc.proxy.AgentSessionProxy;
import com.chatopera.cc.proxy.UserProxy;
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
    private AgentProxy agentProxy;

    @Autowired
    private AgentSessionProxy agentSessionProxy;

    @Autowired
    private UserProxy userProxy;

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
        agentSessionProxy.updateUserSession(loginUser.getId(), MainUtils.getContextID(sessionId), loginUser.getOrgi());
        loginUser.setSessionid(MainUtils.getContextID(sessionId));

        List<UserRole> userRoleList = userRoleRes.findByOrgiAndUser(loginUser.getOrgi(), loginUser);
        if (userRoleList != null && userRoleList.size() > 0) {
            for (UserRole userRole : userRoleList) {
                loginUser.getRoleList().add(userRole.getRole());
            }
        }

        // 获取用户部门以及下级部门
        userProxy.attachOrgansPropertiesForUser(loginUser);

        // 添加角色信息
        userProxy.attachRolesMap(loginUser);

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
