package com.github.xiaobo9.commons.enums;

import org.apache.commons.lang3.time.FastDateFormat;

import java.text.ParseException;
import java.util.Date;

public enum DateFormatEnum {
    DAY("yyyy-MM-dd"),
    DAY_TIME("yyyy-MM-dd HH:mm:ss"),
    ;
    private final String pattern;
    private final FastDateFormat format;

    DateFormatEnum(String pattern) {
        this.pattern = pattern;
        format = FastDateFormat.getInstance(pattern, null, null);
    }

    public FastDateFormat getFormat() {
        return format;
    }

    public String format(Date date) {
        return format.format(date);
    }

    public Date parse(String source) throws ParseException {
        return format.parse(source);
    }
}
