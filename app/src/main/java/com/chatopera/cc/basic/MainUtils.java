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
package com.chatopera.cc.basic;

import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.service.SystemConfigService;
import com.chatopera.cc.service.TemplateService;
import com.chatopera.cc.util.CronTools;
import com.chatopera.cc.util.Dict;
import com.chatopera.cc.util.WebIMReport;
import com.chatopera.cc.util.WeiXinReport;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.xiaobo9.bean.JobTask;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.kit.ObjectKit;
import com.github.xiaobo9.commons.mail.MailSender;
import com.github.xiaobo9.commons.utils.MD5Utils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.repository.*;
import com.googlecode.aviator.AviatorEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jasypt.util.text.BasicTextEncryptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MainUtils {

    private static final Random random = new Random();

    public static SimpleDateFormat timeRangeDateFormat = new SimpleDateFormat("HH:mm");

    public static void copyProperties(Object source, Object target, String... ignoreProperties)
            throws BeansException {

        Assert.notNull(source, "Source must not be null");
        Assert.notNull(target, "Target must not be null");

        Class<?> actualEditable = target.getClass();
        PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(actualEditable);
        List<String> ignoreList = (ignoreProperties != null) ? Arrays.asList(ignoreProperties) : null;

        for (PropertyDescriptor targetPd : targetPds) {
            Method writeMethod = targetPd.getWriteMethod();
            if (writeMethod != null && (ignoreProperties == null || (!ignoreList.contains(targetPd.getName())))) {
                PropertyDescriptor sourcePd = BeanUtils.getPropertyDescriptor(source.getClass(), targetPd.getName());
                if (sourcePd != null && !targetPd.getName().equalsIgnoreCase("id")) {
                    Method readMethod = sourcePd.getReadMethod();
                    if (readMethod != null &&
                            ClassUtils.isAssignable(writeMethod.getParameterTypes()[0], readMethod.getReturnType())) {
                        try {
                            if (!Modifier.isPublic(readMethod.getDeclaringClass().getModifiers())) {
                                readMethod.setAccessible(true);
                            }
                            Object value = readMethod.invoke(source);
                            if (value != null) {  //只拷贝不为null的属性 by zhao
                                if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
                                    writeMethod.setAccessible(true);
                                }
                                writeMethod.invoke(target, value);
                            }
                        } catch (Throwable ex) {
                            throw new FatalBeanException(
                                    "Could not copy property '" + targetPd.getName() + "' from source to target", ex);
                        }
                    }
                }
            }
        }
    }

    /***
     * ID编码 ， 发送对话的时候使用
     */
    public static String genNewID(String id, String nid) {
        StringBuilder strb = new StringBuilder();
        if (id != null && nid != null) {
            int length = Math.max(id.length(), nid.length());
            for (int i = 0; i < length; i++) {
                if (nid.length() > i && id.length() > i) {
                    int cur = (id.charAt(i) + nid.charAt(i)) / 2;
                    strb.append((char) cur);
                } else if (nid.length() > i) {
                    strb.append(nid.charAt(i));
                } else {
                    strb.append(id.charAt(i));
                }
            }
        }
        return strb.toString();
    }

    /**
     * @param request
     * @return
     */
    public static String getParameter(HttpServletRequest request) {
        Enumeration<String> names = request.getParameterNames();
        StringBuilder strb = new StringBuilder();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!name.contains("password")) {    //不记录 任何包含 password 的参数内容
                if (strb.length() > 0) {
                    strb.append(",");
                }
                strb.append(name).append("=").append(request.getParameter(name));
            }
        }
        return strb.toString();

    }

    /**
     * 获取一天的开始时间
     *
     * @return
     */
    public static Date getStartTime() {
        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        return todayStart.getTime();
    }

    /**
     * 获取一天的开始时间
     *
     * @return
     */
    public static Date getWeekStartTime() {
        Calendar weekStart = Calendar.getInstance();
        weekStart.set(
                weekStart.get(Calendar.YEAR), weekStart.get(Calendar.MONTH), weekStart.get(Calendar.DAY_OF_MONTH), 0,
                0, 0);
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return weekStart.getTime();
    }

    /**
     * 获取一天的开始时间
     *
     * @return
     */
    public static Date getLast30Day() {
        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.DAY_OF_MONTH, -30);
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        return todayStart.getTime();
    }

    /**
     * 获取一天的开始时间
     *
     * @return
     */
    public static Date getLastDay(int days) {
        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.DAY_OF_MONTH, -days);
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        return todayStart.getTime();
    }

    /**
     * 获取一天的结束时间
     *
     * @return
     */
    public static Date getEndTime() {
        Calendar todayEnd = Calendar.getInstance();
        todayEnd.set(Calendar.HOUR_OF_DAY, 23);
        todayEnd.set(Calendar.MINUTE, 59);
        todayEnd.set(Calendar.SECOND, 59);
        todayEnd.set(Calendar.MILLISECOND, 999);
        return todayEnd.getTime();
    }

    /**
     * 获取一天的结束时间
     *
     * @return
     */
    public static Date getLastTime(int secs) {
        Calendar todayEnd = Calendar.getInstance();
        todayEnd.add(Calendar.SECOND, secs * -1);
        return todayEnd.getTime();
    }

    /**
     * 活动JPA统计结果
     *
     * @param values
     * @return
     */
    public static WebIMReport getWebIMReport(List<Object> values) {
        WebIMReport report = new WebIMReport();
        if (values != null && values.size() > 0) {
            Object[] value = (Object[]) values.get(0);
            if (value.length >= 2) {
                report.setIpnums((long) value[0]);
                report.setPvnums((long) value[1]);
            }
        }
        return report;
    }

    /**
     * 活动JPA统计结果
     *
     * @param values
     * @return
     */
    public static WebIMReport getWebIMInviteStatus(List<Object> values) {
        WebIMReport report = new WebIMReport();
        if (values != null && values.size() > 0) {

            for (int i = 0; i < values.size(); i++) {
                Object[] value = (Object[]) values.get(i);
                if (value.length >= 2) {
                    String invitestatus = (String) value[0];
                    if (Enums.OnlineUserInviteStatus.DEFAULT.toString().equals(
                            invitestatus) || invitestatus == null) {
                        report.setUsers((long) value[1]);
                    } else if (Enums.OnlineUserInviteStatus.INVITE.toString().equals(invitestatus)) {
                        report.setInviteusers((long) value[1]);
                    } else if (Enums.OnlineUserInviteStatus.REFUSE.toString().equals(invitestatus)) {
                        report.setRefuseusers((long) value[1]);
                    }
                }
            }
        }
        return report;
    }

    /**
     * 活动JPA统计结果
     *
     * @param values
     * @return
     */
    public static List<WebIMReport> getWebIMInviteAgg(List<Object> values) {
        List<WebIMReport> webIMReportList = new ArrayList<WebIMReport>();
        if (values != null && values.size() > 0) {
            for (int i = 0; i < values.size(); i++) {
                Object[] value = (Object[]) values.get(i);
                WebIMReport report = new WebIMReport();
                if (value.length == 3) {
                    report.setData((String) value[0]);
                    report.setIpnums((long) value[1]);
                    report.setPvnums((long) value[2]);
                }
                webIMReportList.add(report);
            }
        }
        return webIMReportList;
    }

    /**
     * 活动JPA统计结果
     *
     * @param values
     * @return
     */
    public static List<WebIMReport> getWebIMDataAgg(List<Object> values) {
        List<WebIMReport> webIMReportList = new ArrayList<WebIMReport>();
        if (values != null && values.size() > 0) {
            for (int i = 0; i < values.size(); i++) {
                Object[] value = (Object[]) values.get(i);
                WebIMReport report = new WebIMReport();
                if (value.length == 2) {
                    if (value[0] == null || value[0].toString().equalsIgnoreCase("null") || StringUtils.isBlank(value[0].toString())) {
                        report.setData("其他");
                    } else {
                        report.setData((String) value[0]);
                    }
                    report.setUsers((long) value[1]);
                }
                webIMReportList.add(report);
            }
        }
        return webIMReportList;
    }

    /**
     * 活动JPA统计结果
     *
     * @param values
     * @return
     */
    public static WebIMReport getWebIMInviteResult(List<Object> values) {
        WebIMReport report = new WebIMReport();
        if (values != null && values.size() > 0) {

            for (int i = 0; i < values.size(); i++) {
                Object[] value = (Object[]) values.get(i);
                if (value.length >= 2) {
                    String invitestatus = (String) value[0];
                    if (Enums.OnlineUserInviteStatus.DEFAULT.toString().equals(
                            invitestatus) || invitestatus == null) {
                        report.setUsers((long) value[1]);
                    } else if (Enums.OnlineUserInviteStatus.ACCEPT.toString().equals(invitestatus)) {
                        report.setInviteusers((long) value[1]);
                    } else if (Enums.OnlineUserInviteStatus.REFUSE.toString().equals(invitestatus)) {
                        report.setRefuseusers((long) value[1]);
                    }
                }
            }
        }
        return report;
    }

    /**
     * 活动JPA统计结果
     *
     * @param values
     * @return
     */
    public static WeiXinReport getWeiXinReportResult(List<Object> values) {
        WeiXinReport report = new WeiXinReport();
        if (values != null && values.size() > 0) {
            for (int i = 0; i < values.size(); i++) {
                Object[] value = (Object[]) values.get(i);
                if (value.length >= 2) {
                    String event = (String) value[0];
                    if (Enums.WeiXinEventType.SUB.toString().equals(event)) {
                        report.setSubs((long) value[1]);
                    } else if (Enums.WeiXinEventType.UNSUB.toString().equals(event)) {
                        report.setUnsubs((long) value[1]);
                    }
                }
            }
        }
        return report;
    }

    public static String encryption(final String str) {
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        textEncryptor.setPassword(MainContext.getSystemSecrityPassword());
        return textEncryptor.encrypt(str);
    }

    public static String decryption(final String str) throws NoSuchAlgorithmException {
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        textEncryptor.setPassword(MainContext.getSystemSecrityPassword());
        return textEncryptor.decrypt(str);
    }

    public static String getTopic(final String snsid, final String msgtype, final String eventype, final String eventkey, final String msg) {
        StringBuilder strb = new StringBuilder()
                .append(snsid).append(".").append(msgtype);
        if (msgtype.equals("text")) {
            strb.append(".").append(msg);
        } else if (msgtype.equals("exchange")) {
            strb.append(".").append(eventype.toLowerCase());
            if (StringUtils.isNotBlank(eventkey)) {
                strb.append(".").append(eventkey);
            }
        } else {
            strb.append(".").append(msgtype);
        }
        return strb.toString();
    }

    public static String getTopic(String snsid, String msgtype, String eventype) {
        StringBuilder strb = new StringBuilder();
        strb.append(snsid);
        strb.append(".").append(msgtype);
        if (msgtype.equals("text")) {
            strb.append(".").append(msgtype);
        } else if (msgtype.equals("exchange")) {
            strb.append(".").append(eventype.toLowerCase());
        } else {
            strb.append(".").append(msgtype);
        }
        return strb.toString();
    }

    /**
     * 处理 对话消息中的图片
     */
    public static String filterChatMessage(String message) {
        Document document = Jsoup.parse(message);
        Elements pngs = document.select("img[src]");
        for (Element element : pngs) {
            String imgUrl = element.attr("src");
            if (imgUrl.contains("/res/image")) {
                element.attr("class", "ukefu-media-image");
            }
        }
        return document.html();
    }

    /**
     * 检查当前时间是否是在 时间范围内 ，时间范围的格式为 ： 08:30~11:30,13:30~17:30
     */
    public static boolean isInWorkingHours(String timeRanges) {
        String timeStr = timeRangeDateFormat.format(new Date());
        if (StringUtils.isBlank(timeRanges)) {
            return true;
        }

        //设置了 工作时间段 将 检查结果设置为 False ， 如果当前时间是在 时间范围内，则 置为 True
        boolean workingTime = false;
        String[] timeRange = timeRanges.split(",");
        for (String tr : timeRange) {
            String[] timeGroup = tr.split("~");
            if (timeGroup.length == 2) {
                if (timeGroup[0].compareTo(timeGroup[1]) >= 0) {
                    if (timeStr.compareTo(timeGroup[0]) >= 0 || timeStr.compareTo(timeGroup[1]) <= 0) {
                        return true;
                    }
                } else {
                    if (timeStr.compareTo(timeGroup[0]) >= 0 && timeStr.compareTo(timeGroup[1]) <= 0) {
                        return true;
                    }
                }
            }
        }
        return workingTime;
    }

    public static String processEmoti(String message) {
        Pattern pattern = Pattern.compile("\\[([\\d]*?)]");
        SystemConfig systemConfig = MainContext.getCache().findOneSystemByIdAndOrgi("systemConfig", Constants.SYSTEM_ORGI);

        Matcher matcher = pattern.matcher(message);
        StringBuffer strb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (systemConfig != null && StringUtils.isNotBlank(systemConfig.getIconstr())) {
                String s = "<img src='" + systemConfig.getIconstr() + "/im/js/kindeditor/plugins/emoticons/images/" + name + ".png'>";
                matcher.appendReplacement(strb, s);
            } else {
                matcher.appendReplacement(strb, "<img src='/im/js/kindeditor/plugins/emoticons/images/" + name + ".png'>");
            }
        }
        matcher.appendTail(strb);
        if (strb.length() == 0) {
            strb.append(message);
        }
        return strb.toString().replaceAll("[\\ud800\\udc00-\\udbff\\udfff\\ud800-\\udfff]", "[表情]");
    }

    public static boolean secConfirm(String confirm, List<Secret> secretConfig) {
        // 先调用 IMServer
        boolean execute = false;
        if (StringUtils.isNotBlank(confirm)) {
            if (secretConfig != null && secretConfig.size() > 0) {
                Secret secret = secretConfig.get(0);
                if (MD5Utils.md5(confirm).equals(secret.getPassword())) {
                    execute = true;
                }
            }
        } else if (secretConfig.size() == 0) {
            execute = true;
        }
        return execute;
    }

    /**
     * 初始化呼叫中心功能里需要隐藏号码的字段
     */
    public static void initSystemSecField(TablePropertiesRepository tpRes) {
        if (tpRes != null) {
            List<TableProperties> tpList = tpRes.findBySecfield(true);
            MainContext.getCache().putSystemListByIdAndOrgi(
                    Constants.CSKEFU_SYSTEM_SECFIELD, Constants.SYSTEM_ORGI, tpList);
        }
    }

    /**
     * 获取系统地区配置
     */
    public static void initSystemArea() {
        MainContext.getCache().deleteSystembyIdAndOrgi(Constants.CSKEFU_SYSTEM_AREA, Constants.SYSTEM_ORGI);
        AreaTypeRepository areaTypeRes = MainContext.getContext().getBean(AreaTypeRepository.class);
        MainContext.getCache().putSystemListByIdAndOrgi(
                Constants.CSKEFU_SYSTEM_AREA, Constants.SYSTEM_ORGI, areaTypeRes.findAll());
    }

    /**
     * 缓存 广告位
     */
    public static void initAdv(String orgi, String skill) {
        MainContext.getCache().deleteSystembyIdAndOrgi(Constants.CSKEFU_SYSTEM_ADV + "_" + skill, orgi);
        AdTypeRepository adRes = MainContext.getContext().getBean(AdTypeRepository.class);
        MainContext.getCache().putSystemListByIdAndOrgi(
                Constants.CSKEFU_SYSTEM_ADV + "_" + skill, orgi, adRes.findByOrgiAndSkill(orgi, skill));
    }

    public static Template getTemplate(String id) {
        return MainContext.getContext().getBean(TemplateService.class).getTemplate(id);
    }

    /**
     * 按照权重获取广告
     */
    public static AdType getPointAdv(String adpos, String skill, String orgi) {
        CacheService cache = MainContext.getCache();
        List<AdType> adTypes = cache.findOneSystemListByIdAndOrgi(Constants.CSKEFU_SYSTEM_ADV + "_" + skill, orgi);
        if (adTypes == null) {
            AdTypeRepository adRes = MainContext.getContext().getBean(AdTypeRepository.class);
            adTypes = adRes.findByOrgiAndSkill(orgi, skill);
            cache.putSystemListByIdAndOrgi(Constants.CSKEFU_SYSTEM_ADV + "_" + skill, orgi, adTypes);
        }
        List<SysDic> sysDicList = Dict.getInstance().getDic(Constants.CSKEFU_SYSTEM_ADPOS_DIC);
        SysDic sysDic = null;
        for (SysDic dic : sysDicList) {
            if (dic.getCode().equals(adpos)) {
                sysDic = dic;
                break;
            }
        }
        List<AdType> adTypeList = new ArrayList<>();
        if (sysDic != null) {
            for (AdType adType : adTypes) {
                if (adType.getAdpos().equals(sysDic.getId())) {
                    adTypeList.add(adType);
                }
            }
        }
        return weight(adTypeList);
    }

    /**
     * 按照权重，获取广告内容
     */
    private static AdType weight(List<AdType> adList) {
        if (adList == null || adList.isEmpty()) {
            return null;
        }
        int weight = 0;
        for (AdType ad : adList) {
            weight += ad.getWeight();
        }
        AdType adType = null;
        int n = random.nextInt(weight), m = 0;
        for (AdType ad : adList) {
            if (m <= n && n < m + ad.getWeight()) {
                adType = ad;
                break;
            }
            m += ad.getWeight();
        }
        return adType;
    }

    /**
     * 16进制字符串转换为字符串
     *
     * @return
     */
    public static String string2HexString(String strPart) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < strPart.length(); i++) {
            int ch = (int) strPart.charAt(i);
            String strHex = Integer.toHexString(ch);
            hexString.append(strHex);
        }
        return hexString.toString();
    }

    /**
     * 发送邮件
     *
     * @param email
     * @param cc
     * @param subject
     * @param content
     * @throws Exception
     */
    public static void sendMail(String email, String cc, String subject, String content, List<String> filenames) throws Exception {
        SystemConfig config = MainContext.getContext().getBean(SystemConfigService.class).getSystemConfig();
        if (config.isEnablemail() && config.getEmailid() != null) {
            SystemMessage systemMessage = MainContext.getContext().getBean(
                    SystemMessageRepository.class).findByIdAndOrgi(config.getEmailid(), config.getOrgi());
            MailSender sender = new MailSender(
                    systemMessage.getSmtpserver(), systemMessage.getMailfrom(), systemMessage.getSmtpuser(),
                    decryption(systemMessage.getSmtppassword()), systemMessage.getSeclev(), systemMessage.getSslport());
            if (email != null) {
                sender.send(email, cc, subject, content, filenames);
            }
        }
    }

    public static String encode(Object obj) {
        Base64 base64 = new Base64();
        try {
            return base64.encodeToString(ObjectKit.toBytes(obj));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String processContentEncode(String str) throws Exception {
        return Base64.encodeBase64String(str.getBytes(StandardCharsets.UTF_8)).replaceAll("\\+", "-");
    }

    public static String processContentDecode(String str) throws Exception {
        return new String(Base64.decodeBase64(str.replaceAll("-", "\\+").getBytes()), StandardCharsets.UTF_8);
    }

    /**
     * @param defaultFormatValue
     * @param text
     * @return
     */
    public static String processParam(String defaultFormatValue, String text) {
        if (defaultFormatValue != null && defaultFormatValue.length() > 0) {
            return getDays(text, defaultFormatValue);
        }
        String formatValue = "yyyy-MM-dd";
        if (text.matches("[ ]*([Yy]+)[ ]*[+-]?([\\d]*)")) {
            formatValue = "yyyy";
        } else if (text.matches("[ ]*([Mm]+)[ ]*[+-]?([\\d]*)")) {
            formatValue = "yyyy-MM";
        }
        return getDays(text, formatValue);
    }

    /***
     * 计算T+1
     * @param text
     * @param format
     * @return
     */
    public static String getDays(String text, String format) {
        Pattern pattern = Pattern.compile("[ ]*([TtMmYy]+)[ ]*[+-]?([\\d]*)");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find() || matcher.groupCount() == 0) {
            return text;
        }
        String group = matcher.group(1);
        try {
            if (group.equalsIgnoreCase("T")) {
                return formatDateValue(format, getDaysParam(text));
            }
            if (group.equalsIgnoreCase("M")) {
                return formatMonthValue(format, getDaysParam(text));
            }
            if (group.equalsIgnoreCase("Y")) {
                return String.valueOf((int) Double.parseDouble(String.valueOf(getDaysParam(text))));
            }
        } catch (ParseException e) {
            log.warn("", e);
        }
        return text;
    }

    /***
     * 计算T+1
     * @param text text
     * @return
     */
    private static Object getDaysParam(String text) {
        Map<String, Object> context = new HashMap<>();
        context.put("T", processDays());
        context.put("t", processDays());
        context.put("M", processMonth());
        context.put("m", processMonth());
        context.put("Y", processYear());
        context.put("y", processYear());

        return AviatorEvaluator.execute(text, context);
    }

    /**
     * @param value
     * @return
     * @throws ParseException
     * @throws Exception
     */
    public static String formatDateValue(String format, Object value) throws ParseException {
        if (value != null && value.toString().matches("[\\d.]{5,}")) {
            value = new SimpleDateFormat(format).format(
                    new Date((long) (Double.parseDouble(value.toString()) * 24 * 60 * 60 * 1000)));
        }
        return value != null ? value.toString() : "0";
    }

    /**
     * @param value
     * @return
     * @throws ParseException
     * @throws Exception
     */
    public static String formatMonthValue(String formatValue, Object value) throws ParseException {
        if (value != null && value.toString().matches("[\\d.]{3,}")) {
            int months = (int) Double.parseDouble(String.valueOf(value));
            int year = 0;
            int month = 0;
            if (months % 12 == 0) {
                year = months / 12 - 1;
                month = 12;
            } else {
                year = months / 12;
                month = months % 12;
            }
            if (month < 10) {
                value = year + "0" + month;
            } else {
                value = String.valueOf(year) + month;
            }
            value = new SimpleDateFormat(formatValue).format(
                    new SimpleDateFormat("yyyyMM").parse(String.valueOf(value)));
        }
        return value != null ? value.toString() : "0";
    }

    /**
     * @return
     */
    public static double processDays() {
        return System.currentTimeMillis() * 1.0f / (1000 * 60 * 60 * 24);
    }

    /**
     * @return
     */
    public static double processMonth() {
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH) + 1;
        return month;
    }

    /**
     * @return
     */
    public static double processYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        JSON.setSerializationInclusion(Include.NON_NULL);
        JSON.configure(SerializationFeature.INDENT_OUTPUT, Boolean.TRUE);
    }

    public static String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String convertCrond(JobTask plan) {

        String minuteRepeat = "";
        String hourRepeat = "";

        if (plan.getIsRepeat()) {
            minuteRepeat = plan.getRepeatSpace() != null && plan.getRepeatSpace() < 60 ? "/" + plan.getRepeatSpace() : "";
            hourRepeat = plan.getRepeatSpace() != null && plan.getRepeatSpace() > 60 ? "/" + plan.getRepeatSpace() / 60
                    : (plan.getRepeatJustTime() != null && plan.getRepeatJustTime() > 0 ? "-" + (plan.getRunBeginHour() + plan.getRepeatJustTime())
                    : "");
        }

        StringBuilder builder = new StringBuilder()
                .append(plan.getRunBeginSecond()).append(" ")
                .append(plan.getRunBeginMinute()).append(minuteRepeat).append(" ")
                .append(plan.getRunBeginHour()).append(hourRepeat).append(" ");

        if ("day".equals(plan.getRunCycle())) {
            builder.append("*")
                    .append(plan.getRunSpace() != null && plan.getRunSpace() > 0 ? "/" + plan.getRunSpace() : "")
                    .append(" ")
                    .append(" * ?");
        }
        if ("week".equals(plan.getRunCycle())) {
            builder.append(plan.getRunDates() == null || plan.getRunDates().length == 0 ? "*" : "?")
                    .append(" * ")
                    .append(plan.getRunDates() == null || plan.getRunDates().length == 0 ? "?" : StringUtils.join(plan.getRunDates(), ","))
                    .append(plan.getRunSpace() != null && plan.getRunSpace() > 0 ? "/" + plan.getRunSpace() : "");
        }
        if ("month".equals(plan.getRunCycle())) {
            builder.append(plan.getRunBeginDate())
                    .append(" ")
                    .append(plan.getRunDates() == null || plan.getRunDates().length == 0 ? "*" : StringUtils.join(plan.getRunDates(), ","))
                    .append(" ").append(" ?");
        }
        return builder.toString();
    }

    public static Date updateTaskNextFireTime(JobDetail jobDetail) {
        Date nextFireDate = new Date();
        Date date = new Date();
        if (jobDetail != null && jobDetail.getCronexp() != null && jobDetail.getCronexp().length() > 0) {
            try {
                nextFireDate = (CronTools.getFinalFireTime(
                        jobDetail.getCronexp(),
                        jobDetail.getNextfiretime() != null ? jobDetail.getNextfiretime() : date));
            } catch (ParseException e) {
                nextFireDate = new Date(
                        System.currentTimeMillis() + 1000 * 60 * 60 * 24);    //一旦任务的 Cron表达式错误，将下次执行时间自动设置为一天后，避免出现任务永远无法终止的情况
                e.printStackTrace();
            }
        }
        return nextFireDate;
    }

}
