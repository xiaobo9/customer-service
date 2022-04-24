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

import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.exception.CSKefuException;
import com.chatopera.cc.exception.EntityNotFoundException;
import com.chatopera.cc.model.*;
import com.chatopera.cc.persistence.es.ContactsRepository;
import com.chatopera.cc.persistence.es.EntCustomerRepository;
import com.chatopera.cc.persistence.repository.MetadataRepository;
import com.chatopera.cc.persistence.repository.PropertiesEventRepository;
import com.chatopera.cc.persistence.repository.ReporterRepository;
import com.chatopera.cc.proxy.OrganProxy;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.PinYinTools;
import com.chatopera.cc.util.PropertiesEventUtil;
import com.chatopera.cc.util.dsdata.DSData;
import com.chatopera.cc.util.dsdata.DSDataEvent;
import com.chatopera.cc.util.dsdata.ExcelImportProcess;
import com.chatopera.cc.util.dsdata.export.ExcelExporterProcess;
import com.chatopera.cc.util.dsdata.process.EntCustomerProcess;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/apps/customer")
public class CustomerController extends Handler {
    private final static Logger logger = LoggerFactory.getLogger(CustomerController.class);

    @Autowired
    private EntCustomerRepository entCustomerRes;

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private ReporterRepository reporterRes;

    @Autowired
    private MetadataRepository metadataRes;

    @Autowired
    private PropertiesEventRepository propertiesEventRes;

    @Autowired
    private OrganProxy organProxy;

    @Value("${web.upload-path}")
    private String path;

    @RequestMapping("/index.html")
    @Menu(type = "customer", subtype = "index")
    public ModelAndView index(ModelMap map,
                              HttpServletRequest request,
                              final @Valid String q,
                              final @Valid String ekind,
                              final @Valid String msg) throws CSKefuException {
        logger.info("[index] query {}, ekind {}, msg {}", q, ekind, msg);
        final User user = super.getUser(request);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        map.put("msg", msg);
        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/customer/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(ekind)) {
            boolQueryBuilder.must(termQuery("ekind", ekind));
            map.put("ekind", ekind);
        }

        map.addAttribute("currentOrgan", currentOrgan);

        map.addAttribute("entCustomerList", entCustomerRes.findByCreaterAndSharesAndOrgi(user.getId(),
                user.getId(),
                super.getOrgi(request),
                null,
                null,
                false,
                boolQueryBuilder,
                q,
                super.page(request)));

        return request(super.createAppsTempletResponse("/apps/business/customer/index"));
    }

    @RequestMapping("/today.html")
    @Menu(type = "customer", subtype = "today")
    public ModelAndView today(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ekind) throws CSKefuException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        User user = super.getUser(request);
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/customer/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }

        if (StringUtils.isNotBlank(ekind)) {
            boolQueryBuilder.must(termQuery("ekind", ekind));
            map.put("ekind", ekind);
        }
        map.addAttribute("entCustomerList", entCustomerRes.findByCreaterAndSharesAndOrgi(user.getId(), user.getId(),
                super.getOrgi(request), MainUtils.getStartTime(), null, false, boolQueryBuilder, q, super.page(request)));

        return request(super.createAppsTempletResponse("/apps/business/customer/index"));
    }

    @RequestMapping("/week.html")
    @Menu(type = "customer", subtype = "week")
    public ModelAndView week(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ekind) throws CSKefuException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));
        User user = super.getUser(request);
        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/customer/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(ekind)) {
            boolQueryBuilder.must(termQuery("ekind", ekind));
            map.put("ekind", ekind);
        }
        map.addAttribute("entCustomerList", entCustomerRes.findByCreaterAndSharesAndOrgi(user.getId(), user.getId(), super.getOrgi(request), MainUtils.getWeekStartTime(), null, false, boolQueryBuilder, q, super.page(request)));

        return request(super.createAppsTempletResponse("/apps/business/customer/index"));
    }

    @RequestMapping("/enterprise.html")
    @Menu(type = "customer", subtype = "enterprise")
    public ModelAndView enterprise(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ekind) throws CSKefuException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        User user = super.getUser(request);
        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/customer/index"));
        }

        boolQueryBuilder.must(termQuery("etype", MainContext.CustomerTypeEnum.ENTERPRISE.toString()));
        if (StringUtils.isNotBlank(ekind)) {
            boolQueryBuilder.must(termQuery("ekind", ekind));
            map.put("ekind", ekind);
        }
        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        map.addAttribute("entCustomerList", entCustomerRes.findByCreaterAndSharesAndOrgi(user.getId(), user.getId(), super.getOrgi(request), null, null, false, boolQueryBuilder, q, super.page(request)));
        return request(super.createAppsTempletResponse("/apps/business/customer/index"));
    }

    @RequestMapping("/personal.html")
    @Menu(type = "customer", subtype = "personal")
    public ModelAndView personal(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ekind) throws CSKefuException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        User user = super.getUser(request);
        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/customer/index"));
        }

        boolQueryBuilder.must(termQuery("etype", MainContext.CustomerTypeEnum.PERSONAL.toString()));

        if (StringUtils.isNotBlank(ekind)) {
            boolQueryBuilder.must(termQuery("ekind", ekind));
            map.put("ekind", ekind);
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        map.addAttribute("entCustomerList", entCustomerRes.findByCreaterAndSharesAndOrgi(user.getId(), user.getId(), super.getOrgi(request), null, null, false, boolQueryBuilder, q, super.page(request)));
        return request(super.createAppsTempletResponse("/apps/business/customer/index"));
    }

    @RequestMapping("/creater.html")
    @Menu(type = "customer", subtype = "creater")
    public ModelAndView creater(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ekind) throws CSKefuException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        User user = super.getUser(request);
        if (!esOrganFilter(user, boolQueryBuilder)) {
            return request(super.createAppsTempletResponse("/apps/business/customer/index"));
        }

        boolQueryBuilder.must(termQuery("creater", user.getId()));

        if (StringUtils.isNotBlank(ekind)) {
            boolQueryBuilder.must(termQuery("ekind", ekind));
            map.put("ekind", ekind);
        }
        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }

        map.addAttribute("entCustomerList", entCustomerRes.findByCreaterAndSharesAndOrgi(user.getId(), user.getId(), super.getOrgi(request), null, null, false, boolQueryBuilder, q, super.page(request)));
        return request(super.createAppsTempletResponse("/apps/business/customer/index"));
    }

    @RequestMapping("/add.html")
    @Menu(type = "customer", subtype = "customer")
    public ModelAndView add(ModelMap map, HttpServletRequest request, @Valid String ekind) {
        map.addAttribute("ekind", ekind);
        return request(super.pageTplResponse("/apps/business/customer/add"));
    }

    @RequestMapping("/save.html")
    @Menu(type = "customer", subtype = "customer")
    public ModelAndView save(HttpServletRequest request,
                             @Valid CustomerGroupForm customerGroupForm) {
        String msg = "";
        msg = "new_entcustomer_success";
        customerGroupForm.getEntcustomer().setCreater(super.getUser(request).getId());
        customerGroupForm.getEntcustomer().setOrgi(super.getOrgi(request));

        final User logined = super.getUser(request);
        Organ currentOrgan = super.getOrgan(request);

//    	customerGroupForm.getEntcustomer().setEtype(MainContext.CustomerTypeEnum.ENTERPRISE.toString());
        customerGroupForm.getEntcustomer().setPinyin(PinYinTools.getInstance().getFirstPinYin(customerGroupForm.getEntcustomer().getName()));
        if (currentOrgan != null && StringUtils.isBlank(customerGroupForm.getEntcustomer().getOrgan())) {
            customerGroupForm.getEntcustomer().setOrgan(currentOrgan.getId());
            customerGroupForm.getContacts().setOrgan(currentOrgan.getId());
        }

        entCustomerRes.save(customerGroupForm.getEntcustomer());
        if (customerGroupForm.getContacts() != null && StringUtils.isNotBlank(customerGroupForm.getContacts().getName())) {
            customerGroupForm.getContacts().setEntcusid(customerGroupForm.getEntcustomer().getId());
            customerGroupForm.getContacts().setCreater(logined.getId());
            customerGroupForm.getContacts().setOrgi(logined.getOrgi());
            customerGroupForm.getContacts().setPinyin(PinYinTools.getInstance().getFirstPinYin(customerGroupForm.getContacts().getName()));
            if (StringUtils.isBlank(customerGroupForm.getContacts().getCusbirthday())) {
                customerGroupForm.getContacts().setCusbirthday(null);
            }

            contactsRes.save(customerGroupForm.getContacts());
        }
        return request(super.pageTplResponse("redirect:/apps/customer/index.html?ekind=" + customerGroupForm.getEntcustomer().getEkind() + "&msg=" + msg));
    }

    @RequestMapping("/delete.html")
    @Menu(type = "customer", subtype = "customer")
    public ModelAndView delete(HttpServletRequest request, @Valid EntCustomer entCustomer, @Valid String p, @Valid String ekind) {
        if (entCustomer != null) {
            entCustomer = entCustomerRes.findById(entCustomer.getId()).orElseThrow(EntityNotFoundException::new);
            entCustomer.setDatastatus(true);                            //客户和联系人都是 逻辑删除
            entCustomerRes.save(entCustomer);
        }
        return request(super.pageTplResponse("redirect:/apps/customer/index.html?p=" + p + "&ekind=" + ekind));
    }

    @RequestMapping("/edit.html")
    @Menu(type = "customer", subtype = "customer")
    public ModelAndView edit(ModelMap map, HttpServletRequest request, @Valid String id, @Valid String ekind) {
        map.addAttribute("entCustomer", entCustomerRes.findById(id).orElseThrow(EntityNotFoundException::new));
        map.addAttribute("ekindId", ekind);
        return request(super.pageTplResponse("/apps/business/customer/edit"));
    }

    @RequestMapping("/update.html")
    @Menu(type = "customer", subtype = "customer")
    public ModelAndView update(HttpServletRequest request, @Valid CustomerGroupForm customerGroupForm, @Valid String ekindId) {
        final User logined = super.getUser(request);
        EntCustomer entcustomer = customerGroupForm.getEntcustomer();
        EntCustomer customer = entCustomerRes.findById(entcustomer.getId()).orElseThrow(EntityNotFoundException::new);
        String msg = "";

        //记录 数据变更 历史
        List<PropertiesEvent> events = PropertiesEventUtil.processPropertiesModify(request, entcustomer, customer);
        if (events.size() > 0) {
            msg = "edit_entcustomer_success";
            String modifyid = MainUtils.getUUID();
            Date modifytime = new Date();
            for (PropertiesEvent event : events) {
                event.setDataid(entcustomer.getId());
                event.setCreater(super.getUser(request).getId());
                event.setOrgi(super.getOrgi(request));
                event.setModifyid(modifyid);
                event.setCreatetime(modifytime);
                propertiesEventRes.save(event);
            }
        }
        entcustomer.setOrgan(customer.getOrgan());
        entcustomer.setCreater(customer.getCreater());
        entcustomer.setCreatetime(customer.getCreatetime());
        entcustomer.setOrgi(logined.getOrgi());
        entcustomer.setPinyin(PinYinTools.getInstance().getFirstPinYin(entcustomer.getName()));
        entCustomerRes.save(entcustomer);

        return request(super.pageTplResponse("redirect:/apps/customer/index.html?ekind=" + ekindId + "&msg=" + msg));
    }

    @RequestMapping("/imp.html")
    @Menu(type = "customer", subtype = "customer")
    public ModelAndView imp(ModelMap map, HttpServletRequest request, @Valid String ekind) {
        map.addAttribute("ekind", ekind);
        return request(super.pageTplResponse("/apps/business/customer/imp"));
    }

    @RequestMapping("/impsave.html")
    @Menu(type = "customer", subtype = "customer")
    public ModelAndView impsave(ModelMap map, HttpServletRequest request, @RequestParam(value = "cusfile", required = false) MultipartFile cusfile, @Valid String ekind) throws IOException {
        DSDataEvent event = new DSDataEvent();
        String fileName = "customer/" + MainUtils.getUUID() + cusfile.getOriginalFilename().substring(cusfile.getOriginalFilename().lastIndexOf("."));
        File excelFile = new File(path, fileName);

        Organ currentOrgan = super.getOrgan(request);
        String organId = currentOrgan != null ? currentOrgan.getId() : null;

        MetadataTable table = metadataRes.findByTablename("uk_entcustomer");
        if (table != null) {
            FileUtils.writeByteArrayToFile(excelFile, cusfile.getBytes());
            event.setDSData(new DSData(table, excelFile, cusfile.getContentType(), super.getUser(request)));
            event.getDSData().setClazz(EntCustomer.class);
            event.getDSData().setProcess(new EntCustomerProcess(entCustomerRes));
            event.setOrgi(super.getOrgi(request));
	    	/*if(StringUtils.isNotBlank(ekind)){
	    		exchange.getValues().put("ekind", ekind) ;
	    	}*/
            event.getValues().put("creater", super.getUser(request).getId());
            event.getValues().put("organ", organId);
            event.getValues().put("shares", "all");
            reporterRes.save(event.getDSData().getReport());
            new ExcelImportProcess(event).process();        //启动导入任务
        }

        return request(super.pageTplResponse("redirect:/apps/customer/index.html"));
    }

    @RequestMapping("/expids")
    @Menu(type = "customer", subtype = "customer")
    public void expids(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String[] ids) throws IOException {
        if (ids != null && ids.length > 0) {
            Iterable<EntCustomer> entCustomerList = entCustomerRes.findAllById(Arrays.asList(ids));
            MetadataTable table = metadataRes.findByTablename("uk_entcustomer");
            List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
            for (EntCustomer customer : entCustomerList) {
                values.add(MainUtils.transBean2Map(customer));
            }

            response.setHeader("content-disposition", "attachment;filename=UCKeFu-EntCustomer-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".xls");

            ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
            excelProcess.process();
        }

        return;
    }

    @RequestMapping("/expall")
    @Menu(type = "customer", subtype = "customer")
    public void expall(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String ekind) throws IOException, CSKefuException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        User user = super.getUser(request);
        if (!esOrganFilter(user, boolQueryBuilder)) {
            // #TODO 提示没有部门
            return;
        }

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (StringUtils.isNotBlank(ekind)) {
            boolQueryBuilder.must(termQuery("ekind", ekind));
            map.put("ekind", ekind);
        }

        boolQueryBuilder.must(termQuery("datastatus", false));        //只导出 数据删除状态 为 未删除的 数据
        Iterable<EntCustomer> entCustomerList = entCustomerRes.findByCreaterAndSharesAndOrgi(user.getId(), user.getId(), super.getOrgi(request), null, null, false, boolQueryBuilder, null, super.page(request));

        MetadataTable table = metadataRes.findByTablename("uk_entcustomer");
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (EntCustomer customer : entCustomerList) {
            values.add(MainUtils.transBean2Map(customer));
        }

        response.setHeader("content-disposition", "attachment;filename=UCKeFu-EntCustomer-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".xls");

        ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
        excelProcess.process();
        return;
    }

    @RequestMapping("/expsearch")
    @Menu(type = "customer", subtype = "customer")
    public void expall(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String q, @Valid String ekind) throws IOException, CSKefuException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        User user = super.getUser(request);
        if (!esOrganFilter(user, boolQueryBuilder)) {
            // #TODO 提示没有部门
            return;
        }

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParentAndOrgi(currentOrgan, super.getOrgi(request));
        boolQueryBuilder.must(termsQuery("organ", organs.keySet()));

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(ekind)) {
            boolQueryBuilder.must(termQuery("ekind", ekind));
            map.put("ekind", ekind);
        }

        Iterable<EntCustomer> entCustomerList = entCustomerRes.findByCreaterAndSharesAndOrgi(user.getId(), user.getId(), super.getOrgi(request), null, null, false, boolQueryBuilder, q, super.page(request));
        MetadataTable table = metadataRes.findByTablename("uk_entcustomer");
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (EntCustomer customer : entCustomerList) {
            values.add(MainUtils.transBean2Map(customer));
        }

        response.setHeader("content-disposition", "attachment;filename=UCKeFu-EntCustomer-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".xls");

        ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
        excelProcess.process();

        return;
    }
}