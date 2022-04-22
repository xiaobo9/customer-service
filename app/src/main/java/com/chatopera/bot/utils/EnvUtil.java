package com.chatopera.bot.utils;

import org.apache.commons.lang3.StringUtils;

public class EnvUtil {
    public EnvUtil() {
    }

    public static String getEnv(String variable, String defaultVal) {
        String val = System.getenv(variable);
        return StringUtils.isBlank(val) ? defaultVal : val;
    }
}



