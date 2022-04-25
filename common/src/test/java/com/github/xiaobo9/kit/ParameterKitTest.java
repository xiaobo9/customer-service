package com.github.xiaobo9.kit;

import com.github.xiaobo9.commons.kit.ParameterKit;
import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ParameterKitTest {

    @Test
    public void test() throws UnsupportedEncodingException {
        Map<String, String[]> map = new HashMap<>();
        ParameterKit.parseParameters(map, "a=a1&a=a2&b=b1&c=中文", StandardCharsets.UTF_8.displayName());

        Assert.assertEquals(3, map.size());
        Assert.assertArrayEquals(new String[]{"a1", "a2"}, map.get("a"));
        Assert.assertEquals("b1", map.get("b")[0]);
        Assert.assertEquals("中文", map.get("c")[0]);
    }
}