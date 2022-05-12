package com.github.xiaobo9.commons.kit;

import org.apache.commons.lang3.StringUtils;

public class StringKit {
    public static String subLongString(String origin, int length) {
        if (StringUtils.isBlank(origin) || origin.length() <= length) {
            return origin;
        }
        return origin.substring(0, length);
    }
}
