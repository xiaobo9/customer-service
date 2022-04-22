package com.chatopera.cc.util;

import org.junit.Assert;
import org.junit.Test;

public class StreamingFileUtilTest {

    @Test
    public void test_validate() {
        Assert.assertNull(StreamingFileUtil.validate("image", "a.gif"));
        Assert.assertNotNull(StreamingFileUtil.validate("image", "a.text"));
    }

}