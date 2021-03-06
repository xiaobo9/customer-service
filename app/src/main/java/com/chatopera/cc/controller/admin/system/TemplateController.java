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
package com.chatopera.cc.controller.admin.system;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.util.Dict;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.commons.kit.AttachFileKit;
import com.github.xiaobo9.commons.kit.ObjectKit;
import com.github.xiaobo9.entity.SysDic;
import com.github.xiaobo9.entity.Template;
import com.github.xiaobo9.repository.SysDicRepository;
import com.github.xiaobo9.repository.TemplateRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/admin/template")
public class TemplateController extends Handler {

    @Autowired
    private TemplateRepository templateRes;

    @Autowired
    private SysDicRepository dicRes;

    @Autowired
    private CacheService cacheService;

    @RequestMapping("/index")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView index(ModelMap map) {
        map.addAttribute("sysDicList", Dict.getInstance().getDic(Constants.CSKEFU_SYSTEM_DIC));
        return request(super.createAdminTemplateResponse("/admin/system/template/index"));
    }

    @RequestMapping("/expall")
    @Menu(type = "admin", subtype = "template", admin = true)
    public void expall(HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<Template> templateList = templateRes.findByOrgi(super.getOrgi(request));
        response.setHeader(AttachFileKit.HEADER_KEY, AttachFileKit.nameWithDayAnd("Template-Export", ".data"));
        response.getOutputStream().write(ObjectKit.toBytes(templateList));
    }

    @RequestMapping("/imp")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView imp() {
        return request(super.pageTplResponse("/admin/system/template/imp"));
    }

    @SuppressWarnings("unchecked")
    @RequestMapping("/impsave")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView impsave(@RequestParam(value = "dataFile", required = false) MultipartFile dataFile) throws Exception {
        if (dataFile != null && dataFile.getSize() > 0) {
            List<Template> templateList = (List<Template>) ObjectKit.toObject(dataFile.getBytes());
            if (templateList != null && templateList.size() > 0) {
                templateRes.deleteInBatch(templateList);
                templateRes.saveAll(templateList);
            }
        }
        return request(super.pageTplResponse("redirect:/admin/template/index.html"));
    }

    @RequestMapping("/list")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView list(ModelMap map, HttpServletRequest request, @Valid String type) {
        map.addAttribute("sysDic", dicRes.findById(type).orElseThrow(() -> new RuntimeException("not found")));
        map.addAttribute("templateList", templateRes.findByTemplettypeAndOrgi(type, super.getOrgi(request)));
        return request(super.createAdminTemplateResponse("/admin/system/template/list"));
    }

    @RequestMapping("/add")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView add(ModelMap map, @Valid String type) {
        map.addAttribute("sysDic", dicRes.findById(type).orElseThrow(() -> new RuntimeException("not found")));
        return request(super.pageTplResponse("/admin/system/template/add"));
    }

    @RequestMapping("/save")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView save(HttpServletRequest request, @Valid Template template) {
        template.setOrgi(super.getOrgi(request));
        template.setCreatetime(new Date());

        SysDic dic = dicRes.findById(template.getTemplettype()).orElse(null);
        if (dic != null && StringUtils.isBlank(template.getCode())) {
            template.setCode(dic.getCode());
        }
        templateRes.save(template);

        return request(super.pageTplResponse("redirect:/admin/template/list.html?type=" + template.getTemplettype()));
    }

    @RequestMapping("/edit")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView edit(ModelMap map, HttpServletRequest request, @Valid String id, @Valid String type) {
        map.addAttribute("sysDic", dicRes.findById(type).orElseThrow(() -> new RuntimeException("not found")));
        map.addAttribute("template", templateRes.findByIdAndOrgi(id, super.getOrgi(request)));
        return request(super.pageTplResponse("/admin/system/template/edit"));
    }

    @RequestMapping("/update")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView update(HttpServletRequest request, @Valid Template template) {
        Template oldTemplate = templateRes.findByIdAndOrgi(template.getId(), super.getOrgi(request));
        if (oldTemplate != null) {
            dicRes.findById(oldTemplate.getTemplettype())
                    .ifPresent(dic -> oldTemplate.setCode(dic.getCode()));
            if (StringUtils.isNotBlank(template.getCode())) {
                oldTemplate.setCode(template.getCode());
            }
            oldTemplate.setName(template.getName());
            oldTemplate.setLayoutcols(template.getLayoutcols());
            oldTemplate.setIconstr(template.getIconstr());
            oldTemplate.setDatatype(template.getDatatype());
            oldTemplate.setCharttype(template.getCharttype());
            templateRes.save(oldTemplate);

            cacheService.deleteSystembyIdAndOrgi(template.getId(), super.getOrgi(request));
        }
        return request(super.pageTplResponse("redirect:/admin/template/list.html?type=" + template.getTemplettype()));
    }

    @RequestMapping("/code")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView code(ModelMap map, HttpServletRequest request, @Valid String id, @Valid String type) {
        map.addAttribute("sysDic", dicRes.findById(type).orElseThrow(() -> new RuntimeException("not found")));
        map.addAttribute("template", templateRes.findByIdAndOrgi(id, super.getOrgi(request)));
        return request(super.pageTplResponse("/admin/system/template/code"));
    }

    @RequestMapping("/codesave")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView codesave(HttpServletRequest request, @Valid Template template) {
        Template oldTemplate = templateRes.findByIdAndOrgi(template.getId(), super.getOrgi(request));
        if (oldTemplate != null) {
            oldTemplate.setTemplettext(template.getTemplettext());
            oldTemplate.setTemplettitle(template.getTemplettitle());
            templateRes.save(oldTemplate);

            cacheService.deleteSystembyIdAndOrgi(template.getId(), super.getOrgi(request));
        }
        return request(super.pageTplResponse("redirect:/admin/template/list.html?type=" + template.getTemplettype()));
    }

    @RequestMapping("/delete")
    @Menu(type = "admin", subtype = "template", admin = true)
    public ModelAndView delete(HttpServletRequest request, @Valid Template template) {
        if (template == null) {
            throw new RuntimeException("缺少参数");
        }
        templateRes.delete(template);

        cacheService.deleteSystembyIdAndOrgi(template.getId(), super.getOrgi(request));
        return request(super.pageTplResponse("redirect:/admin/template/list.html?type=" + template.getTemplettype()));
    }

}