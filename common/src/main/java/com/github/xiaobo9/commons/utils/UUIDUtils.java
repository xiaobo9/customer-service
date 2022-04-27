package com.github.xiaobo9.commons.utils;

import java.util.UUID;

public class UUIDUtils {
    public static String getUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String removeHyphen(String id) {
        return id.replaceAll("-", "");
    }

}
