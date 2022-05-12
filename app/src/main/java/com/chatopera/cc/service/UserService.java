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
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.controller.api.request.RestUtils;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import com.google.gson.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户/坐席 常用方法
 */
@Service
public class UserService {
    private final static Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private OrganUserRepository organUserRes;

    @Autowired
    private OrganRepository organRes;

    @Autowired
    private UserRepository userRes;

    @Autowired
    private RoleAuthRepository roleAuthRes;

    @Autowired
    private PbxHostRepository pbxHostRes;

    @Autowired
    private ExtensionRepository extensionRes;

    public JsonObject createNewUser(final User user) {
        return this.createNewUser(user, null);
    }

    /**
     * 创建新用户
     * 支持多租户
     *
     * @param user
     * @param organ
     * @return
     */
    public JsonObject createNewUser(final User user, Organ organ) {
        JsonObject result = new JsonObject();
        String msg = validUser(user);
        if ("new_user_success".equalsIgnoreCase(msg)) {
            user.setSuperadmin(false); // 不支持创建第二个系统管理员
            user.setOrgi(Constants.SYSTEM_ORGI);

            if (StringUtils.isNotBlank(user.getPassword())) {
                user.setPassword(MD5Utils.md5(user.getPassword()));
            }
            userRes.save(user);

            if (organ != null) {
                OrganUser ou = new OrganUser();
                ou.setUserid(user.getId());
                ou.setOrgan(organ.getId());
                organUserRes.save(ou);
            }

        }
        // 新账号未通过验证，返回创建失败信息msg
        result.addProperty(RestUtils.RESP_KEY_RC, RestUtils.RESP_RC_SUCC);
        result.addProperty(RestUtils.RESP_KEY_DATA, msg);
        return result;
    }


    public User findById(final String id) {
        return userRes.findById(id).orElseThrow(() -> EntityNotFoundEx.of(User.class));
    }

    /**
     * 通过技能组查找技能组下坐席所有信息
     */
    @NotNull
    public List<User> findUserInOrgans(final Collection<String> organs) {
        return findUserIds(organs, ids -> userRes.findAllById(ids));
    }

    private List<String> findUserIdsInOrgan(final String organ) {
        return organUserRes.findByOrgan(organ).stream().map(OrganUser::getUserid).collect(Collectors.toList());
    }

    private Set<String> findUserIds(Collection<String> organs) {
        return organUserRes.findByOrganIn(organs).stream().map(OrganUser::getUserid).collect(Collectors.toSet());
    }

    private <T> List<T> findUserIds(Collection<String> organs, Function<Set<String>, List<T>> function) {
        Set<String> ids = findUserIds(organs);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return function.apply(ids);
    }

    public Page<User> findUserInOrgans(final Collection<String> organs, Pageable pageRequest) {
        Set<String> ids = findUserIds(organs);
        if (ids.isEmpty()) {
            return null;
        }
        return userRes.findByIdIn(ids, pageRequest);
    }

    /**
     * 通过坐席ID查找其技能组Map
     *
     * @param agentno
     * @return
     */
    public Map<String, String> getSkillsMapByAgentno(final String agentno) {

        final User user = userRes.findById(agentno).orElse(null);
        if (user == null) {
            return new HashMap<>();
        }

        attachOrgansPropertiesForUser(user);
        return user.getSkills();
    }

    public List<User> findByOrganInAndAgentAndDatastatus(final Collection<String> organs, boolean agent, boolean datastatus) {
        return findUserIds(organs, ids -> userRes.findByAgentAndDatastatusAndIdIn(agent, datastatus, ids));
    }

    public List<User> findByOrganInAndDatastatus(final Collection<String> organs, boolean datastatus) {
        return findUserIds(organs, ids -> userRes.findByDatastatusAndIdIn(datastatus, ids));
    }

    public List<User> findByOrganAndOrgiAndDatastatus(final String organ, final String orgi, final boolean datastatus) {
        List<String> users = findUserIdsInOrgan(organ);
        if (users.isEmpty()) {
            return null;
        }
        return userRes.findByOrgiAndDatastatusAndIdIn(orgi, datastatus, users);
    }

    /**
     * 检查用户更新是否合理
     *
     * @param user
     * @param oldUser
     * @return
     */
    public String validUserUpdate(final User user, final User oldUser) {
        String msg = "edit_user_success";
        User tempUser = userRes.findByUsernameAndDatastatus(user.getUsername(), false);

        if (!StringUtils.equals(user.getUsername(), oldUser.getUsername()) && tempUser != null && (!StringUtils.equals(
                oldUser.getId(), tempUser.getId()))) {
            // 用户名发生变更，并且数据库里有其它用户占用该用户名
            msg = "username_exist";
            return msg;
        }

        if (StringUtils.isNotBlank(user.getEmail())) {
            tempUser = userRes.findByEmailAndDatastatus(user.getEmail(), false);
            if (!StringUtils.equals(user.getEmail(), oldUser.getEmail()) && tempUser != null && (!StringUtils.equals(
                    oldUser.getId(), tempUser.getId()))) {
                msg = "email_exist";
                return msg;
            }
        }

        if (StringUtils.isNotBlank(user.getMobile())) {
            tempUser = userRes.findByMobileAndDatastatus(user.getMobile(), false);
            if (!StringUtils.equals(user.getMobile(), oldUser.getMobile()) && tempUser != null && (!StringUtils.equals(
                    oldUser.getId(), tempUser.getId()))) {
                msg = "mobile_exist";
                return msg;
            }
        }

        return msg;
    }

    /**
     * 从Json中创建User
     *
     * @param payload
     * @return
     */
    public User parseUserFromJson(final JsonObject payload) {
        User user = new User();

        // 手机号
        if (payload.has("id")) {
            String val = payload.get("id").getAsString();
            if (StringUtils.isNotBlank(val)) {
                user.setId(val);
            }
        }

        // 用户名，用于登录
        if (payload.has("username")) {
            String val = payload.get("username").getAsString();
            if (StringUtils.isNotBlank(val)) {
                user.setUsername(val);
            }
        }

        // 姓名
        if (payload.has("uname")) {
            String val = payload.get("uname").getAsString();
            if (StringUtils.isNotBlank(val)) {
                user.setUname(val);
            }
        }

        // 邮件
        if (payload.has("email")) {
            String val = payload.get("email").getAsString();
            if (StringUtils.isNotBlank(val)) {
                user.setEmail(val);
            }
        }

        // 手机号
        if (payload.has("mobile")) {
            String val = payload.get("mobile").getAsString();
            if (StringUtils.isNotBlank(val)) {
                user.setMobile(val);
            }
        }

        // 密码
        if (payload.has("password")) {
            String val = payload.get("password").getAsString();
            if (StringUtils.isNotBlank(val)) {
                user.setPassword(val);
            }
        }

        // 是否是坐席
        if (payload.has("agent")) {
            String val = payload.get("agent").getAsString();
            if (StringUtils.isNotBlank(val) && StringUtils.equals("1", val)) {
                user.setAgent(true);
            } else {
                user.setAgent(false);
            }
        } else {
            user.setAgent(false);
        }

        // 是否是管理员
        if (payload.has("admin")) {
            String val = payload.get("admin").getAsString();
            if (StringUtils.isNotBlank(val) && StringUtils.equals("1", val)) {
                // 管理员默认就是坐席
                user.setAdmin(true);
                user.setAgent(true);
            } else {
                user.setAdmin(false);
            }
        } else {
            user.setAdmin(false);
        }

        // 是否是呼叫中心
        if (payload.has("callcenter")) {
            if (StringUtils.equals(payload.get("callcenter").getAsString(), "1")) {
                user.setCallcenter(true);
                // 当为呼叫中心坐席时，同时提取pbxhostid和extensionid
                if (payload.has("pbxhostid")) {
                    user.setPbxhostId(payload.get("pbxhostid").getAsString());
                }

                if (payload.has("extensionid")) {
                    user.setExtensionId(payload.get("extensionid").getAsString());
                }
            } else {
                user.setCallcenter(false);
            }
        } else {
            user.setCallcenter(false);
        }

        // 不允许创建系统管理员
        user.setSuperadmin(false);

        return user;
    }

    /**
     * 验证用户数据合法性
     *
     * @param user
     * @return
     */
    public String validUser(final User user) {
        User exist = userRes.findByUsernameAndDatastatus(user.getUsername(), false);
        if (exist != null) {
            return "username_exist";
        }

        if (StringUtils.isNotBlank(user.getEmail())) {
            exist = userRes.findByEmailAndDatastatus(user.getEmail(), false);
            if (exist != null) {
                return "email_exist";
            }
        }

        if (StringUtils.isNotBlank(user.getMobile())) {
            exist = userRes.findByMobileAndDatastatus(user.getMobile(), false);
            if (exist != null) {
                return "mobile_exist";
            }
        }
        String msg = "new_user_success";
        // 检查作为呼叫中心坐席的信息
        if (MainContext.hasModule(Constants.CSKEFU_MODULE_CALLCENTER) && user.isCallcenter()) {
            final PbxHost pbxHost = pbxHostRes.findById(user.getPbxhostId()).orElse(null);
            if (pbxHost == null) {
                // 呼叫中心的语音平台不存在
                return "pbxhost_not_exist";
            }
            Extension extension = extensionRes.findById(user.getExtensionId()).orElse(null);
            if (extension == null) {
                // 该分机不存在
                return "extension_not_exist";
            }
            if (StringUtils.isNotBlank(extension.getAgentno())) {
                // 呼叫中心该分机已经绑定
                return "extension_binded";
            }
        }
        return msg;
    }

    /**
     * 增加用户的角色信息
     *
     * @param user
     */
    public void attachRolesMap(final User user) {
        // 获取用户的角色权限，进行授权
        List<RoleAuth> roleAuthList = roleAuthRes.findAll(new Specification<RoleAuth>() {
            @Override
            public Predicate toPredicate(Root<RoleAuth> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> criteria = new ArrayList<>();
                if (user.getRoleList() != null && user.getRoleList().size() > 0) {
                    for (Role role : user.getRoleList()) {
                        criteria.add(cb.equal(root.get("roleid").as(String.class), role.getId()));
                    }
                }
                Predicate[] p = new Predicate[criteria.size()];
                cb.and(cb.equal(root.get("orgi").as(String.class), user.getOrgi()));
                return cb.or(criteria.toArray(p));
            }
        });

        // clear previous auth map values, ensure the changes are token effect in real time.
        user.getRoleAuthMap().clear();
        if (roleAuthList != null) {
            for (RoleAuth roleAuth : roleAuthList) {
                user.getRoleAuthMap().put(roleAuth.getDicvalue(), true);
            }
        }
    }

    /**
     * 获取用户部门以及下级部门
     *
     * @param user
     */
    public void attachOrgansPropertiesForUser(final User user) {
        List<OrganUser> organUsers = organUserRes.findByUserid(user.getId());
        user.setOrgans(new HashMap<>());
        user.setAffiliates(new HashSet<>());

        final Map<String, String> skills = new HashMap<>();

        Set<String> organIds = organUsers.stream().map(OrganUser::getOrgan).collect(Collectors.toSet());
        List<Organ> organs = organRes.findAllById(organIds);
        for (final Organ organ : organs) {
            // 添加直属部门到organs
            user.getOrgans().put(organ.getId(), organ);
            if (organ.isSkill()) {
                skills.put(organ.getId(), organ.getName());
            }
            // 添加部门及附属部门
            processAffiliates(user, organ);
        }
        user.setSkills(skills);
    }


    /**
     * 获得一个部门及其子部门并添加到User的myorgans中
     *
     * @param user
     */
    private void processAffiliates(final User user, final Organ organ) {
        if (organ == null) {
            return;
        }

        if (user.inAffiliates(organ.getId())) {
            return;
        }

        user.getAffiliates().add(organ.getId());

        // 获得子部门
        List<Organ> y = organRes.findByOrgiAndParent(user.getOrgi(), organ.getId());

        for (Organ x : y) {
            try {
                // 递归调用
                processAffiliates(user, x);
            } catch (Exception e) {
                logger.error("processAffiliates", e);
            }
        }
    }

    public User getUserByUserNameAndPassword(String username, String password) {
        return userRes.findByUsernameAndPasswordAndDatastatus(username, MD5Utils.md5(password), false);
    }
}
