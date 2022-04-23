package com.chatopera.cc.basic;

import io.netty.handler.codec.http.HttpHeaders;

import javax.servlet.http.HttpServletRequest;

public class IPUtils {

    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (notValid(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (notValid(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (notValid(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    public static String getIpAddress(HttpHeaders headers, String remoteAddress) {
        String ip = headers.get("x-forwarded-for");
        if (notValid(ip)) {
            ip = headers.get("Proxy-Client-IP");
        }
        if (notValid(ip)) {
            ip = headers.get("WL-Proxy-Client-IP");
        }
        if (notValid(ip)) {
            ip = remoteAddress;
        }
        return ip;
    }

    public static boolean notValid(String ip) {
        return ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip);
    }

}
