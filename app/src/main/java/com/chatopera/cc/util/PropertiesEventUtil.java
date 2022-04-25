/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chatopera.cc.util;

import com.github.xiaobo9.entity.PropertiesEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;

public class PropertiesEventUtil {
    private static final Set<String> ignoreList;

    static {
        String[] fields = new String[]{"id", "orgi", "creater", "createtime", "updatetime"};
        ignoreList = new HashSet<>(Arrays.asList(fields));
    }

    public static List<PropertiesEvent> processPropertiesModify(
            @NotNull HttpServletRequest request, @NotNull Object newobj, @NotNull Object oldobj) {

        List<PropertiesEvent> events = new ArrayList<>();

        PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(newobj.getClass());
        for (PropertyDescriptor targetPd : targetPds) {
            Method newReadMethod = targetPd.getReadMethod();
            String name = targetPd.getName();
            if (newReadMethod == null || ignoreList.contains(name)) {
                continue;
            }

            PropertyDescriptor sourcePd = BeanUtils.getPropertyDescriptor(oldobj.getClass(), name);
            if (sourcePd == null || name.equalsIgnoreCase("id")) {
                continue;
            }
            Method readMethod = sourcePd.getReadMethod();
            if (readMethod == null) {
                continue;
            }
            try {
                Object newValue = readMethod.invoke(newobj);
                Object oldValue = readMethod.invoke(oldobj);

                if (newValue != null && !newValue.equals(oldValue)) {
                    PropertiesEvent event = new PropertiesEvent();
                    event.setField(name);
                    event.setCreatetime(new Date());
                    event.setName(name);
                    event.setPropertity(name);
                    event.setOldvalue(oldValue != null && oldValue.toString().length() < 100 ? oldValue.toString() : null);
                    event.setNewvalue(newValue.toString().length() < 100 ? newValue.toString() : null);
                    if (request != null && !StringUtils.isBlank(request.getParameter(name + ".text"))) {
                        event.setTextvalue(request.getParameter(name + ".text"));
                    }
                    events.add(event);
                }
            } catch (Throwable ex) {
                throw new FatalBeanException("Could not copy property '" + name + "' from source to target", ex);
            }
        }
        return events;
    }
}
