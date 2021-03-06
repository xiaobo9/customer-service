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
package com.chatopera.cc.controller.apps.service;

import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.service.OrganService;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.dsdata.export.ExcelExporterProcess;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.kit.AttachFileKit;
import com.github.xiaobo9.commons.kit.ObjectKit;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.AgentServiceRepository;
import com.github.xiaobo9.repository.MetadataRepository;
import com.github.xiaobo9.repository.ServiceSummaryRepository;
import com.github.xiaobo9.repository.TagRepository;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.persistence.criteria.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

@Controller
@RequestMapping("/apps/agent/summary")
public class AgentSummaryController extends Handler {

    @Autowired
    private ServiceSummaryRepository serviceSummaryRes;

    @Autowired
    private MetadataRepository metadataRes;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private TagRepository tagRes;

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private OrganService organService;

    /**
     * 按条件查询
     *
     * @param map
     * @param request
     * @param begin
     * @param end
     * @return
     */
    @RequestMapping(value = "/index")
    @Menu(type = "agent", subtype = "agentsummary", access = false)
    public ModelAndView index(ModelMap map, HttpServletRequest request, @Valid final String begin, @Valid final String end) {
        final String orgi = super.getOrgi(request);
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        Page<AgentServiceSummary> page = serviceSummaryRes.findAll(new Specification<AgentServiceSummary>() {
            @Override
            public Predicate toPredicate(Root<AgentServiceSummary> root, CriteriaQuery<?> query,
                                         CriteriaBuilder cb) {
                List<Predicate> list = new ArrayList<Predicate>();
                Expression<String> exp = root.<String>get("skill");
                list.add(exp.in(organs.keySet()));
                list.add(cb.equal(root.get("orgi").as(String.class), orgi));
                list.add(cb.equal(root.get("process").as(boolean.class), 0));
                list.add(cb.notEqual(root.get("channel").as(String.class), Enums.ChannelType.PHONE.toString()));
                try {
                    if (!StringUtils.isBlank(begin) && begin.matches("[\\d]{4}-[\\d]{2}-[\\d]{2}")) {
                        list.add(cb.greaterThanOrEqualTo(root.get("createtime").as(Date.class), DateFormatEnum.DAY.parse(begin)));
                    }
                    if (!StringUtils.isBlank(end) && end.matches("[\\d]{4}-[\\d]{2}-[\\d]{2}")) {
                        list.add(cb.lessThanOrEqualTo(root.get("createtime").as(Date.class), DateFormatEnum.DAY_TIME.parse(end + " 23:59:59")));
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                Predicate[] p = new Predicate[list.size()];
                return cb.and(list.toArray(p));
            }
        }, super.page(request, Sort.Direction.DESC, "createtime"));
        map.addAttribute("summaryList", page);
        map.addAttribute("begin", begin);
        map.addAttribute("end", end);

        map.addAttribute("tags", tagRes.findByOrgiAndTagtype(super.getOrgi(request), Enums.ModelType.SUMMARY.toString()));

        return request(super.createAppsTempletResponse("/apps/service/summary/index"));
    }

    @RequestMapping(value = "/process")
    @Menu(type = "agent", subtype = "agentsummary", access = false)
    public ModelAndView process(ModelMap map, HttpServletRequest request, @Valid final String id) {
        AgentServiceSummary summary = serviceSummaryRes.findByIdAndOrgi(id, super.getOrgi(request));
        map.addAttribute("summary", summary);
        map.put("summaryTags", tagRes.findByOrgiAndTagtype(super.getOrgi(request), Enums.ModelType.SUMMARY.toString()));
        if (summary != null && !StringUtils.isBlank(summary.getAgentserviceid())) {
            AgentService service = agentServiceRes.findByIdAndOrgi(summary.getAgentserviceid(), super.getOrgi(request));
            map.addAttribute("service", service);
            if (!StringUtils.isBlank(summary.getContactsid())) {
                Contacts contacts = contactsRes.findById(summary.getContactsid()).orElse(null);
                map.addAttribute("contacts", contacts);
            }
        }

        return request(super.pageTplResponse("/apps/service/summary/process"));
    }

    @RequestMapping(value = "/save")
    @Menu(type = "agent", subtype = "agentsummary", access = false)
    public ModelAndView save(ModelMap map, HttpServletRequest request, @Valid final AgentServiceSummary summary) {
        AgentServiceSummary oldSummary = serviceSummaryRes.findByIdAndOrgi(summary.getId(), super.getOrgi(request));
        if (oldSummary != null) {
            oldSummary.setProcess(true);
            oldSummary.setUpdatetime(new Date());
            oldSummary.setUpdateuser(super.getUser(request).getId());
            oldSummary.setProcessmemo(summary.getProcessmemo());
            serviceSummaryRes.save(oldSummary);
        }

        return request(super.pageTplResponse("redirect:/apps/agent/summary/index.html"));
    }

    @RequestMapping("/expids")
    @Menu(type = "agent", subtype = "agentsummary", access = false)
    public void expids(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String[] ids) throws IOException {
        if (ids != null && ids.length > 0) {
            Iterable<AgentServiceSummary> statusEventList = serviceSummaryRes.findAllById(Arrays.asList(ids));
            MetadataTable table = metadataRes.findByTablename("uk_servicesummary");
            List<Map<String, Object>> values = new ArrayList<>();
            for (AgentServiceSummary event : statusEventList) {
                values.add(ObjectKit.transBean2Map(event));
            }

            response.setHeader(AttachFileKit.HEADER_KEY, AttachFileKit.xlsWithDayAnd("Summary-History"));

            ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
            excelProcess.process();
        }

    }

    @RequestMapping("/expall")
    @Menu(type = "agent", subtype = "agentsummary")
    public void expall(ModelMap map, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organService.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        Iterable<AgentServiceSummary> statusEventList = serviceSummaryRes.findByChannelNotAndOrgiAndSkillIn(
                Enums.ChannelType.PHONE.toString(), super.getOrgi(request), organs.keySet(), new PageRequest(0, 10000));
        MetadataTable table = metadataRes.findByTablename("uk_servicesummary");
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (AgentServiceSummary statusEvent : statusEventList) {
            values.add(ObjectKit.transBean2Map(statusEvent));
        }

        response.setHeader(AttachFileKit.HEADER_KEY, AttachFileKit.xlsWithDayAnd("Summary-History"));

        ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
        excelProcess.process();
    }

    @RequestMapping("/expsearch")
    @Menu(type = "agent", subtype = "agentsummary", access = false)
    public void expall(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid final String begin, @Valid final String end) throws IOException {
        final String orgi = super.getOrgi(request);
        Page<AgentServiceSummary> page = serviceSummaryRes.findAll(new Specification<AgentServiceSummary>() {
            @Override
            public Predicate toPredicate(Root<AgentServiceSummary> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> list = new ArrayList<Predicate>();
                list.add(cb.and(cb.equal(root.get("process").as(boolean.class), 0)));
                list.add(cb.equal(root.get("orgi").as(String.class), orgi));
                list.add(cb.and(cb.notEqual(root.get("channel").as(String.class), Enums.ChannelType.PHONE.toString())));
                try {
                    if (!StringUtils.isBlank(begin) && begin.matches("[\\d]{4}-[\\d]{2}-[\\d]{2} [\\d]{2}:[\\d]{2}:[\\d]{2}")) {
                        list.add(cb.and(cb.greaterThanOrEqualTo(root.get("createtime").as(Date.class), DateFormatEnum.DAY_TIME.parse(begin))));
                    }
                    if (!StringUtils.isBlank(end) && end.matches("[\\d]{4}-[\\d]{2}-[\\d]{2} [\\d]{2}:[\\d]{2}:[\\d]{2}")) {
                        list.add(cb.and(cb.lessThanOrEqualTo(root.get("createtime").as(Date.class), DateFormatEnum.DAY_TIME.parse(end))));
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                Predicate[] p = new Predicate[list.size()];
                return cb.and(list.toArray(p));
            }
        }, PageRequest.of(0, 10000, Sort.Direction.DESC, "createtime"));

        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (AgentServiceSummary summary : page) {
            values.add(ObjectKit.transBean2Map(summary));
        }

        response.setHeader(AttachFileKit.HEADER_KEY, AttachFileKit.xlsWithDayAnd("Summary-History"));

        MetadataTable table = metadataRes.findByTablename("uk_servicesummary");

        ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
        excelProcess.process();

        return;
    }
}
