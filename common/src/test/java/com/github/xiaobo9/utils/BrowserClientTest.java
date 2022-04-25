package com.github.xiaobo9.utils;

import com.github.xiaobo9.commons.utils.BrowserClient;
import org.junit.Assert;
import org.junit.Test;

public class BrowserClientTest {

    @Test
    public void parseClient() {
        BrowserClient client = BrowserClient.parseClient("Mozilla/5.0 (Linux; {Android Version}; {Build Tag etc.}) ");
        Assert.assertEquals("android", client.getOs());
        Assert.assertEquals("Mozilla/5.0", client.getBrowser());
        Assert.assertNull(client.getVersion());
    }
}