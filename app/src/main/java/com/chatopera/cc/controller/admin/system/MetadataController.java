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

import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.persistence.hibernate.HibernateDao;
import com.chatopera.cc.util.CskefuList;
import com.chatopera.cc.util.Dict;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.metadata.DatabaseMetaDataHandler;
import com.chatopera.cc.util.metadata.UKColumnMetadata;
import com.chatopera.cc.util.metadata.UKTableMetaData;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.MetadataTable;
import com.github.xiaobo9.entity.SysDic;
import com.github.xiaobo9.entity.TableProperties;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.repository.MetadataRepository;
import com.github.xiaobo9.repository.SysDicRepository;
import com.github.xiaobo9.repository.TablePropertiesRepository;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/admin/metadata")
public class MetadataController extends Handler {
    private static final Logger logger = LoggerFactory.getLogger(MetadataController.class);

    @Autowired
    private MetadataRepository metadataRes;

    @Autowired
    private HibernateDao<?> service;

    @Autowired
    private SysDicRepository sysDicRes;

    @Autowired
    private TablePropertiesRepository tablePropertiesRes;

    @Autowired
    @PersistenceContext
    private EntityManager em;

    @RequestMapping("/index")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView index(ModelMap map, HttpServletRequest request) throws SQLException {
        map.addAttribute("metadataList", metadataRes.findAll(super.page(request)));
        return request(super.createAdminTemplateResponse("/admin/system/metadata/index"));
    }

    @RequestMapping("/edit")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView edit(ModelMap map, @Valid String id) {
        map.addAttribute("metadata", metadataRes.findById(id).orElse(null));
        return request(super.pageTplResponse("/admin/system/metadata/edit"));
    }

    @RequestMapping("/update")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView update(@Valid MetadataTable metadata) throws SQLException {
        MetadataTable table = metadataRes.findById(metadata.getId())
                .orElseThrow(() -> EntityNotFoundEx.of(MetadataTable.class));
        table.setName(metadata.getName());
        table.setFromdb(metadata.isFromdb());
        table.setListblocktemplet(metadata.getListblocktemplet());
        table.setPreviewtemplet(metadata.getPreviewtemplet());
        metadataRes.save(table);
        return request(super.pageTplResponse("redirect:/admin/metadata/index.html"));
    }

    @RequestMapping("/properties/edit")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView propertiesedit(ModelMap map, @Valid String id) {
        map.addAttribute("tp", tablePropertiesRes.findById(id).orElse(null));
        map.addAttribute("sysdicList", sysDicRes.findByParentid("0"));
        map.addAttribute("dataImplList", Dict.getInstance().getDic("com.dic.data.impl"));

        return request(super.pageTplResponse("/admin/system/metadata/tpedit"));
    }

    @RequestMapping("/properties/update")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView propertiesupdate(@Valid TableProperties tp) {
        TableProperties tableProperties = tablePropertiesRes.findById(tp.getId()).orElseThrow(() -> new RuntimeException("not found"));
        tableProperties.setName(tp.getName());
        tableProperties.setSeldata(tp.isSeldata());
        tableProperties.setSeldatacode(tp.getSeldatacode());

        tableProperties.setReffk(tp.isReffk());
        tableProperties.setReftbid(tp.getReftbid());

        tableProperties.setDefaultvaluetitle(tp.getDefaultvaluetitle());
        tableProperties.setDefaultfieldvalue(tp.getDefaultfieldvalue());

        tableProperties.setModits(tp.isModits());
        tableProperties.setPk(tp.isPk());

        tableProperties.setSystemfield(tp.isSystemfield());

        tableProperties.setImpfield(tp.isImpfield());

        tablePropertiesRes.save(tableProperties);
        return request(super.pageTplResponse("redirect:/admin/metadata/table.html?id=" + tableProperties.getDbtableid()));
    }

    @RequestMapping("/delete")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView delete(@Valid String id) throws SQLException {
        MetadataTable table = metadataRes.findById(id).orElseThrow(() -> new RuntimeException("not found"));
        metadataRes.delete(table);
        return request(super.pageTplResponse("redirect:/admin/metadata/index.html"));
    }

    @RequestMapping("/batdelete")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView batdelete(@Valid String[] ids) throws SQLException {
        if (ids != null && ids.length > 0) {
            metadataRes.deleteAll(metadataRes.findAllById(Arrays.asList(ids)));
        }
        return request(super.pageTplResponse("redirect:/admin/metadata/index.html"));
    }

    @RequestMapping("/properties/delete")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView propertiesdelete(@Valid String id, @Valid String tbid) {
        TableProperties prop = tablePropertiesRes.findById(id).orElseThrow(() -> new RuntimeException("not found"));
        tablePropertiesRes.delete(prop);
        return request(super.pageTplResponse("redirect:/admin/metadata/table.html?id=" + (!StringUtils.isBlank(tbid) ? tbid : prop.getDbtableid())));
    }

    @RequestMapping("/properties/batdelete")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView propertiesbatdelete(@Valid String[] ids, @Valid String tbid) {
        if (ids != null && ids.length > 0) {
            tablePropertiesRes.deleteAll(tablePropertiesRes.findAllById(Arrays.asList(ids)));
        }
        return request(super.pageTplResponse("redirect:/admin/metadata/table.html?id=" + tbid));
    }

    @RequestMapping("/table")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView table(ModelMap map, @Valid String id) throws SQLException {
        map.addAttribute("propertiesList", tablePropertiesRes.findByDbtableid(id));
        map.addAttribute("tbid", id);
        map.addAttribute("table", metadataRes.findById(id).orElseThrow(() -> new RuntimeException("not found")));
        return request(super.createAdminTemplateResponse("/admin/system/metadata/table"));
    }

    @RequestMapping("/imptb")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView imptb(final ModelMap map) {

        Session session = (Session) em.getDelegate();
        session.doWork(connection -> {
            try {
                map.addAttribute("tablesList", DatabaseMetaDataHandler.getTables(connection));
            } catch (Exception e) {
                logger.error("When import metadata", e);
            }
        });

        return request(super
                .pageTplResponse("/admin/system/metadata/imptb"));
    }

    @RequestMapping("/imptbsave")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView imptb(HttpServletRequest request, final @Valid String[] tables) {
        final User user = super.getUser(request);
        if (tables != null && tables.length > 0) {
            Session session = (Session) em.getDelegate();
            session.doWork(
                    connection -> {
                        try {
                            for (String table : tables) {
                                int count = metadataRes.countByTablename(table);
                                if (count == 0) {
                                    MetadataTable metaDataTable = new MetadataTable();
                                    //当前记录没有被添加过，进行正常添加
                                    metaDataTable.setTablename(table);
                                    metaDataTable.setOrgi(user.getOrgi());
                                    metaDataTable.setId(MD5Utils.md5(metaDataTable.getTablename()));
                                    metaDataTable.setTabledirid("0");
                                    metaDataTable.setCreater(user.getId());
                                    metaDataTable.setCreatername(user.getUsername());
                                    metaDataTable.setName(table);
                                    metaDataTable.setUpdatetime(new Date());
                                    metaDataTable.setCreatetime(new Date());
                                    metadataRes.save(processMetadataTable(DatabaseMetaDataHandler.getTable(connection, metaDataTable.getTablename()), metaDataTable));
                                }
                            }
                        } catch (Exception e) {
                            logger.error("When import metadata", e);
                        }
                    }
            );

        }

        return request(super.pageTplResponse("redirect:/admin/metadata/index.html"));
    }

    private MetadataTable processMetadataTable(UKTableMetaData metaData, MetadataTable table) {
        table.setTableproperty(new ArrayList<>());
        if (metaData != null) {
            String tableName = metaData.getName().toLowerCase();
            for (UKColumnMetadata colum : metaData.getColumnMetadatas()) {
                String columName = colum.getName().toLowerCase();
                TableProperties properties = new TableProperties(columName, colum.getTypeName(), colum.getColumnSize(), tableName);
                properties.setOrgi(table.getOrgi());

                properties.setDatatypecode(0);
                properties.setLength(colum.getColumnSize());
                properties.setDatatypename(getDataTypeName(colum.getTypeName()));
                properties.setName(colum.getTitle().toLowerCase());
                String fieldName = properties.getFieldname();
                if (fieldName.equals("create_time") || fieldName.equals("createtime") || fieldName.equals("update_time")) {
                    properties.setDatatypename(getDataTypeName("datetime"));
                }
                properties.setFieldstatus(!columName.startsWith("field"));
                table.getTableproperty().add(properties);
            }
            // 转小写
            table.setTablename(table.getTablename().toLowerCase());
        }
        return table;
    }

    public String getDataTypeName(String type) {
        type = type.toLowerCase();
        String typeName = "text";
        if (type.contains("varchar")) {
            typeName = "text";
        } else if (type.equals("date") || type.equals("datetime")) {
            typeName = type.toLowerCase();
        } else if (type.equals("int") || type.equals("float") || type.equals("number")) {
            typeName = "number";
        }
        return typeName;
    }

    @RequestMapping("/clean")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView clean(@Valid String id) throws SQLException, BeansException, ClassNotFoundException {
        if (StringUtils.isNotBlank(id)) {
            MetadataTable table = metadataRes.findById(id).orElseThrow(() -> new RuntimeException("not found"));
            if (table.isFromdb() && StringUtils.isNotBlank(table.getListblocktemplet())) {
                SysDic dic = Dict.getInstance().getDicItem(table.getListblocktemplet());
                if (dic != null) {
                    Object bean = MainContext.getContext().getBean(Class.forName(dic.getCode()));
                    if (bean instanceof ElasticsearchRepository) {
                        ElasticsearchRepository<?, ?> jpa = (ElasticsearchRepository<?, ?>) bean;
                        jpa.deleteAll();
                    }
                }
            }
        }
        return request(super.pageTplResponse("redirect:/admin/metadata/index.html"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @RequestMapping("/synctoes")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView synctoes(@Valid String id) throws BeansException, ClassNotFoundException {
        ModelAndView modelAndView = request(super.pageTplResponse("redirect:/admin/metadata/index.html"));
        if (StringUtils.isBlank(id)) {
            return modelAndView;
        }
        MetadataTable table = metadataRes.findById(id).orElseThrow(() -> new RuntimeException("not found"));
        if (!table.isFromdb() || StringUtils.isBlank(table.getListblocktemplet())) {
            return modelAndView;
        }
        SysDic dic = Dict.getInstance().getDicItem(table.getListblocktemplet());

        if (dic != null) {
            Object bean = MainContext.getContext().getBean(Class.forName(dic.getCode()));
            if (bean instanceof ElasticsearchRepository) {
                ElasticsearchRepository jpa = (ElasticsearchRepository) bean;
                if (!StringUtils.isBlank(table.getPreviewtemplet())) {
                    SysDic jpaDic = Dict.getInstance().getDicItem(table.getPreviewtemplet());
                    List dataList = service.list(jpaDic.getCode());
                    List values = new CskefuList();
                    values.addAll(dataList);
                    if (dataList.size() > 0) {
                        jpa.save(values);
                    }
                }
            }
        }
        return modelAndView;
    }

    @SuppressWarnings({"rawtypes"})
    @RequestMapping("/synctodb")
    @Menu(type = "admin", subtype = "metadata", admin = true)
    public ModelAndView synctodb(@Valid String id) throws BeansException, ClassNotFoundException {
        if (!StringUtils.isBlank(id)) {
            MetadataTable table = metadataRes.findById(id).orElseThrow(() -> new RuntimeException("not found"));
            if (table.isFromdb() && !StringUtils.isBlank(table.getListblocktemplet())) {
                SysDic dic = Dict.getInstance().getDicItem(table.getListblocktemplet());

                if (dic != null) {
                    Object bean = MainContext.getContext().getBean(Class.forName(dic.getCode()));
                    if (bean instanceof ElasticsearchRepository) {
                        ElasticsearchRepository jpa = (ElasticsearchRepository) bean;
                        if (!StringUtils.isBlank(table.getPreviewtemplet())) {
                            Iterable dataList = jpa.findAll();
                            for (Object object : dataList) {
                                service.delete(object);
                                service.save(object);
                            }
                        }
                    }
                }
            }
        }
        return request(super.pageTplResponse("redirect:/admin/metadata/index.html"));
    }

}