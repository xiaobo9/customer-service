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
package com.chatopera.cc.util.dsdata;

import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.persistence.interfaces.DataExchangeInterface;
import com.chatopera.cc.util.Dict;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.JobDetailRepository;
import com.github.xiaobo9.repository.ReporterRepository;
import com.google.common.collect.ArrayListMultimap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ExcelImportProcess extends DataProcess {
    private final DecimalFormat format = new DecimalFormat("###");
    private final AtomicInteger pages = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private DSData dsData;
    private Reporter report;
    Map<Object, List> refValues = new HashMap<>();
    MetadataTable table;

    public ExcelImportProcess(DSDataEvent event) {
        super(event);
    }

    @Override
    public void process() {
        processExcel(event);
    }

    private void processExcel(final DSDataEvent event) {
        preProcess(event);

        File file = dsData.getFile();
        try (InputStream is = new FileInputStream(file);
             Workbook wb = isExcel2007(file.getName()) ? new XSSFWorkbook(is) : new HSSFWorkbook(is)) {
            process(wb);
        } catch (Exception e) {
            log.warn("", e);
        } finally {
            FileUtils.deleteQuietly(file);
            // 更新数据
            updateDBData();
            dsData.getProcess().end();
        }
    }

    private void preProcess(DSDataEvent event) {
        dsData = event.getDSData();
        report = dsData.getReport();
        report.setTableid(dsData.getTask().getId());
        if (dsData.getUser() != null) {
            report.setUserid(dsData.getUser().getId());
            report.setUsername(dsData.getUser().getUsername());
        }

        table = dsData.getTask();
        for (TableProperties tp : table.getTableproperty()) {
            if (tp.isReffk() && StringUtils.isNotBlank(tp.getReftbid())) {
                DataExchangeInterface exchange = (DataExchangeInterface) MainContext.getContext().getBean(tp.getReftbid());
                refValues.put(tp.getFieldname(), exchange.getListDataByIdAndOrgi(null, null, event.getOrgi()));
            }
        }
    }

    private void process(Workbook wb) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, ParseException {
        // 需要检查Mapping 是否存在
        long start = System.currentTimeMillis();

        Sheet sheet = wb.getSheetAt(0);
        Row titleRow = sheet.getRow(0);
        int totalRows = sheet.getPhysicalNumberOfRows();
        int colNum = titleRow.getPhysicalNumberOfCells();
        for (int i = 1; i < totalRows; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Object data = null;
            if (dsData.getClazz() != null) {
                data = dsData.getClazz().newInstance();
            }
            Map<Object, Object> values = new HashMap<>();
            ArrayListMultimap<String, Object> multiValues = ArrayListMultimap.create();
            boolean skipDataVal = false; //跳过数据校验
            StringBuilder pkStr = new StringBuilder(), allStr = new StringBuilder();
            for (int col = 0; col < colNum; col++) {
                Cell value = row.getCell(col);
                Cell title = titleRow.getCell(col);
                String titleValue = getValue(title);
                TableProperties tableProperties = getTableProperties(titleValue);
                if (tableProperties == null || value == null) {
                    continue;
                }
                String valuestr = getValue(value);
                if (StringUtils.isNotBlank(valuestr)) {
                    String fieldName = tableProperties.getFieldname();
                    if (tableProperties.isModits()) {
                        if (StringUtils.isNotBlank(valuestr)) {
                            multiValues.put(fieldName, valuestr);
                        }
                    } else {
                        if (tableProperties.isSeldata()) {
                            SysDic sysDic = Dict.getInstance().getDicItem(valuestr);
                            if (sysDic != null) {
                                values.put(fieldName, sysDic.getName());
                            } else {
                                List<SysDic> dicItemList = Dict.getInstance().getSysDic(tableProperties.getSeldatacode());
                                if (dicItemList != null && dicItemList.size() > 0) {
                                    for (SysDic dicItem : dicItemList) {
                                        if (dicItem.getName().equals(valuestr)) {
                                            values.put(fieldName, dicItem.isDiscode() ? dicItem.getCode() : dicItem.getId());
                                            break;
                                        }
                                    }
                                }
                            }
                        } else if (tableProperties.isReffk() && refValues.get(fieldName) != null) {
                            List keys = refValues.get(fieldName);
                            if (keys != null) {
                                values.put(fieldName, getRefid(keys, valuestr));
                            }
                        } else {
                            values.put(fieldName, valuestr);
                        }
                        if (tableProperties.isPk() && !fieldName.equalsIgnoreCase("id")) {
                            pkStr.append(valuestr);
                        }
                    }
                    allStr.append(valuestr);
                }
                report.setBytes(report.getBytes() + valuestr.length());
                report.getAtompages().incrementAndGet();
            }
            values.put("orgi", event.getOrgi());
            if (values.get("id") == null) {
                if (pkStr.length() > 0) {
                    values.put("id", MD5Utils.md5(pkStr.append(dsData.getTask().getTablename()).toString()));
                } else {
                    values.put("id", MD5Utils.md5(allStr.append(dsData.getTask().getTablename()).toString()));
                }
            }
            if (event.getValues() != null && event.getValues().size() > 0) {
                values.putAll(event.getValues());
            }
            values.putAll(multiValues.asMap());
            String validFaildMessage = null;
            for (TableProperties tp : table.getTableproperty()) {
                String title = tp.getDefaultvaluetitle();
                if (!StringUtils.isBlank(title)) {
                    String valuestr = (String) values.get(tp.getFieldname());
                    if (title.contains("required") && StringUtils.isBlank(valuestr)) {
                        skipDataVal = true;
                        validFaildMessage = "required";
                        break;
                    } else if (valuestr != null && (title.contains("numstr") && !valuestr.matches("[\\d]+"))) {
                        skipDataVal = true;
                        validFaildMessage = "numstr";
                        break;
                    } else if (valuestr != null && (title.contains("datenum") || title.contains("datetime"))) {
                        if (!valuestr.matches("[\\d]{4}-[\\d]{2}-[\\d]{2}") && !valuestr.matches("[\\d]{4}-[\\d]{2}-[\\d]{2} [\\d]{2}:[\\d]{2}:[\\d]{2}")) {
                            skipDataVal = true;
                            validFaildMessage = "datenum";
                            break;
                        } else {
                            if (valuestr.matches("[\\d]{4}-[\\d]{2}-")) {
                                if ("date".equals(tp.getDefaultfieldvalue())) {
                                    values.put(tp.getFieldname(), DateFormatEnum.DAY.parse(valuestr));
                                } else {
                                    values.put(tp.getFieldname(), DateFormatEnum.DAY.format(DateFormatEnum.DAY.parse(valuestr)));
                                }
                            } else if (valuestr.matches("[\\d]{4}-[\\d]{2}-[\\d]{2} [\\d]{2}:[\\d]{2}:[\\d]{2}")) {
                                if ("date".equals(tp.getDefaultfieldvalue())) {
                                    values.put(tp.getFieldname(), DateFormatEnum.DAY_TIME.parse(valuestr));
                                } else {
                                    values.put(tp.getFieldname(), DateFormatEnum.DAY.format(DateFormatEnum.DAY_TIME.parse(valuestr)));
                                }

                            }
                        }
                    }
                }
                if (tp.isReffk() && !StringUtils.isBlank(tp.getReftbid()) && refValues.get(tp.getFieldname()) == null) {
                    DataExchangeInterface exchange = (DataExchangeInterface) MainContext.getContext().getBean(tp.getReftbid());
                    exchange.process(data, event.getOrgi());
                }
            }

            if (!values.containsKey("orgi")) {
                skipDataVal = true;
            }
            report.setTotal(pages.intValue());
            values.put("creater", event.getValues().get("creater"));
            values.put("organ", event.getValues().get("organ"));
            if (data != null && !skipDataVal) {
                populate(data, values);
                pages.incrementAndGet();
                dsData.getProcess().process(data);
            } else if (data == null) {
                // 导入的数据，只写入ES
                if (skipDataVal) {    //跳过
                    values.put("status", "invalid");
                    values.put("validresult", "invalid");
                    values.put("validmessage", validFaildMessage != null ? validFaildMessage : "");
                } else {
                    values.put("validresult", "valid");
                }
                values.put("status", Enums.NamesDisStatusType.NOT.toString());
                values.put("batid", event.getBatid());

                values.put("createtime", System.currentTimeMillis());
                values.put("callstatus", Enums.NameStatusType.NOTCALL.toString());
                values.put("execid", report.getId());

                if (i % 500 == 0) {
                    MainContext.getContext().getBean(ReporterRepository.class).save(report);
                }

                if (values.get("cusid") == null) {
                    values.put("cusid", values.get("id"));
                }
                pages.incrementAndGet();
                dsData.getProcess().process(values);

            }
            if (skipDataVal) {    //跳过
                errors.incrementAndGet();
            }
        }

        event.setTimes(System.currentTimeMillis() - start);
        report.setEndtime(new Date());
        report.setAmount(String.valueOf((float) event.getTimes() / 1000f));
        report.setStatus(Enums.TaskStatusType.END.getType());
        report.setTotal(pages.intValue());
        report.setPages(pages.intValue());
        report.setErrors(errors.intValue());
    }

    private void updateDBData() {
        MainContext.getContext().getBean(ReporterRepository.class).save(report);
        if (dsData.getClazz() == null && !StringUtils.isBlank(event.getBatid())) {
            JobDetailRepository batchRes = MainContext.getContext().getBean(JobDetailRepository.class);
            JobDetail batch = dsData.getJobDetail();
            if (batch == null) {
                batch = batchRes.findByIdAndOrgi(event.getBatid(), event.getOrgi());
            }
            if (batch != null) {
                batch.setNamenum(batch.getNamenum() + pages.intValue());
                batch.setValidnum(batch.getValidnum() + (pages.intValue() - errors.intValue()));
                batch.setInvalidnum(batch.getInvalidnum() + errors.intValue());
                batch.setExecnum(batch.getExecnum() + 1);
                batch.setNotassigned(batch.getNotassigned() + (pages.intValue() - errors.intValue()));
                batchRes.save(batch);
            }
        }
    }

    public static void populate(Object bean, Map<Object, Object> properties) throws IllegalAccessException, InvocationTargetException {
        ConvertUtils.register(new Converter() {
            @Override
            public Object convert(Class type, Object value) {
                if (value == null) {
                    return null;
                }
                if (value instanceof Date) {
                    return value;
                }
                if (!(value instanceof String)) {
                    throw new ConversionException("只支持字符串转换 !");
                }
                String str = (String) value;
                if (str.trim().equals("")) {
                    return null;
                }
                try {
                    return DateFormatEnum.DAY_TIME.parse(str);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }

        }, Date.class);
        if (properties == null || bean == null) {
            return;
        }
        try {
            BeanUtilsBean.getInstance().populate(bean, properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getRefid(List<Object> dataList, String value) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String id = "";
        for (Object data : dataList) {
            if (PropertyUtils.isReadable(data, "name")) {
                Object target = BeanUtils.getProperty(data, "name");
                if (target != null && target.equals(value)) {
                    id = BeanUtils.getProperty(data, "id");
                }
            }
            if (PropertyUtils.isReadable(data, "tag")) {
                Object target = BeanUtils.getProperty(data, "tag");
                if (target != null && target.equals(value)) {
                    id = BeanUtils.getProperty(data, "id");
                }
            }
            if (StringUtils.isBlank(id) && PropertyUtils.isReadable(data, "title")) {
                Object target = BeanUtils.getProperty(data, "title");
                if (target != null && target.equals(value)) {
                    id = BeanUtils.getProperty(data, "id");
                }
            }
            if (StringUtils.isBlank(id)) {
                Object target = BeanUtils.getProperty(data, "id");
                if (target != null && target.equals(value)) {
                    id = target.toString();
                }
            }
        }
        return id;
    }

    private TableProperties getTableProperties(String title) {
        TableProperties tableProperties = null;
        for (TableProperties tp : dsData.getTask().getTableproperty()) {
            if (tp.getName().equals(title) || tp.getFieldname().equals(title)) {
                tableProperties = tp;
                break;
            }
        }
        return tableProperties;
    }

    private boolean isExcel2007(String fileName) {
        return fileName.matches("^.+\\.(?i)(xlsx)$");
    }

    private String getValue(Cell cell) {
        String strCell = "";
        if (cell != null) {
            short dt = cell.getCellStyle().getDataFormat();
            switch (cell.getCellTypeEnum()) {
                case STRING:
                    strCell = cell.getStringCellValue();
                    break;
                case BOOLEAN:
                    strCell = String.valueOf(cell.getBooleanCellValue());
                    break;
                case BLANK:
                    strCell = "";
                    break;
                case NUMERIC:
                    if (HSSFDateUtil.isCellDateFormatted(cell)) {
                        strCell = DateFormatEnum.DAY_TIME.format(cell.getDateCellValue());
                    } else if (cell.getCellStyle().getDataFormat() == 58) {
                        double value = cell.getNumericCellValue();
                        strCell = DateFormatEnum.DAY_TIME.format(DateUtil.getJavaDate(value));
                    } else {
                        if (HSSFDateUtil.isCellDateFormatted(cell)) {
                            strCell = DateFormatEnum.DAY_TIME.format(cell.getDateCellValue());
                        } else {
                            boolean isNumber = isNumberFormat(dt);
                            if (isNumber) {
                                DecimalFormat numberFormat = getNumberFormat(cell.getCellStyle().getDataFormatString());
                                if (numberFormat != null) {
                                    strCell = String.valueOf(numberFormat.format(cell.getNumericCellValue()));
                                } else {
                                    strCell = String.valueOf(cell.getNumericCellValue());
                                }
                            } else {
                                strCell = String.valueOf(format.format(cell.getNumericCellValue()));
                            }
                        }
                    }
                    break;
                case FORMULA: {
                    // 判断当前的cell是否为Date
                    boolean isNumber = isNumberFormat(dt);
                    try {
                        if (isNumber) {
                            strCell = String.valueOf(cell.getNumericCellValue());
                        } else {
                            strCell = "";
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        strCell = cell.getRichStringCellValue().getString();
                    }
                    break;
                }
                default:
                    strCell = "";
                    break;
            }
            if (strCell == null || strCell.equals("")) {
                return "";
            }
        }
        return strCell;
    }

    private DecimalFormat getNumberFormat(String dateformat) {
        DecimalFormat numberFormat = null;
        int index = dateformat.indexOf("_") > 0 ? dateformat.indexOf("_") : dateformat.indexOf(";");
        if (index > 0) {
            String format = dateformat.substring(0, index);
            if (format.matches("[\\d.]+")) {
                numberFormat = new DecimalFormat(format);
            }
        }

        return numberFormat;
    }

    private boolean isNumberFormat(short dataType) {
        boolean number = false;
        switch (dataType) {
            case 180:
            case 181:
            case 182:
            case 178:
            case 177:
            case 176:
            case 183:
            case 185:
            case 186:
            case 179:
            case 187:
            case 7:
            case 8:
            case 44:
            case 10:
            case 12:
            case 13:
            case 188:
            case 189:
            case 190:
            case 191:
            case 192:
            case 193:
            case 194:
            case 11:
                number = true;
                break;
        }
        return number;
    }
}
