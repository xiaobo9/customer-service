package com.github.xiaobo9.commons.utils;

public class Base62Utils {
    public static String genID() {
        return Base62.encode(UUIDUtils.getUUID());
    }

    public static String genIDByKey(String key) {
        return Base62.encode(key);
    }

}
