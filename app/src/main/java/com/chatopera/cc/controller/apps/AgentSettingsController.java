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

import com.chatopera.cc.acd.ACDPolicyService;
import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import com.chatopera.cc.service.OrganService;
import com.chatopera.cc.util.Dict;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/setting")
public class AgentSettingsController extends Handler {

    @Autowired
    private ACDPolicyService acdPolicyService;

    @Autowired
    private SessionConfigRepository sessionConfigRes;

    @Autowired
    private TagRepository tagRes;

    @Autowired
    private BlackListRepository blackListRes;

    @Autowired
    private AdTypeRepository adTypeRes;

    @Autowired
    private TemplateRepository templateRes;

    @Autowired
    private OrganService organService;

    @Autowired
    private CacheService cacheService;

    @RequestMapping("/agent/index.html")
    @Menu(type = "setting", subtype = "sessionconfig")
    public ModelAndView index(ModelMap map, HttpServletRequest request) {
        SessionConfig sessionConfig = null;
        Organ currentOrgan = super.getOrgan(request);

        if (currentOrgan != null) {
            sessionConfig = sessionConfigRes.findByOrgiAndSkill(super.getOrgi(request), currentOrgan.getId());
        }

        if (sessionConfig == null) {
            sessionConfig = new SessionConfig();
        }
        map.put("sessionConfig", sessionConfig);


        List<SysDic> dicList = Dict.getInstance().getDic(Constants.CSKEFU_SYSTEM_DIC);
        SysDic inputDic = null, outputDic = null;
        for (SysDic dic : dicList) {
            if (dic.getCode().equals(Constants.CSKEFU_SYSTEM_AI_INPUT)) {
                inputDic = dic;
            }
            if (dic.getCode().equals(Constants.CSKEFU_SYSTEM_AI_OUTPUT)) {
                outputDic = dic;
            }
        }
        if (inputDic != null) {
            map.addAttribute("innputtemlet", templateRes.findByTemplettypeAndOrgi(inputDic.getId(), super.getOrgi(request)));
        }
        if (outputDic != null) {
            map.addAttribute("outputtemlet", templateRes.findByTemplettypeAndOrgi(outputDic.getId(), super.getOrgi(request)));
        }

        return request(super.createAppsTempletResponse("/apps/setting/agent/index"));
    }

    @RequestMapping("/agent/sessionconfig/save")
    @Menu(type = "setting", subtype = "sessionconfig")
    public ModelAndView sessionconfig(ModelMap map, HttpServletRequest request, @Valid SessionConfig sessionConfig) {
        Organ currentOrgan = super.getOrgan(request);
        String orgi = super.getOrgi(request);
        SessionConfig tempSessionConfig = sessionConfigRes.findByOrgiAndSkill(orgi, currentOrgan.getId());

        if (tempSessionConfig == null) {
            tempSessionConfig = sessionConfig;
            tempSessionConfig.setCreater(super.getUser(request).getId());
        } else {
            MainUtils.copyProperties(sessionConfig, tempSessionConfig);
        }
        tempSessionConfig.setOrgi(super.getOrgi(request));
        // 强制开启满意度问卷
        tempSessionConfig.setSatisfaction(true);
        tempSessionConfig.setSkill(currentOrgan.getId());

        sessionConfigRes.save(tempSessionConfig);

        cacheService.putSessionConfigByOrgi(tempSessionConfig, tempSessionConfig.getSkill(), orgi);
        cacheService.deleteSessionConfigListByOrgi(orgi);

        acdPolicyService.initSessionConfigList();
        map.put("sessionConfig", tempSessionConfig);

        return request(super.pageTplResponse("redirect:/setting/agent/index.html"));
    }

    @RequestMapping("/blacklist")
    @Menu(type = "setting", subtype = "blacklist")
    public ModelAndView blacklist(ModelMap map, HttpServletRequest request) {
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));

        Page<BlackEntity> blackList = blackListRes.findByOrgiAndSkillIn(super.getOrgi(request), organs.keySet(), super.page(request, Sort.Direction.DESC, "endtime"));

        map.put("blackList", blackList);
        map.put("tagTypeList", Dict.getInstance().getDic("com.dic.tag.type"));
        return request(super.createAppsTempletResponse("/apps/setting/agent/blacklist"));
    }

    @RequestMapping("/blacklist/delete")
    @Menu(type = "setting", subtype = "tag")
    public ModelAndView blacklistdelete(HttpServletRequest request, @Valid String id) {
        if (!StringUtils.isBlank(id)) {
            BlackEntity tempBlackEntity = blackListRes.findByIdAndOrgi(id, super.getOrgi(request));
            if (tempBlackEntity != null) {
                blackListRes.delete(tempBlackEntity);
                cacheService.deleteSystembyIdAndOrgi(tempBlackEntity.getUserid(), Constants.SYSTEM_ORGI);
            }
        }
        return request(super.pageTplResponse("redirect:/setting/blacklist.html"));
    }

    @RequestMapping("/tag")
    @Menu(type = "setting", subtype = "tag")
    public ModelAndView tag(ModelMap map, HttpServletRequest request, @Valid String code) {
        Organ currentOrgan = super.getOrgan(request);

        SysDic tagType = null;
        List<SysDic> tagList = Dict.getInstance().getDic("com.dic.tag.type");
        if (tagList.size() > 0) {

            if (!StringUtils.isBlank(code)) {
                for (SysDic dic : tagList) {
                    if (code.equals(dic.getCode())) {
                        tagType = dic;
                    }
                }
            } else {
                tagType = tagList.get(0);
            }
            map.put("tagType", tagType);
        }
        if (tagType != null && currentOrgan != null) {
            map.put("tagList", tagRes.findByOrgiAndTagtypeAndSkill(super.getOrgi(request), tagType.getCode(), currentOrgan.getId(), super.page(request)));
        }
        map.put("tagTypeList", tagList);
        return request(super.createAppsTempletResponse("/apps/setting/agent/tag"));
    }

    @RequestMapping("/tag/add")
    @Menu(type = "setting", subtype = "tag")
    public ModelAndView tagadd(ModelMap map, @Valid String tagtype) {
        map.addAttribute("tagtype", tagtype);
        return request(super.pageTplResponse("/apps/setting/agent/tagadd"));
    }

    @RequestMapping("/tag/edit")
    @Menu(type = "setting", subtype = "tag")
    public ModelAndView tagedit(ModelMap map, @Valid String id, @Valid String tagtype) {
        map.put("tag", tagRes.findById(id).orElseThrow(EntityNotFoundEx::new));
        map.addAttribute("tagtype", tagtype);
        return request(super.pageTplResponse("/apps/setting/agent/tagedit"));
    }

    @RequestMapping("/tag/update")
    @Menu(type = "setting", subtype = "tag")
    public ModelAndView tagupdate(HttpServletRequest request, @Valid Tag tag, @Valid String tagtype) {
        Organ currentOrgan = super.getOrgan(request);

        Tag temptag = tagRes.findByOrgiAndTagAndSkill(super.getOrgi(request), tag.getTag(), currentOrgan.getId());

        if (temptag == null || tag.getId().equals(temptag.getId())) {
            tag.setOrgi(super.getOrgi(request));
            tag.setCreater(super.getUser(request).getId());
            tag.setSkill(currentOrgan.getId());
            tagRes.save(tag);
        }
        return request(super.pageTplResponse("redirect:/setting/tag.html?code=" + tagtype));
    }

    @RequestMapping("/tag/save")
    @Menu(type = "setting", subtype = "tag")
    public ModelAndView tagsave(HttpServletRequest request, @Valid Tag tag, @Valid String tagtype) {
        Organ currentOrgan = super.getOrgan(request);

        if (currentOrgan != null && tagRes.findByOrgiAndTagAndSkill(super.getOrgi(request), tag.getTag(), currentOrgan.getId()) == null) {
            tag.setOrgi(super.getOrgi(request));
            tag.setCreater(super.getUser(request).getId());
            tag.setSkill(currentOrgan.getId());
            tagRes.save(tag);
        }
        return request(super.pageTplResponse("redirect:/setting/tag.html?code=" + tagtype));
    }

    @RequestMapping("/tag/delete")
    @Menu(type = "setting", subtype = "tag")
    public ModelAndView tagdelete(@Valid String id, @Valid String tagtype) {
        tagRes.deleteById(id);
        return request(super.pageTplResponse("redirect:/setting/tag.html?code=" + tagtype));
    }


    @RequestMapping("/acd")
    @Menu(type = "setting", subtype = "acd")
    public ModelAndView acd(ModelMap map) {
        map.put("tagTypeList", Dict.getInstance().getDic("com.dic.tag.type"));
        return request(super.createAppsTempletResponse("/apps/setting/agent/acd"));
    }


    @RequestMapping("/adv")
    @Menu(type = "setting", subtype = "adv")
    public ModelAndView adv(ModelMap map, HttpServletRequest request, @Valid String adpos) {
        Organ currentOrgan = super.getOrgan(request);

        SysDic advType = null;
        List<SysDic> tagList = Dict.getInstance().getDic("com.dic.adv.type");
        if (tagList.size() > 0) {
            if (!StringUtils.isBlank(adpos)) {
                for (SysDic dic : tagList) {
                    if (adpos.equals(dic.getId())) {
                        advType = dic;
                    }
                }
            } else {
                advType = tagList.get(0);
            }
            map.put("advType", advType);
        }
        if (currentOrgan != null && advType != null) {
            map.put("adTypeList", adTypeRes.findByAdposAndOrgiAndSkill(advType.getId(), super.getOrgi(request), currentOrgan.getId()));
        }

        map.put("tagTypeList", Dict.getInstance().getDic("com.dic.tag.type"));

        map.put("advTypeList", Dict.getInstance().getDic("com.dic.adv.type"));

        return request(super.createAppsTempletResponse("/apps/setting/agent/adv"));
    }

    @RequestMapping("/adv/add")
    @Menu(type = "setting", subtype = "adv")
    public ModelAndView advadd(ModelMap map, @Valid String adpos) {
        map.addAttribute("adpos", adpos);
        return request(super.pageTplResponse("/apps/setting/agent/adadd"));
    }

    @RequestMapping("/adv/save")
    @Menu(type = "setting", subtype = "adv", admin = false)
    public ModelAndView advsave(HttpServletRequest request, @Valid AdType adv, @RequestParam(value = "imgfile", required = false) MultipartFile imgfile) throws IOException {
        Organ currentOrgan = super.getOrgan(request);
        if (currentOrgan != null) {
            adv.setSkill(currentOrgan.getId());
        }

        adv.setOrgi(super.getOrgi(request));
        adv.setCreater(super.getUser(request).getId());
        if (StringUtils.isNotBlank(adv.getContent())) {
            adv.setContent(adv.getContent().replaceAll("\"", "'"));
        }
        adv.setCreatetime(new Date());
        if (imgfile != null && imgfile.getSize() > 0) {
            adv.setImgurl("/res/image.html?id=" + super.saveImageFileWithMultipart(imgfile));
        }
        adTypeRes.save(adv);

        MainUtils.initAdv(super.getOrgi(request), adv.getSkill());

        return request(super.pageTplResponse("redirect:/setting/adv.html?adpos=" + adv.getAdpos()));
    }

    @RequestMapping("/adv/edit")
    @Menu(type = "setting", subtype = "adv", admin = false)
    public ModelAndView advedit(ModelMap map, HttpServletRequest request, @Valid String adpos, @Valid String id) {
        map.addAttribute("adpos", adpos);
        map.put("ad", adTypeRes.findByIdAndOrgi(id, super.getOrgi(request)));
        return request(super.pageTplResponse("/apps/setting/agent/adedit"));
    }

    @RequestMapping("/adv/update")
    @Menu(type = "setting", subtype = "adv", admin = false)
    public ModelAndView advupdate(HttpServletRequest request, @Valid AdType ad, @Valid String adpos, @RequestParam(value = "imgfile", required = false) MultipartFile imgfile) throws IOException {
        String orgi = super.getOrgi(request);
        Organ currentOrgan = super.getOrgan(request);
        AdType tempad = null;
        if (currentOrgan != null) {
            tempad = adTypeRes.findByIdAndOrgiAndSkill(ad.getId(), super.getOrgi(request), currentOrgan.getId());
        }

        if (tempad != null) {
            ad.setOrgi(super.getOrgi(request));
            ad.setCreater(tempad.getCreater());
            ad.setCreatetime(tempad.getCreatetime());
            if (StringUtils.isNotBlank(ad.getContent())) {
                ad.setContent(ad.getContent().replaceAll("\"", "'"));
            }
            if (imgfile != null && imgfile.getSize() > 0) {
                ad.setImgurl("/res/image.html?id=" + super.saveImageFileWithMultipart(imgfile));
            } else {
                ad.setImgurl(tempad.getImgurl());
            }
            ad.setSkill(currentOrgan.getId());
            adTypeRes.save(ad);
            MainUtils.initAdv(orgi, tempad.getSkill());
        }
        return request(super.pageTplResponse("redirect:/setting/adv.html?adpos=" + adpos));
    }

    @RequestMapping("/adv/delete")
    @Menu(type = "setting", subtype = "adv", admin = false)
    public ModelAndView advdelete(HttpServletRequest request, @Valid String id, @Valid String adpos) {
        String orgi = super.getOrgi(request);
        AdType adType = adTypeRes.findByIdAndOrgi(id, orgi);
        if (adType != null) {
            adTypeRes.deleteById(id);
            MainUtils.initAdv(orgi, adType.getSkill());
        }
        return request(super.pageTplResponse("redirect:/setting/adv.html?adpos=" + adpos));
    }
}