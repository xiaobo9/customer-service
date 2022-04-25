package com.github.xiaobo9.commons.utils;

public class MD5Utils {
    private static final MD5 md5 = new MD5();

    public static String md5(String str) {
        return md5.getMD5ofStr(md5.getMD5ofStr(str));
    }

    public static String md5(byte[] bytes) {
        return md5.getMD5ofByte(bytes);
    }

}
