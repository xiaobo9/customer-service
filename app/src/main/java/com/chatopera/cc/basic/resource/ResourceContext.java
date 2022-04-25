package com.chatopera.cc.basic.resource;

import com.github.xiaobo9.commons.enums.Enums;

import java.util.HashMap;
import java.util.Map;

public class ResourceContext {
    public static Map<String, Class<?>> csKeFuResourceMap = new HashMap<String, Class<?>>();

    static {
        csKeFuResourceMap.put(Enums.TaskType.ACTIVE.toString(), ActivityResource.class);
        csKeFuResourceMap.put(Enums.TaskType.BATCH.toString(), BatchResource.class);
    }

    /**
     * @param resource
     * @return
     */
    public static Class<?> getResource(String resource) {
        return csKeFuResourceMap.get(resource);
    }

}
