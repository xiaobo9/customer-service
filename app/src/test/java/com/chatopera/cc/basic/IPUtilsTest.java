package com.chatopera.cc.basic;

import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

public class IPUtilsTest {

    @Test
    public void getIpAddress() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Assert.assertNull(IPUtils.getIpAddress(request));

        Mockito.when(request.getHeader(Mockito.eq("x-forwarded-for"))).thenReturn("192.168.1.1");
        Assert.assertEquals("192.168.1.1", IPUtils.getIpAddress(request));

        Mockito.when(request.getHeader(Mockito.eq("x-forwarded-for"))).thenReturn(null);
        Mockito.when(request.getHeader(Mockito.eq("Proxy-Client-IP"))).thenReturn("192.168.1.1");
        Assert.assertEquals("192.168.1.1", IPUtils.getIpAddress(request));

        Mockito.when(request.getHeader(Mockito.eq("Proxy-Client-IP"))).thenReturn(null);
        Mockito.when(request.getHeader(Mockito.eq("WL-Proxy-Client-IP"))).thenReturn("192.168.1.1");
        Assert.assertEquals("192.168.1.1", IPUtils.getIpAddress(request));
    }

    @Test
    public void testGetIpAddress() {
        HttpHeaders headers = Mockito.mock(HttpHeaders.class);
        Assert.assertEquals("abc", IPUtils.getIpAddress(headers, "abc"));

        Mockito.when(headers.get(Mockito.eq("x-forwarded-for"))).thenReturn("192.168.1.1");
        Assert.assertEquals("192.168.1.1", IPUtils.getIpAddress(headers, "abc"));

        Mockito.when(headers.get(Mockito.eq("x-forwarded-for"))).thenReturn(null);
        Mockito.when(headers.get(Mockito.eq("Proxy-Client-IP"))).thenReturn("192.168.1.1");
        Assert.assertEquals("192.168.1.1", IPUtils.getIpAddress(headers, "abc"));

        Mockito.when(headers.get(Mockito.eq("Proxy-Client-IP"))).thenReturn(null);
        Mockito.when(headers.get(Mockito.eq("WL-Proxy-Client-IP"))).thenReturn("192.168.1.1");
        Assert.assertEquals("192.168.1.1", IPUtils.getIpAddress(headers, "abc"));

        Mockito.when(headers.get(Mockito.eq("x-forwarded-for"))).thenReturn("unknown");
        Mockito.when(headers.get(Mockito.eq("Proxy-Client-IP"))).thenReturn("");
        Mockito.when(headers.get(Mockito.eq("WL-Proxy-Client-IP"))).thenReturn(null);
        Assert.assertEquals("abc", IPUtils.getIpAddress(headers, "abc"));
    }
}