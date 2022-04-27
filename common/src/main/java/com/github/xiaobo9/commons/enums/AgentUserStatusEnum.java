package com.github.xiaobo9.commons.enums;

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
            if (item.name().equalsIgnoreCase(str)) {
                return item;
            }
        }
        throw new IllegalArgumentException();
    }

    public boolean check(String toCheck) {
        return name().toLowerCase().equals(toCheck);
    }
}
