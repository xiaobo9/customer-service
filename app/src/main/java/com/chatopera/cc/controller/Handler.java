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

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.Viewport;
import com.chatopera.cc.basic.auth.AuthToken;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.api.QueryParams;
import com.chatopera.cc.persistence.blob.JpaBlobHelper;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.commons.utils.Base62Utils;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.github.xiaobo9.entity.Organ;
import com.github.xiaobo9.entity.StreamingFile;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.repository.StreamingFileRepository;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.elasticsearch.index.query.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;


@Controller
public class Handler {

    @Autowired
    private JpaBlobHelper jpaBlobHelper;

    @Autowired
    private StreamingFileRepository streamingFileRes;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AuthToken authToken;

    @Value("${enable.user.filter:false}")
    private boolean enableUserFilter;

    public final static int PAGE_SIZE_BG = 1;
    public final static int PAGE_SIZE_TW = 20;
    public final static int PAGE_SIZE_FV = 50;
    public final static int PAGE_SIZE_HA = 100;

    public User getUser(HttpServletRequest request) {
        User user = (User) request.getSession(true).getAttribute(Constants.USER_SESSION_NAME);
        if (user != null) {
            user.setSessionid(UUIDUtils.removeHyphen(request.getSession().getId()));
            return user;
        }
        String authorization = request.getHeader("authorization");
        if (StringUtils.isBlank(authorization) && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("authorization")) {
                    authorization = cookie.getValue();
                    break;
                }
            }
        }
        if (StringUtils.isNotBlank(authorization)) {
            user = authToken.findUserByAuth(authorization);
        }
        if (user == null) {
            user = newGuestUser(request);
        }
        return user;
    }

    protected User newGuestUser(HttpServletRequest request) {
        User user;
        user = new User();
        user.setId(UUIDUtils.removeHyphen(request.getSession().getId()));
        user.setUsername(Constants.GUEST_USER + "_" + Base62Utils.genIDByKey(user.getId()));
        user.setOrgi(Constants.SYSTEM_ORGI);
        user.setSessionid(user.getId());
        return user;
    }

    /**
     * 获得登录账号的当前导航的组织机构
     */
    public Organ getOrgan(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Organ organ = (Organ) session.getAttribute(Constants.ORGAN_SESSION_NAME);
        if (organ != null) {
            return organ;
        }
        User user = getUser(request);
        if (user.getOrgans() == null) {
            return null;
        }
        List<Organ> organs = new ArrayList<>(user.getOrgans().values());
        if (organs.size() == 0) {
            return null;
        }
        organ = organs.get(0);
        session.setAttribute(Constants.ORGAN_SESSION_NAME, organ);
        return organ;
    }

    /**
     * 构建ElasticSearch基于部门查询的Filter
     */
    public boolean esOrganFilter(User user, final BoolQueryBuilder boolQueryBuilder) {
        if (user.isAdmin()) {
            // 管理员, 查看任何数据
            return true;
        }
        if (enableUserFilter) {
            // 用户在部门中，通过部门过滤数据
            // TODO 不对contacts进行过滤，普通用户也可以查看该租户的任何数据
            String[] values = user.getAffiliates().toArray(new String[0]);
            boolQueryBuilder.filter(termsQuery("organ", values));
        }
        return true;
    }

    /**
     * @param queryBuilder
     * @param request
     */
    public BoolQueryBuilder search(BoolQueryBuilder queryBuilder, ModelMap map, HttpServletRequest request) {
        queryBuilder.must(termQuery("orgi", this.getOrgi(request)));

        // 搜索框
        if (StringUtils.isNotBlank(request.getParameter("q"))) {
            String q = request.getParameter("q");
            q = q.replaceAll("(OR|AND|NOT|:|\\(|\\))", "");
            if (StringUtils.isNotBlank(q)) {
                queryBuilder.must(
                        QueryBuilders.boolQuery().must(new QueryStringQueryBuilder(q).defaultOperator(Operator.AND)));
                map.put("q", q);
            }
        }

        // 筛选表单
        if (StringUtils.isNotBlank(request.getParameter("filterid"))) {
            queryBuilder.must(termQuery("filterid", request.getParameter("filterid")));
            map.put("filterid", request.getParameter("filterid"));
        }

        // 批次
        if (StringUtils.isNotBlank(request.getParameter("batid"))) {
            queryBuilder.must(termQuery("batid", request.getParameter("batid")));
            map.put("batid", request.getParameter("batid"));
        }

        // 活动
        if (StringUtils.isNotBlank(request.getParameter("actid"))) {
            queryBuilder.must(termQuery("actid", request.getParameter("actid")));
            map.put("actid", request.getParameter("actid"));
        }

        // 业务状态
        if (StringUtils.isNotBlank(request.getParameter("workstatus"))) {
            queryBuilder.must(termQuery("workstatus", request.getParameter("workstatus")));
            map.put("workstatus", request.getParameter("workstatus"));
        }

        // 拨打状态
        if (StringUtils.isNotBlank(request.getParameter("callstatus"))) {
            queryBuilder.must(termQuery("callstatus", request.getParameter("callstatus")));
            map.put("callstatus", request.getParameter("callstatus"));
        }

        // 预约状态
        if (StringUtils.isNotBlank(request.getParameter("apstatus"))) {
            queryBuilder.must(termQuery("apstatus", request.getParameter("apstatus")));
            map.put("apstatus", request.getParameter("apstatus"));
        }

        RangeQueryBuilder rangeQuery = null;
        // 拨打时间区间查询
        if (StringUtils.isNotBlank(request.getParameter("callbegin")) || StringUtils.isNotBlank(
                request.getParameter("callend"))) {

            if (StringUtils.isNotBlank(request.getParameter("callbegin"))) {
                try {

                    rangeQuery = QueryBuilders.rangeQuery("calltime").from(
                            DateFormatEnum.DAY_TIME.parse(request.getParameter("callbegin")).getTime());
                } catch (ParseException e) {

                    e.printStackTrace();
                }
            }
            if (StringUtils.isNotBlank(request.getParameter("callend"))) {

                try {

                    if (rangeQuery == null) {
                        rangeQuery = QueryBuilders.rangeQuery("calltime").to(
                                DateFormatEnum.DAY_TIME.parse(request.getParameter("callend")).getTime());
                    } else {
                        rangeQuery.to(DateFormatEnum.DAY_TIME.parse(request.getParameter("callend")).getTime());
                    }
                } catch (ParseException e) {

                    e.printStackTrace();
                }

            }
            map.put("callbegin", request.getParameter("callbegin"));
            map.put("callend", request.getParameter("callend"));
        }
        // 预约时间区间查询
        if (StringUtils.isNotBlank(request.getParameter("apbegin")) || StringUtils.isNotBlank(
                request.getParameter("apend"))) {

            if (StringUtils.isNotBlank(request.getParameter("apbegin"))) {
                try {

                    rangeQuery = QueryBuilders.rangeQuery("aptime").from(
                            DateFormatEnum.DAY_TIME.parse(request.getParameter("apbegin")).getTime());
                } catch (ParseException e) {

                    e.printStackTrace();
                }
            }
            if (StringUtils.isNotBlank(request.getParameter("apend"))) {

                try {

                    if (rangeQuery == null) {
                        rangeQuery = QueryBuilders.rangeQuery("aptime").to(
                                DateFormatEnum.DAY_TIME.parse(request.getParameter("apend")).getTime());
                    } else {
                        rangeQuery.to(DateFormatEnum.DAY_TIME.parse(request.getParameter("apend")).getTime());
                    }
                } catch (ParseException e) {

                    e.printStackTrace();
                }


            }
            map.put("apbegin", request.getParameter("apbegin"));
            map.put("apend", request.getParameter("apend"));
        }

        if (rangeQuery != null) {
            queryBuilder.must(rangeQuery);
        }

        // 外呼任务id
        if (StringUtils.isNotBlank(request.getParameter("taskid"))) {
            queryBuilder.must(termQuery("taskid", request.getParameter("taskid")));
            map.put("taskid", request.getParameter("taskid"));
        }
        // 坐席
        if (StringUtils.isNotBlank(request.getParameter("owneruser"))) {
            queryBuilder.must(termQuery("owneruser", request.getParameter("owneruser")));
            map.put("owneruser", request.getParameter("owneruser"));
        }
        // 部门
        if (StringUtils.isNotBlank(request.getParameter("ownerdept"))) {
            queryBuilder.must(termQuery("ownerdept", request.getParameter("ownerdept")));
            map.put("ownerdept", request.getParameter("ownerdept"));
        }
        // 分配状态
        if (StringUtils.isNotBlank(request.getParameter("status"))) {
            queryBuilder.must(termQuery("status", request.getParameter("status")));
            map.put("status", request.getParameter("status"));
        }

        return queryBuilder;
    }

    /**
     * 创建或从HTTP会话中查找到访客的User对象，该对象不在数据库中，属于临时会话。
     * 这个User很可能是打开一个WebIM访客聊天控件，随机生成用户名，之后和Contact关联
     * 这个用户可能关联一个OnlineUser，如果开始给TA分配坐席
     *
     * @param request
     * @param userid
     * @param nickname
     * @return
     */
    public User getIMUser(HttpServletRequest request, String userid, String nickname) {
        User user = (User) request.getSession(true).getAttribute(Constants.IM_USER_SESSION_NAME);
        if (user == null) {
            user = new User();
            if (StringUtils.isNotBlank(userid)) {
                user.setId(userid);
            } else {
                user.setId(UUIDUtils.removeHyphen(request.getSession().getId()));
            }
            if (StringUtils.isNotBlank(nickname)) {
                user.setUsername(nickname);
            } else {
                Map<String, String> sessionMessage = cacheService.findOneSystemMapByIdAndOrgi(
                        request.getSession().getId(), Constants.SYSTEM_ORGI);
                if (sessionMessage != null) {
                    String struname = sessionMessage.get("username");
                    String strcname = sessionMessage.get("company_name");

                    user.setUsername(struname + "@" + strcname);
                } else {
                    user.setUsername(Constants.GUEST_USER + "_" + Base62Utils.genIDByKey(user.getId()));
                }
            }
            user.setSessionid(user.getId());
        } else {
            user.setSessionid(UUIDUtils.removeHyphen(request.getSession().getId()));
        }
        return user;
    }

    public User getIMUser(HttpServletRequest request, String userid, String nickname, String sessionid) {
        User user = (User) request.getSession(true).getAttribute(Constants.IM_USER_SESSION_NAME);
        if (user == null) {
            user = new User();
            if (StringUtils.isNotBlank(userid)) {
                user.setId(userid);
            } else {
                user.setId(UUIDUtils.removeHyphen(request.getSession().getId()));
            }
            if (StringUtils.isNotBlank(nickname)) {
                user.setUsername(nickname);
            } else {
                Map<String, String> sessionMessage = cacheService.findOneSystemMapByIdAndOrgi(
                        sessionid, Constants.SYSTEM_ORGI);
                if (sessionMessage != null) {
                    String struname = sessionMessage.get("username");
                    String strcname = sessionMessage.get("company_name");

                    user.setUsername(struname + "@" + strcname);
                } else {
                    user.setUsername(Constants.GUEST_USER + "_" + Base62Utils.genIDByKey(user.getId()));
                }
            }
            user.setSessionid(user.getId());
        } else {
            user.setSessionid(UUIDUtils.removeHyphen(request.getSession().getId()));
        }
        return user;
    }

    public void setUser(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(true);
        session.removeAttribute(Constants.USER_SESSION_NAME);
        session.setAttribute(Constants.USER_SESSION_NAME, user);
    }


    /**
     * 创建系统监控的 模板页面
     *
     * @param page
     * @return
     */
    public Viewport createAdminTemplateResponse(String page) {
        return new Viewport("/admin/include/tpl", page);
    }

    /**
     * 创建系统监控的 模板页面
     *
     * @param page
     * @return
     */
    public Viewport createAppsTempletResponse(String page) {
        return new Viewport("/apps/include/tpl", page);
    }

    /**
     * 创建系统监控的 模板页面
     *
     * @param page
     * @return
     */
    public Viewport createEntIMTempletResponse(final String page) {
        return new Viewport("/apps/entim/include/tpl", page);
    }

    public Viewport pageTplResponse(final String page) {
        return new Viewport(page);
    }

    /**
     * @param data
     * @return
     */
    public ModelAndView request(Viewport data) {
        String viewName = data.getTemplet() != null ? data.getTemplet() : data.getPage();
        return new ModelAndView(viewName, "data", data);
    }

    protected PageRequest page(HttpServletRequest request) {
        return PageRequest.of(getP(request), getPs(request));
    }

    protected PageRequest page(HttpServletRequest request, Sort.Direction direction, String... properties) {
        return PageRequest.of(getP(request), getPs(request), direction, properties);
    }

    protected PageRequest page(QueryParams params, Sort.Direction direction, String... properties) {
        return PageRequest.of(getP(params), getPs(params), direction, properties);
    }

    public int getP(HttpServletRequest request) {
        String p = request.getParameter("p");
        int page = NumberUtils.toInt(p, 0);
        if (page > 0) {
            page = page - 1;
        }
        return page;
    }

    public int getPs(HttpServletRequest request) {
        String ps = request.getParameter("ps");
        return NumberUtils.toInt(ps, PAGE_SIZE_TW);
    }

    public int getP(QueryParams params) {
        if (params == null) {
            return 0;
        }
        String p = params.getP();
        int page = NumberUtils.toInt(p, 0);
        if (page > 0) {
            page = page - 1;
        }
        return page;
    }

    public int getPs(QueryParams params) {
        if (params == null) {
            return PAGE_SIZE_TW;
        }
        String ps = params.getPs();
        return NumberUtils.toInt(ps, PAGE_SIZE_TW);
    }

    public int get50Ps(HttpServletRequest request) {
        int pagesize = PAGE_SIZE_FV;
        String ps = request.getParameter("ps");
        if (StringUtils.isNotBlank(ps) && ps.matches("[\\d]*")) {
            pagesize = Integer.parseInt(ps);
        }
        return pagesize;
    }

    public String getOrgi() {
        return Constants.SYSTEM_ORGI;
    }

    // FIXME: 保存此处是为了兼容之前到代码，宜去掉
    public String getOrgi(HttpServletRequest request) {
        return getOrgi();
    }

    /**
     * 使用Blob保存文件
     *
     * @param multipart
     * @return id
     * @throws IOException
     */
    public String saveImageFileWithMultipart(MultipartFile multipart) throws IOException {
        StreamingFile sf = new StreamingFile();
        final String fileid = UUIDUtils.getUUID();
        sf.setId(fileid);
        sf.setMime(multipart.getContentType());
        sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
        sf.setName(multipart.getOriginalFilename());
        streamingFileRes.save(sf);
        return fileid;
    }

}
