package com.github.xiaobo9.commons.enums;

import org.apache.commons.lang3.StringUtils;

public enum AgentStatusEnum {
    READY("就绪", 1),
    NOTREADY("未就绪", 2),
    BUSY("置忙", 3),
    NOTBUSY("不忙", 4),
    IDLE("空闲", 5),
    OFFLINE("离线", 6),
    SERVICES("服务", 7);

    private String name;
    private int index;

    AgentStatusEnum(final String name, final int index) {
        this.name = name;
        this.index = index;
    }

    public String zh() {
        return this.name;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    public static AgentStatusEnum toValue(final String str) {
        for (final AgentStatusEnum item : values()) {
            if (StringUtils.equalsIgnoreCase(item.toString(), str)) {
                return item;
            }
        }
        throw new IllegalArgumentException();
    }

}