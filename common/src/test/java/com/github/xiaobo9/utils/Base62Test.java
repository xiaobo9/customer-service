package com.github.xiaobo9.utils;

import com.github.xiaobo9.commons.utils.Base62;
import org.junit.Assert;
import org.junit.Test;

public class Base62Test {

    @Test
    public void encodeLString() {
        Assert.assertEquals("04Eh0h", Base62.encode("I will be encoded"));
        Assert.assertEquals("1IY4Yk", Base62.encode("我要被编码了"));
    }
}