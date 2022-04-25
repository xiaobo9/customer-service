package com.github.xiaobo9.commons.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 坐席访客对话状态
 */
public enum AgentUserStatusEnum {
    INSERVICE,
    INQUENE,
    END;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    public static AgentUserStatusEnum toValue(final String str) {
        for (final AgentUserStatusEnum item : values()) {
            if (StringUtils.equalsIgnoreCase(item.toString(), str)) {
                return item;
            }
        }
        throw new IllegalArgumentException();
    }
}
