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

package com.chatopera.cc.controller.apps;

import com.github.xiaobo9.commons.enums.Enums;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.entity.Quality;
import com.github.xiaobo9.bean.QualityRequest;
import com.github.xiaobo9.entity.Tag;
import com.github.xiaobo9.repository.QualityRepository;
import com.github.xiaobo9.repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/apps/quality")
public class AgentQualityController extends Handler {

    @Autowired
    private QualityRepository qualityRes;

    @Autowired
    private TagRepository tagRes;

    @RequestMapping(value = "/index")
    @Menu(type = "agent", subtype = "quality", access = false)
    public ModelAndView index(ModelMap map, HttpServletRequest request) {
        // TODO: mdx-organ clean
//        map.addAttribute("sessionConfig", acdPolicyService.initSessionConfig(super.getOrgi(request)));
        map.addAttribute("qualityList", qualityRes.findByQualitytypeAndOrgi(Enums.QualityType.CHAT.toString(), super.getOrgi(request)));
        map.addAttribute("tagList", tagRes.findByOrgiAndTagtype(super.getOrgi(request), Enums.TagType.QUALITY.toString()));
        return request(super.createAppsTempletResponse("/apps/quality/index"));
    }


    @RequestMapping(value = "/save")
    @Menu(type = "agent", subtype = "quality", access = false)
    public ModelAndView save(ModelMap map, HttpServletRequest request, @Valid QualityRequest qualityArray) {
        String orgi = super.getOrgi(request);

        if (qualityArray != null && qualityArray.getTitle() != null) {
            List<Quality> qualityList = qualityRes.findByQualitytypeAndOrgi(Enums.QualityType.CHAT.toString(), super.getOrgi(request));
            qualityRes.deleteAll(qualityList);
            List<Quality> tempList = new ArrayList<>();
            for (int i = 0; i < qualityArray.getTitle().length; i++) {
                Quality temp = new Quality();
                temp.setName(qualityArray.getTitle()[i]);
                if (qualityArray.getDescription().length == qualityArray.getTitle().length) {
                    temp.setDescription(qualityArray.getDescription()[i]);
                }
                if (qualityArray.getScore().length == qualityArray.getTitle().length) {
                    temp.setScore(qualityArray.getScore()[i]);
                }
                temp.setOrgi(super.getOrgi(request));
                temp.setQualitytype(Enums.QualityType.CHAT.toString());
                tempList.add(temp);
            }
            if (tempList.size() > 0) {
                qualityRes.saveAll(tempList);
            }

            // TODO: mdx-organ clean
//            SessionConfig config = acdPolicyService.initSessionConfig(super.getOrgi(request));
//            if (config != null) {
//                if ("points".equals(request.getParameter("qualityscore"))) {
//                    config.setQualityscore("points");
//                } else {
//                    config.setQualityscore("score");
//                }
//
//                sessionConfigRes.save(config);
//                cache.putSessionConfigByOrgi(config, orgi);
//                cache.deleteSessionConfigListByOrgi(orgi);
//            }
            if (qualityArray.getTag() != null && qualityArray.getTag().length > 0) {
                List<Tag> tagList = tagRes.findByOrgiAndTagtype(super.getOrgi(request), Enums.TagType.QUALITY.toString());
                if (tagList.size() > 0) {
                    tagRes.deleteAll(tagList);
                }
                List<Tag> tagTempList = new ArrayList<>();
                for (String tag : qualityArray.getTag()) {
                    Tag temp = new Tag();
                    temp.setOrgi(super.getOrgi(request));
                    temp.setCreater(super.getUser(request).getId());
                    temp.setTag(tag);
                    temp.setCreater(super.getOrgi(request));
                    temp.setTagtype(Enums.TagType.QUALITY.toString());
                    tagTempList.add(temp);
                }
                if (tagTempList.size() > 0) {
                    tagRes.saveAll(tagTempList);
                }
            }
        }
        return request(super.pageTplResponse("redirect:/apps/quality/index.html"));
    }
}
