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
package com.chatopera.cc.controller.apps.report;

import com.chatopera.cc.controller.Handler;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import com.chatopera.cc.util.Menu;
import com.github.xiaobo9.entity.CubeMeasure;
import com.github.xiaobo9.entity.CubeMetadata;
import com.github.xiaobo9.repository.CubeMeasureRepository;
import com.github.xiaobo9.repository.CubeMetadataRepository;
import com.github.xiaobo9.repository.TablePropertiesRepository;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/apps/report/cubemeasure")
public class CubeMeasureController extends Handler {

    @Autowired
    private CubeMeasureRepository cubeMeasureRes;

    @Autowired
    private TablePropertiesRepository tablePropertiesRes;

    @Autowired
    private CubeMetadataRepository cubeMetadataRes;

    @RequestMapping("/add")
    @Menu(type = "report", subtype = "cubemeasure")
    public ModelAndView cubeMeasureadd(ModelMap map, @Valid String cubeid) {
        map.addAttribute("cubeid", cubeid);
        List<CubeMetadata> cmList = cubeMetadataRes.findByCubeidAndMtype(cubeid, "0");
        if (!cmList.isEmpty() && cmList.get(0) != null) {
            map.put("fktableidList", tablePropertiesRes.findByDbtableid(cmList.get(0).getTb().getId()));
            map.put("table", cmList.get(0).getTb());
        }
        return request(super.pageTplResponse("/apps/business/report/cube/cubemeasure/add"));
    }

    @RequestMapping("/save")
    @Menu(type = "report", subtype = "cubemeasure")
    public ModelAndView cubeMeasuresave(HttpServletRequest request, @Valid CubeMeasure cubeMeasure) {
        if (!StringUtils.isBlank(cubeMeasure.getName())) {
            cubeMeasure.setOrgi(super.getOrgi(request));
            cubeMeasure.setCreater(super.getUser(request).getId());
            cubeMeasure.setCode(cubeMeasure.getColumname());
            cubeMeasureRes.save(cubeMeasure);
        }
        return request(super.pageTplResponse("redirect:/apps/report/cube/detail.html?dimensionId=cubemeasure&id=" + cubeMeasure.getCubeid()));
    }

    @RequestMapping("/delete")
    @Menu(type = "report", subtype = "cubemeasure")
    public ModelAndView quickreplydelete(@Valid String id) {
        CubeMeasure cubeMeasure = cubeMeasureRes.findById(id).orElseThrow(EntityNotFoundEx::new);
        cubeMeasureRes.delete(cubeMeasure);
        return request(super.pageTplResponse("redirect:/apps/report/cube/detail.html?dimensionId=cubemeasure&id=" + cubeMeasure.getCubeid()));
    }

    @RequestMapping("/edit")
    @Menu(type = "report", subtype = "cubemeasure", admin = true)
    public ModelAndView quickreplyedit(ModelMap map, @Valid String id) {
        CubeMeasure cubeMeasure = cubeMeasureRes.findById(id).orElse(null);
        map.put("cubemeasure", cubeMeasure);
        if (cubeMeasure != null) {
            List<CubeMetadata> cmList = cubeMetadataRes.findByCubeidAndMtype(cubeMeasure.getCubeid(), "0");
            if (!cmList.isEmpty() && cmList.get(0) != null) {
                map.put("fktableidList", tablePropertiesRes.findByDbtableid(cmList.get(0).getTb().getId()));
                map.put("table", cmList.get(0).getTb());
            }
        }
        return request(super.pageTplResponse("/apps/business/report/cube/cubemeasure/edit"));
    }

    @RequestMapping("/update")
    @Menu(type = "report", subtype = "cubemeasure", admin = true)
    public ModelAndView quickreplyupdate(HttpServletRequest request, @Valid CubeMeasure cubeMeasure) {
        if (!StringUtils.isBlank(cubeMeasure.getId())) {
            CubeMeasure temp = cubeMeasureRes.findById(cubeMeasure.getId()).orElse(null);
            cubeMeasure.setOrgi(super.getOrgi(request));
            cubeMeasure.setCreater(super.getUser(request).getId());
            if (temp != null) {
                cubeMeasure.setCreatetime(temp.getCreatetime());
            }
            cubeMeasure.setCode(cubeMeasure.getColumname());
            cubeMeasureRes.save(cubeMeasure);
        }
        return request(super.pageTplResponse("redirect:/apps/report/cube/detail.html?dimensionId=cubemeasure&id=" + cubeMeasure.getCubeid()));
    }

    @RequestMapping("/fktableid")
    @Menu(type = "report", subtype = "cubemeasure", admin = true)
    public ModelAndView fktableid(ModelMap map, @Valid String tableid) {
        if (!StringUtils.isBlank(tableid)) {
            map.put("fktableidList", tablePropertiesRes.findByDbtableid(tableid));
        }
        return request(super.pageTplResponse("/apps/business/report/cube/cubemeasure/fktableiddiv"));
    }
}