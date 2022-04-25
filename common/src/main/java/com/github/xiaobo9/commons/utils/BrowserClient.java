package com.github.xiaobo9.commons.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowserClient {
    public static final String USER_AGENT = "User-Agent";

    // \b 是单词边界(连着的两个(字母字符 与 非字母字符) 之间的逻辑上的间隔),
    // 字符串在编译时会被转码一次,所以是 "\\b"
    // \B 是单词内部逻辑间隔(连着的两个字母字符之间的逻辑上的间隔)
    private static final String phoneReg = "\\b(ip(hone|od)|android|opera m(ob|in)i"
            + "|windows (phone|ce)|blackberry"
            + "|s(ymbian|eries60|amsung)|p(laybook|alm|rofile/midp"
            + "|laystation portable)|nokia|fennec|htc[-_]"
            + "|mobile|up.browser|[1-4][0-9]{2}x[1-4][0-9]{2})\\b";
    private static final String tableReg = "\\b(ipad|tablet|(Nexus 7)|up.browser"
            + "|[1-4][0-9]{2}x[1-4][0-9]{2})\\b";

    //移动设备正则匹配：手机端、平板
    private static final Pattern phonePat = Pattern.compile(phoneReg, Pattern.CASE_INSENSITIVE);
    private static final Pattern tablePat = Pattern.compile(tableReg, Pattern.CASE_INSENSITIVE);

    private String useragent;
    private String os;
    private String browser;
    private String version;

    public BrowserClient(String useragent) {
        this.useragent = useragent;
    }

    /**
     * parse user agent
     *
     * @param userAgent req
     * @return client info
     */
    public static BrowserClient parseClient(String userAgent) {
        return new BrowserClient(userAgent).parse();
    }

    /**
     * parse user agent
     *
     * @return browser client info
     */
    public BrowserClient parse() {

        String lowerCase = this.useragent.toLowerCase();

        //=================OS=======================
        parseOperatingSystem(lowerCase);

        //===============Browser===========================
        if (lowerCase.contains("qqbrowser")) {
            browser = "QQBrowser";
        } else if (lowerCase.contains("msie") || lowerCase.contains("rv:11")) {
            if (lowerCase.contains("rv:11")) {
                browser = "IE11";
            } else {
                String substring = this.useragent.substring(this.useragent.indexOf("MSIE")).split(";")[0];
                browser = substring.split(" ")[0].replace("MSIE", "IE") + substring.split(" ")[1];
            }
        } else if (lowerCase.contains("trident")) {
            browser = "IE 11";
        } else if (lowerCase.contains("edge")) {
            browser = "Edge";
        } else if (lowerCase.contains("safari") && lowerCase.contains("version")) {
            browser = (this.useragent.substring(this.useragent.indexOf("Safari")).split(" ")[0]).split("/")[0];
            version = (this.useragent.substring(this.useragent.indexOf("Version")).split(" ")[0]).split("/")[1];
        } else if (lowerCase.contains("opr") || lowerCase.contains("opera")) {
            if (lowerCase.contains("opera")) {
                browser = (this.useragent.substring(this.useragent.indexOf("Opera")).split(" ")[0]).split(
                        "/")[0] + "-" + (this.useragent.substring(this.useragent.indexOf("Version")).split(" ")[0]).split("/")[1];
            } else if (lowerCase.contains("opr")) {
                browser = ((this.useragent.substring(this.useragent.indexOf("OPR")).split(" ")[0]).replace("/", "-")).replace(
                        "OPR", "Opera");
            }
        } else if (lowerCase.contains("chrome")) {
            browser = "Chrome";
        } else if ((lowerCase.contains("mozilla/7.0")) || (lowerCase.contains("netscape6")) || (lowerCase.contains("mozilla/4.7")) || (lowerCase.contains("mozilla/4.78")) || (lowerCase.contains("mozilla/4.08")) || (lowerCase.contains("mozilla/3"))) {
            browser = "Netscape-?";

        } else if ((lowerCase.contains("mozilla"))) {
            if (this.useragent.indexOf(" ") > 0) {
                browser = this.useragent.substring(0, this.useragent.indexOf(" "));
            } else {
                browser = "Mozilla";
            }

        } else if (lowerCase.contains("firefox")) {
            browser = (this.useragent.substring(this.useragent.indexOf("Firefox")).split(" ")[0]).replace("/", "-");
        } else if (lowerCase.contains("rv")) {
            browser = "ie";
        } else {
            browser = "UnKnown";
        }

        return this;
    }


    /**
     * 检测是否是移动设备访问
     *
     * @return true:移动设备接入，false:pc端接入
     */
    public boolean isMobile() {
        Matcher matcherPhone = phonePat.matcher(useragent);
        Matcher matcherTable = tablePat.matcher(useragent);
        return matcherPhone.find() || matcherTable.find();
    }

    /**
     * 检测是否是移动设备访问
     *
     * @param userAgent 浏览器标识
     * @return true:移动设备接入，false:pc端接入
     */
    public static boolean isMobile(String userAgent) {
        if (null == userAgent) {
            return false;
        }
        // 匹配
        return phonePat.matcher(userAgent).find() || tablePat.matcher(userAgent).find();
    }

    private void parseOperatingSystem(String lowerCase) {
        if (lowerCase.contains("windows")) {
            os = "windows";
        } else if (lowerCase.contains("mac")) {
            os = "mac";
        } else if (lowerCase.contains("x11")) {
            os = "unix";
        } else if (lowerCase.contains("android")) {
            os = "android";
        } else if (lowerCase.contains("iphone")) {
            os = "iphone";
        } else {
            os = "UnKnown";
        }
    }

    public String getUseragent() {
        return useragent;
    }

    public void setUseragent(String useragent) {
        this.useragent = useragent;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

}
