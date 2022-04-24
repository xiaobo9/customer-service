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

import com.chatopera.cc.basic.DateFormatEnum;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.exception.CSKefuException;
import com.chatopera.cc.exception.EntityNotFoundException;
import com.chatopera.cc.model.*;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.persistence.es.ContactsRepositoryImpl;
import com.chatopera.cc.persistence.repository.*;
import com.chatopera.cc.proxy.ContactsProxy;
import com.chatopera.cc.proxy.OrganProxy;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.PinYinTools;
import com.chatopera.cc.util.PropertiesEventUtil;
import com.chatopera.cc.util.dsdata.DSData;
import com.chatopera.cc.util.dsdata.DSDataEvent;
import com.chatopera.cc.util.dsdata.ExcelImportProcess;
import com.chatopera.cc.util.dsdata.export.ExcelExporterProcess;
import com.chatopera.cc.util.dsdata.process.ContactsProcess;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Controller
@RequestMapping("/apps/contacts")
public class ContactsController extends Handler {

    private final static Logger logger = LoggerFactory.getLogger(ContactsController.class);

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private ContactsRepositoryImpl contactsRepository;

    @Autowired
    private PropertiesEventRepository propertiesEventRes;

    @Autowired
    private ReporterRepository reporterRes;

    @Autowired
    private MetadataRepository metadataRes;

    @Autowired
    private ContactsProxy contactsProxy;

    @Autowired
    private OrganRepository organRes;

    @Autowired
    private OrganProxy organProxy;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private AgentUserContactsRepository agentUserContactsRes;

    @Value("${web.upload-path}")
    private String path;

    @RequestMapping("/index.html")
    @Menu(type = "customer", subtype = "index")
    public ModelAndView index(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind) throws CSKefuException {
        final User user = super.getUser(request);
        final String orgi = user.getOrgi();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/contacts/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (StringUtils.isNotBlank(ckind)) {
            boolQueryBuilder.must(termQuery("ckind", ckind));
            map.put("ckind", ckind);
        }

        map.addAttribute("currentOrgan", currentOrgan);

        // FIXME
        PageRequest page = super.page(request);
        Page<Contacts> contacts = contactsRepository.findByCreaterAndSharesAndOrgi(
                user.getId(), user.getId(), orgi,
                null, null, false, boolQueryBuilder, q, page);

        map.addAttribute("contactsList", contacts);
        map.addAttribute("page", page);

        contactsProxy.bindContactsApproachableData(contacts, map, user);

        return request(super.createAppsTempletResponse("/apps/business/contacts/index"));
    }

    @RequestMapping("/today.html")
    @Menu(type = "customer", subtype = "today")
    public ModelAndView today(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind) throws CSKefuException {
        final User user = super.getUser(request);
        final String orgi = user.getOrgi();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/contacts/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(ckind)) {
            boolQueryBuilder.must(termQuery("ckind", ckind));
            map.put("ckind", ckind);
        }

        Page<Contacts> contacts = contactsRepository.findByCreaterAndSharesAndOrgi(user.getId(),
                user.getId(),
                orgi,
                MainUtils.getStartTime(), null, false,
                boolQueryBuilder, q,
                super.page(request));

        map.addAttribute("contactsList", contacts);

        contactsProxy.bindContactsApproachableData(contacts, map, user);

        return request(super.createAppsTempletResponse("/apps/business/contacts/index"));
    }

    @RequestMapping("/week.html")
    @Menu(type = "customer", subtype = "week")
    public ModelAndView week(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind) throws CSKefuException {
        final User user = super.getUser(request);
        final String orgi = user.getOrgi();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/contacts/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(ckind)) {
            boolQueryBuilder.must(termQuery("ckind", ckind));
            map.put("ckind", ckind);
        }

        Page<Contacts> contacts = contactsRepository.findByCreaterAndSharesAndOrgi(user.getId(),
                user.getId(),
                orgi,
                MainUtils.getWeekStartTime(), null, false,
                boolQueryBuilder, q,
                super.page(request));
        map.addAttribute("contactsList", contacts);
        contactsProxy.bindContactsApproachableData(contacts, map, user);


        return request(super.createAppsTempletResponse("/apps/business/contacts/index"));
    }

    @RequestMapping("/creater.html")
    @Menu(type = "customer", subtype = "creater")
    public ModelAndView creater(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind) throws CSKefuException {
        final User user = super.getUser(request);
        final String orgi = user.getOrgi();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/contacts/index"));
        }

        boolQueryBuilder.must(termQuery("creater", user.getId()));

        if (StringUtils.isNotBlank(ckind)) {
            boolQueryBuilder.must(termQuery("ckind", ckind));
            map.put("ckind", ckind);
        }
        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }

        Page<Contacts> contacts = contactsRepository.findByCreaterAndSharesAndOrgi(user.getId(),
                user.getId(),
                orgi, null, null, false,
                boolQueryBuilder, q,
                super.page(request));


        map.addAttribute("contactsList", contacts);
        contactsProxy.bindContactsApproachableData(contacts, map, user);
        return request(super.createAppsTempletResponse("/apps/business/contacts/index"));
    }

    @RequestMapping("/delete.html")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView delete(@Valid Contacts contacts, @Valid String p, @Valid String ckind) {
        if (contacts != null) {
            contacts = contactsRes.findById(contacts.getId()).orElseThrow(EntityNotFoundException::new);
            contacts.setDatastatus(true);                            //客户和联系人都是 逻辑删除
            contactsRes.save(contacts);
        }
        return request(super.pageTplResponse("redirect:/apps/contacts/index.html?p=" + p + "&ckind=" + ckind));
    }

    @RequestMapping("/add.html")
    @Menu(type = "contacts", subtype = "add")
    public ModelAndView add(ModelMap map, @Valid String ckind) {
        map.addAttribute("ckind", ckind);
        return request(super.pageTplResponse("/apps/business/contacts/add"));
    }


    @RequestMapping("/save.html")
    @Menu(type = "contacts", subtype = "save")
    public ModelAndView save(HttpServletRequest request, @Valid Contacts contacts,
                             @RequestParam(name = "savefrom", required = false) String savefrom,
                             @RequestParam(name = "idselflocation", required = false) String selflocation) {
        final User logined = super.getUser(request);
        final String orgi = logined.getOrgi();
        Organ currentOrgan = super.getOrgan(request);
        String skypeIDReplace = contactsProxy.sanitizeSkypeId(contacts.getSkypeid());
        List<Contacts> contact = contactsRes.findByskypeidAndDatastatus(skypeIDReplace, false);

        // 添加数据
        if (contacts.getSkypeid() != null && contact.size() == 0) {
            logger.info("[save] 数据库没有相同skypeid");
            contacts.setCreater(logined.getId());
            contacts.setOrgi(orgi);

            if (currentOrgan != null && StringUtils.isBlank(contacts.getOrgan())) {
                contacts.setOrgan(currentOrgan.getId());
            }

            contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));
            if (StringUtils.isBlank(contacts.getCusbirthday())) {
                contacts.setCusbirthday(null);
            }
            contactsRes.save(contacts);
            String msg = "new_contacts_success";
            return request(super.pageTplResponse("redirect:/apps/contacts/index.html?ckind=" + contacts.getCkind() + "&msg=" + msg));
        }
        String msg = "new_contacts_fail";
        return request(super.pageTplResponse("redirect:/apps/contacts/index.html?ckind=" + contacts.getCkind() + "&msg=" + msg));
    }

    @RequestMapping("/edit.html")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView edit(ModelMap map, @Valid String id, @Valid String ckind) {
        map.addAttribute("contacts", contactsRes.findById(id).orElseThrow(EntityNotFoundException::new));
        map.addAttribute("ckindId", ckind);
        return request(super.pageTplResponse("/apps/business/contacts/edit"));
    }

    @RequestMapping("/detail.html")
    @Menu(type = "customer", subtype = "index")
    public ModelAndView detail(ModelMap map, @Valid String id) {
        if (id == null) {
            return null; // id is required. Block strange requst anyway with g2.min, https://github.com/alibaba/BizCharts/issues/143
        }
        map.addAttribute("contacts", contactsRes.findById(id).orElseThrow(EntityNotFoundException::new));
        return request(super.createAppsTempletResponse("/apps/business/contacts/detail"));

    }

    // FIXME 待重构
//    @RequestMapping("mass")
//    @Menu(type = "contacts", subtype = "contacts")
//    public ModelAndView mass(
//            ModelMap map,
//            HttpServletRequest request,
//            @RequestParam(name = "ids", required = false) String ids,
//            @RequestParam(name = "massMessageToOnlineUserText", required = false) String massMessageToOnlineUserText,
//            @RequestParam(value = "massMessageToOnlineUserPic", required = false) MultipartFile multipart,
//            @RequestParam(value = "massMessageToOnlineUserFile", required = false) MultipartFile massMessageToOnlineUserFile,
//            @RequestParam(name = "paste", required = false) String paste,
//            @Valid String msg) throws IOException {
//        map.put("msg", msg);
//        final User logined = super.getUser(request);
//        final String orgi = logined.getOrgi();
//        boolean massStatus = false;
//
//        String massFileStyle = Constants.SKYPE_MESSAGE_TEXT;
//        if (StringUtils.isNotBlank(multipart.getOriginalFilename())) {
//            massFileStyle = Constants.SKYPE_MESSAGE_PIC;
//            contactsProxy.sendMessageToContacts(
//                    logined, ids, massMessageToOnlineUserText, multipart, massFileStyle, paste);
//        } else if (StringUtils.isNotBlank(massMessageToOnlineUserFile.getOriginalFilename())) {
//            massFileStyle = Constants.SKYPE_MESSAGE_FILE;
//            contactsProxy.sendMessageToContacts(
//                    logined, ids, massMessageToOnlineUserText, massMessageToOnlineUserFile, massFileStyle, paste);
//        } else {
//            contactsProxy.sendMessageToContacts(
//                    logined, ids, massMessageToOnlineUserText, massMessageToOnlineUserFile, massFileStyle, paste);
//        }
//
//        return request(
//                super.createRequestPageTempletResponse("redirect:/apps/contacts/index.html?massStatus=" + massStatus));
//    }


    @RequestMapping("/update.html")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView update(HttpServletRequest request, @Valid Contacts contacts, @Valid String ckindId) {
        final User logined = super.getUser(request);
        final String orgi = logined.getOrgi();
        Contacts data = contactsRes.findById(contacts.getId()).orElseThrow(EntityNotFoundException::new);

        String skypeIDReplace = contactsProxy.sanitizeSkypeId(contacts.getSkypeid());
        Contacts theOnlyContact = contactsRes.findByskypeidAndOrgiAndDatastatus(skypeIDReplace, logined.getOrgi(), false);
        Contacts oldContact = contactsRes.findByidAndOrgiAndDatastatus(contacts.getId(), logined.getOrgi(), false);

        boolean determineChange = contactsProxy.determineChange(contacts, oldContact);
        // 验证skype唯一性验证
        if (theOnlyContact != null && !theOnlyContact.getId().equals(oldContact.getId())) {
            logger.info("[contacts edit] errer :The same skypeid exists");
            String msg = "edit_contacts_fail";
            return request(super.pageTplResponse("redirect:/apps/contacts/index.html?ckind=" + ckindId + "&msg=" + msg));
        }
        if (!determineChange) {
            //无修改，直接点击确定
            return request(super.pageTplResponse("redirect:/apps/contacts/index.html?ckind=" + ckindId));
        }
        logger.info("[contacts edit] success :The contact has been modified successfully.");
        String msg = "edit_contacts_success";

        //记录 数据变更 历史
        List<PropertiesEvent> events = PropertiesEventUtil.processPropertiesModify(request, contacts, data);
        if (events.size() > 0) {
            String modifyid = MainUtils.getUUID();
            Date modifytime = new Date();
            for (PropertiesEvent event : events) {
                event.setDataid(contacts.getId());
                event.setCreater(logined.getId());
                event.setOrgi(orgi);
                event.setModifyid(modifyid);
                event.setCreatetime(modifytime);
                propertiesEventRes.save(event);
            }
        }

        contacts.setSkypeid(contacts.getSkypeid());
        contacts.setCreater(data.getCreater());
        contacts.setOrgan(data.getOrgan());
        contacts.setCreatetime(data.getCreatetime());
        contacts.setOrgi(logined.getOrgi());
        contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));

        if (StringUtils.isBlank(contacts.getCusbirthday())) {
            contacts.setCusbirthday(null);
        }

        contactsRes.save(contacts);
        return request(super.pageTplResponse("redirect:/apps/contacts/index.html?ckind=" + ckindId + "&msg=" + msg));
    }


    @RequestMapping("/imp.html")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView imp(ModelMap map, @Valid String ckind) {
        map.addAttribute("ckind", ckind);
        return request(super.pageTplResponse("/apps/business/contacts/imp"));
    }

    @RequestMapping("/impsave.html")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView impsave(HttpServletRequest request, @RequestParam(value = "cusfile", required = false) MultipartFile cusfile) throws IOException {
        final User logined = super.getUser(request);
        final String orgi = logined.getOrgi();
        Organ currentOrgan = super.getOrgan(request);
        String organId = currentOrgan != null ? currentOrgan.getId() : null;

        DSDataEvent event = new DSDataEvent();
        String filename = Objects.requireNonNull(cusfile.getOriginalFilename(), "获取文件名失败");
        String fileName = "contacts/" + MainUtils.getUUID() + filename.substring(filename.lastIndexOf("."));
        File excelFile = new File(path, fileName);
        MetadataTable table = metadataRes.findByTablename("uk_contacts");
        if (table != null) {
            FileUtils.writeByteArrayToFile(excelFile, cusfile.getBytes());
            event.setDSData(new DSData(table, excelFile, cusfile.getContentType(), logined));
            event.getDSData().setClazz(Contacts.class);
            event.getDSData().setProcess(new ContactsProcess(contactsRes));
            event.setOrgi(orgi);
            event.getValues().put("creater", logined.getId());
            event.getValues().put("organ", organId);
            event.getValues().put("shares", "all");
            reporterRes.save(event.getDSData().getReport());
            new ExcelImportProcess(event).process();        //启动导入任务
        }
        return request(super.pageTplResponse("redirect:/apps/contacts/index.html"));
    }

    @RequestMapping("/startmass.html")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView mass(ModelMap map, HttpServletRequest request, @Valid String ids) {
        map.addAttribute("organList", organRes.findByOrgiAndSkill(super.getOrgi(request), true));

        map.addAttribute("ids", ids);
        return request(super.pageTplResponse("/apps/business/contacts/mass"));
    }

    @RequestMapping("/expids.html")
    @Menu(type = "contacts", subtype = "contacts")
    public void expids(HttpServletResponse response, @Valid String[] ids) throws IOException {
        if (ids != null && ids.length > 0) {
            Iterable<Contacts> contactsList = contactsRes.findAllById(Arrays.asList(ids));
            MetadataTable table = metadataRes.findByTablename("uk_contacts");
            List<Map<String, Object>> values = new ArrayList<>();
            for (Contacts contacts : contactsList) {
                values.add(MainUtils.transBean2Map(contacts));
            }

            String format = DateFormatEnum.DAY.format(new Date());
            response.setHeader("content-disposition", "attachment;filename=CSKefu-Contacts-" + format + ".xls");

            ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
            excelProcess.process();
        }
    }

    @RequestMapping("/expall.html")
    @Menu(type = "contacts", subtype = "contacts")
    public void expall(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String ckind) throws IOException, CSKefuException {
        final User user = super.getUser(request);
        final String orgi = user.getOrgi();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if (!esOrganFilter(user, boolQueryBuilder)) {
            return;
        }

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (StringUtils.isNotBlank(ckind)) {
            boolQueryBuilder.must(termQuery("ckind", ckind));
            map.put("ckind", ckind);
        }

        boolQueryBuilder.must(termQuery("datastatus", false));        //只导出 数据删除状态 为 未删除的 数据
        Iterable<Contacts> contactsList = contactsRepository.findByCreaterAndSharesAndOrgi(
                user.getId(), user.getId(), orgi, null, null,
                false, boolQueryBuilder, null, super.page(request));

        MetadataTable table = metadataRes.findByTablename("uk_contacts");
        List<Map<String, Object>> values = new ArrayList<>();
        for (Contacts contacts : contactsList) {
            values.add(MainUtils.transBean2Map(contacts));
        }

        response.setHeader(
                "content-disposition",
                "attachment;filename=CSKefu-Contacts-" + new SimpleDateFormat("yyyy-MM-dd").format(
                        new Date()) + ".xls");

        ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
        excelProcess.process();
    }

    @RequestMapping("/expsearch.html")
    @Menu(type = "contacts", subtype = "contacts")
    public void expall(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String q, @Valid String ckind) throws IOException {
        final User logined = super.getUser(request);
        final String orgi = logined.getOrgi();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(ckind)) {
            boolQueryBuilder.must(termQuery("ckind", ckind));
            map.put("ckind", ckind);
        }

        Iterable<Contacts> contactsList = contactsRepository.findByCreaterAndSharesAndOrgi(
                logined.getId(), logined.getId(), orgi, null, null,
                false, boolQueryBuilder, q, super.page(request));
        MetadataTable table = metadataRes.findByTablename("uk_contacts");
        List<Map<String, Object>> values = new ArrayList<>();
        for (Contacts contacts : contactsList) {
            values.add(MainUtils.transBean2Map(contacts));
        }

        String format = DateFormatEnum.DAY.format(new Date());
        response.setHeader("content-disposition", "attachment;filename=CSKefu-Contacts-" + format + ".xls");

        ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
        excelProcess.process();
    }

    @RequestMapping("/embed/index.html")
    @Menu(type = "customer", subtype = "embed")
    public ModelAndView embed(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind, @Valid String msg, @Valid String userid, @Valid String agentserviceid) throws CSKefuException {
        final User user = super.getUser(request);
        final String orgi = user.getOrgi();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        map.put("msg", msg);
        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/contacts/embed/index"));
        }
        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(agentserviceid)) {
            map.put("agentserviceid", agentserviceid);
        }
        if (StringUtils.isNotBlank(ckind)) {
            boolQueryBuilder.must(termQuery("ckind", ckind));
            map.put("ckind", ckind);
        }
        if (StringUtils.isNotBlank(agentserviceid)) {
            AgentService service = agentServiceRes.findByIdAndOrgi(agentserviceid, orgi);
            boolQueryBuilder.must(termQuery("organ", service.getSkill()));
        }
        Page<Contacts> contactsList = contactsRepository.findByCreaterAndSharesAndOrgi(
                user.getId(), user.getId(), orgi, null, null, false, boolQueryBuilder, q,
                super.page(request));

        map.addAttribute("contactsList", contactsList);

        if (StringUtils.isNotBlank(userid)) {
            agentUserContactsRes.findOneByUseridAndOrgi(userid, orgi).ifPresent(p -> {
                map.addAttribute("currentAgentUserContactsId", p.getId());
                map.addAttribute("currentContacsId", p.getContactsid());
            });
        }

        return request(super.pageTplResponse("/apps/business/contacts/embed/index"));
    }

    @RequestMapping("/embed/add.html")
    @Menu(type = "contacts", subtype = "embedadd")
    public ModelAndView embedadd(ModelMap map, @Valid String agentserviceid) {
        if (StringUtils.isNotBlank(agentserviceid)) {
            map.put("agentserviceid", agentserviceid);
        }
        return request(super.pageTplResponse("/apps/business/contacts/embed/add"));
    }

    @RequestMapping("/embed/save.html")
    @Menu(type = "contacts", subtype = "embedsave")
    public ModelAndView embedsave(HttpServletRequest request, @Valid Contacts contacts, @Valid String agentserviceid) {
        final User logined = super.getUser(request);
        String skypeIDReplace = contactsProxy.sanitizeSkypeId(contacts.getSkypeid());
        List<Contacts> contact = contactsRes.findByskypeidAndDatastatus(skypeIDReplace, false);

        //添加数据
        if (contacts.getSkypeid() != null && contact.size() == 0) {
            contacts.setCreater(logined.getId());
            contacts.setOrgi(logined.getOrgi());

            if (StringUtils.isNotBlank(agentserviceid)) {
                AgentService agentService = agentServiceRes.findById(agentserviceid).orElseThrow(EntityNotFoundException::new);
                contacts.setOrgan(agentService.getSkill());
            }

            contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));
            if (StringUtils.isBlank(contacts.getCusbirthday())) {
                contacts.setCusbirthday(null);
            }
            contactsRes.save(contacts);
            String msg = "new_contacts_success";
            return request(super.pageTplResponse("redirect:/apps/contacts/embed/index.html?msg=" + msg + "&agentserviceid=" + agentserviceid));
        }
        String msg = "new_contacts_fail";
        return request(super.pageTplResponse("redirect:/apps/contacts/embed/index.html?msg=" + msg + "&agentserviceid=" + agentserviceid));
    }

    @RequestMapping("/embed/edit.html")
    @Menu(type = "contacts", subtype = "embededit")
    public ModelAndView embededit(ModelMap map, @Valid String id, @Valid String agentserviceid) {
        map.addAttribute("contacts", contactsRes.findById(id).orElseThrow(EntityNotFoundException::new));
        if (StringUtils.isNotBlank(agentserviceid)) {
            map.addAttribute("agentserviceid", agentserviceid);
        }
        return request(super.pageTplResponse("/apps/business/contacts/embed/edit"));
    }

    @RequestMapping("/embed/update.html")
    @Menu(type = "contacts", subtype = "embedupdate")
    public ModelAndView embedupdate(HttpServletRequest request, @Valid Contacts contacts, @Valid String agentserviceid) {
        final User logined = super.getUser(request);
        final String orgi = logined.getOrgi();
        Contacts data = contactsRes.findById(contacts.getId()).orElseThrow(EntityNotFoundException::new);

        String skypeIDReplace = contactsProxy.sanitizeSkypeId(contacts.getSkypeid());
        Contacts theOnlyContact = contactsRes.findByskypeidAndOrgiAndDatastatus(skypeIDReplace, logined.getOrgi(), false);
        Contacts oldContact = contactsRes.findByidAndOrgiAndDatastatus(contacts.getId(), logined.getOrgi(), false);

        boolean determineChange = contactsProxy.determineChange(contacts, oldContact);

        // 验证skype唯一性验证
        if (theOnlyContact != null && !theOnlyContact.getId().equals(oldContact.getId())) {
            logger.info("[contacts edit] errer :The same skypeid exists");
            String msg = "edit_contacts_fail";
            return request(super.pageTplResponse("redirect:/apps/contacts/embed/index.html?msg=" + msg + "&agentserviceid=" + agentserviceid));
        }
        if (!determineChange) {
            //无修改，直接点击确定
            return request(super.pageTplResponse("redirect:/apps/contacts/embed/index.html?agentserviceid=" + agentserviceid));
        }
        logger.info("[contacts edit] success :The contact has been modified successfully.");
        String msg = "edit_contacts_success";
        //记录 数据变更 历史
        List<PropertiesEvent> events = PropertiesEventUtil.processPropertiesModify(request, contacts, data);
        if (events.size() > 0) {
            String modifyid = MainUtils.getUUID();
            Date modifytime = new Date();
            for (PropertiesEvent event : events) {
                event.setDataid(contacts.getId());
                event.setCreater(logined.getId());
                event.setOrgi(orgi);
                event.setModifyid(modifyid);
                event.setCreatetime(modifytime);
                propertiesEventRes.save(event);
            }
        }

        contacts.setSkypeid(contacts.getSkypeid());
        contacts.setCreater(data.getCreater());
        contacts.setOrgan(data.getOrgan());
        contacts.setCreatetime(data.getCreatetime());
        contacts.setOrgi(logined.getOrgi());
        contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));


        if (StringUtils.isBlank(contacts.getCusbirthday())) {
            contacts.setCusbirthday(null);
        }

        contactsRes.save(contacts);
        return request(super.pageTplResponse("redirect:/apps/contacts/embed/index.html?msg=" + msg + "&agentserviceid=" + agentserviceid));
    }
}
