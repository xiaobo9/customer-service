package com.chatopera.bot.sdk;

import com.chatopera.bot.exception.ChatbotException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.Map;

public class Credentials {
    private String clientId;
    private String clientSecret;

    private Credentials() {
    }

    public Credentials(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private String byte2hex(byte[] b) {
        StringBuilder hs = new StringBuilder();

        for (int n = 0; b != null && n < b.length; ++n) {
            String stmp = Integer.toHexString(b[n] & 255);
            if (stmp.length() == 1) {
                hs.append('0');
            }

            hs.append(stmp);
        }

        return hs.toString().toLowerCase();
    }

    private String HmacSHA1Encrypt(String encryptText, String encryptKey) throws Exception {
        String MAC_NAME = "HmacSHA1";
        String ENCODING = "UTF-8";
        byte[] data = encryptKey.getBytes(ENCODING);
        SecretKey secretKey = new SecretKeySpec(data, MAC_NAME);
        Mac mac = Mac.getInstance(MAC_NAME);
        mac.init(secretKey);
        byte[] text = encryptText.getBytes(ENCODING);
        return this.byte2hex(mac.doFinal(text));
    }

    public String generate(String method, String path) throws ChatbotException {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String random = RandomStringUtils.random(10, false, true);
        String signature = null;

        try {
            signature = this.HmacSHA1Encrypt(this.clientId + timestamp + random + method + path, this.clientSecret);
        } catch (Exception var8) {
            var8.printStackTrace();
            throw new ChatbotException("生成认证签名异常。");
        }

        Map<String, String> map = new HashMap();
        map.put("appId", this.clientId);
        map.put("timestamp", timestamp);
        map.put("random", random);
        map.put("signature", signature);
        JSONObject json = new JSONObject(map);
        return new String(Base64.encodeBase64(json.toString().getBytes()));
    }
}
