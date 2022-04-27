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
import com.github.xiaobo9.entity.CubeLevel;
import com.github.xiaobo9.entity.CubeMetadata;
import com.github.xiaobo9.entity.Dimension;
import com.github.xiaobo9.entity.TableProperties;
import com.github.xiaobo9.repository.CubeLevelRepository;
import com.github.xiaobo9.repository.CubeMetadataRepository;
import com.github.xiaobo9.repository.DimensionRepository;
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
@RequestMapping("/apps/report/cubelevel")
public class CubeLevelController extends Handler {

    @Autowired
    private CubeLevelRepository cubeLevelRes;

    @Autowired
    private DimensionRepository dimensionRes;

    @Autowired
    private TablePropertiesRepository tablePropertiesRes;

    @Autowired
    private CubeMetadataRepository cubeMetadataRes;

    @RequestMapping("/add")
    @Menu(type = "report", subtype = "cubelevel")
    public ModelAndView cubeLeveladd(ModelMap map, HttpServletRequest request, @Valid String cubeid, @Valid String dimid) {
        map.addAttribute("cubeid", cubeid);
        map.addAttribute("dimid", dimid);
        //map.addAttribute("fktableList",cubeMetadataRes.findByCubeid(cubeid));
        Dimension dim = dimensionRes.findByIdAndOrgi(dimid, super.getOrgi(request));

        if (dim != null) {
            if (!StringUtils.isBlank(dim.getFktable())) {
                map.put("fktableidList", tablePropertiesRes.findByDbtableid(dim.getFktable()));
            } else {
                List<CubeMetadata> cmList = cubeMetadataRes.findByCubeidAndMtype(cubeid, "0");
                if (!cmList.isEmpty() && cmList.get(0) != null) {
                    map.put("fktableidList", tablePropertiesRes.findByDbtableid(cmList.get(0).getTb().getId()));
                }
            }

        }
        return request(super.pageTplResponse("/apps/business/report/cube/cubelevel/add"));
    }

    @RequestMapping("/save")
    @Menu(type = "report", subtype = "cubelevel")
    public ModelAndView cubeLevelsave(ModelMap map, HttpServletRequest request, @Valid CubeLevel cubeLevel, @Valid String tableid) {
        if (!StringUtils.isBlank(cubeLevel.getName())) {
            cubeLevel.setOrgi(super.getOrgi(request));
            cubeLevel.setCreater(super.getUser(request).getId());
            cubeLevel.setCode(cubeLevel.getColumname());
            if (!StringUtils.isBlank(tableid)) {
                TableProperties tb = new TableProperties();
                tb.setId(tableid);
                TableProperties t = tablePropertiesRes.findById(tableid).orElseThrow(() -> new RuntimeException("not found"));
                cubeLevel.setTablename(t.getTablename());
                cubeLevel.setCode(t.getFieldname());
                cubeLevel.setColumname(t.getFieldname());
                cubeLevel.setTableproperty(tb);
            }
            cubeLevelRes.save(cubeLevel);
        }
        return request(super.pageTplResponse("redirect:/apps/report/cube/detail.html?id=" + cubeLevel.getCubeid() + "&dimensionId=" + cubeLevel.getDimid()));
    }

    @RequestMapping("/delete")
    @Menu(type = "report", subtype = "cubelevel")
    public ModelAndView quickreplydelete(ModelMap map, HttpServletRequest request, @Valid String id) {
        CubeLevel cubeLevel = cubeLevelRes.findById(id).orElseThrow(() -> new RuntimeException("not found"));
        cubeLevelRes.delete(cubeLevel);
        return request(super.pageTplResponse("redirect:/apps/report/cube/detail.html?id=" + cubeLevel.getCubeid() + "&dimensionId=" + cubeLevel.getDimid()));
    }

    @RequestMapping("/edit")
    @Menu(type = "report", subtype = "cubelevel", admin = true)
    public ModelAndView quickreplyedit(ModelMap map, HttpServletRequest request, @Valid String id) {
        CubeLevel cubeLevel = cubeLevelRes.findById(id).orElseThrow(EntityNotFoundEx::new);
        map.put("cubeLevel", cubeLevel);
        Dimension dim = dimensionRes.findByIdAndOrgi(cubeLevel.getDimid(), super.getOrgi(request));
        if (dim != null) {
            if (!StringUtils.isBlank(dim.getFktable())) {
                map.put("fktableidList", tablePropertiesRes.findByDbtableid(dim.getFktable()));
                map.addAttribute("tableid", dim.getFktable());
            } else {
                List<CubeMetadata> cmList = cubeMetadataRes.findByCubeidAndMtype(cubeLevel.getCubeid(), "0");
                if (!cmList.isEmpty() && cmList.get(0) != null) {
                    map.put("fktableidList", tablePropertiesRes.findByDbtableid(cmList.get(0).getTb().getId()));
                    map.addAttribute("tableid", cmList.get(0).getId());
                }
            }

        }
        return request(super.pageTplResponse("/apps/business/report/cube/cubelevel/edit"));
    }

    @RequestMapping("/update")
    @Menu(type = "report", subtype = "cubelevel", admin = true)
    public ModelAndView quickreplyupdate(ModelMap map, HttpServletRequest request, @Valid CubeLevel cubeLevel, @Valid String tableid) {
        if (!StringUtils.isBlank(cubeLevel.getId())) {
            cubeLevel.setOrgi(super.getOrgi(request));
            cubeLevel.setCreater(super.getUser(request).getId());
            cubeLevelRes.findById(cubeLevel.getId())
                    .ifPresent(temp -> {
                        cubeLevel.setCreatetime(temp.getCreatetime());
                    });
            if (!StringUtils.isBlank(tableid)) {
                TableProperties tb = new TableProperties();
                tb.setId(tableid);
                TableProperties t = tablePropertiesRes.findById(tableid).orElseThrow(() -> new RuntimeException("not found"));
                cubeLevel.setTablename(t.getTablename());
                cubeLevel.setCode(t.getFieldname());
                cubeLevel.setColumname(t.getFieldname());
                cubeLevel.setTableproperty(tb);
            }
            cubeLevelRes.save(cubeLevel);
        }
        return request(super.pageTplResponse("redirect:/apps/report/cube/detail.html?id=" + cubeLevel.getCubeid() + "&dimensionId=" + cubeLevel.getDimid()));
    }

    @RequestMapping("/fktableid")
    @Menu(type = "report", subtype = "cubelevel", admin = true)
    public ModelAndView fktableid(ModelMap map, HttpServletRequest request, @Valid String tableid) {
        if (!StringUtils.isBlank(tableid)) {
            map.put("fktableidList", tablePropertiesRes.findByDbtableid(tableid));
        }
        return request(super.pageTplResponse("/apps/business/report/cube/cubelevel/fktableiddiv"));
    }
}