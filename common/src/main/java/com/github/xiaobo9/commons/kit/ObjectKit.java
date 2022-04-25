package com.github.xiaobo9.commons.kit;

import lombok.extern.slf4j.Slf4j;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ObjectKit {
    public static byte[] toBytes(Object object) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream objectOutput = new ObjectOutputStream(out);
        objectOutput.writeObject(object);
        return out.toByteArray();
    }

    public static Object toObject(byte[] data) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        ObjectInputStream objectInput = new ObjectInputStream(input);
        return objectInput.readObject();
    }

    public static Map<String, Object> transBean2Map(Object obj) {
        if (obj == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor property : propertyDescriptors) {
                String key = property.getName();

                // 过滤class属性
                if (key.equals("class")) {
                    continue;
                }

                // 得到property对应的getter方法
                Method readMethod = property.getReadMethod();

                if (readMethod != null) {
                    Object value = readMethod.invoke(obj);
                    if (value instanceof Date) {
                        value = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value);
                    }
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            log.warn("", e);
        }
        return map;
    }
}
