package com.chatopera.cc.controller.api.tokens;

import com.chatopera.cc.basic.auth.AuthToken;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.RestResult;
import com.chatopera.cc.util.RestResultType;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.entity.UserRole;
import com.github.xiaobo9.repository.UserRepository;
import com.github.xiaobo9.repository.UserRoleRepository;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Date;
import java.util.List;

/**
 * 账号密码登录
 */
@RestController
@RequestMapping("/tokens")
public class TokensController extends Handler {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRes;

    @Autowired
    private AuthToken authToken;

    /**
     * 登录服务，传入登录账号和密码
     */
    @RequestMapping(method = RequestMethod.POST)
    @Menu(type = "apps", subtype = "token", access = true)
    public ResponseEntity<String> login(HttpServletResponse response, @Valid String username, @Valid String password) {
        User loginUser = userRepository.findByUsernameAndPassword(username, MD5Utils.md5(password));
        if (loginUser == null || StringUtils.isBlank(loginUser.getId())) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        loginUser.setLogin(true);
        List<UserRole> userRoleList = userRoleRes.findByOrgiAndUser(loginUser.getOrgi(), loginUser);
        if (userRoleList != null && userRoleList.size() > 0) {
            for (UserRole userRole : userRoleList) {
                loginUser.getRoleList().add(userRole.getRole());
            }
        }
        loginUser.setLastlogintime(new Date());
        if (!StringUtils.isBlank(loginUser.getId())) {
            userRepository.save(loginUser);
        }
        String auth = UUIDUtils.getUUID();
        authToken.putUserByAuth(auth, loginUser);

        response.addCookie(new Cookie("authorization", auth));
        return new ResponseEntity<>(auth, HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.GET)
    @Menu(type = "apps", subtype = "token", access = true)
    public ResponseEntity<User> getLogin(HttpServletRequest request) {
        User data = super.getUser(request);
        return new ResponseEntity<>(data, data != null ? HttpStatus.OK : HttpStatus.UNAUTHORIZED);
    }

    @RequestMapping(method = RequestMethod.DELETE)
    public ResponseEntity<Void> logout(HttpServletRequest request, @RequestHeader(value = "authorization") String authorization) {
        authToken.deleteUserByAuth(authorization);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Token验证失败
     */
    @RequestMapping(value = "error", method = RequestMethod.GET)
    @Menu(type = "apps", subtype = "token", access = true)
    public ResponseEntity<RestResult> error() {
        return new ResponseEntity<>(new RestResult(RestResultType.AUTH_ERROR), HttpStatus.OK);
    }
}
