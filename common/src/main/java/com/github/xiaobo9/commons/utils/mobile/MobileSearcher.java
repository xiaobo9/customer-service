package com.github.xiaobo9.commons.utils.mobile;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MobileSearcher implements IMobileSearcher {
    private final Map<String, MobileAddress> mobileAddressMap = new HashMap<>();

    /**
     * 根据呼入号码 找到对应 城市 , 需要传入的号码是 手机号 或者 固话号码，位数为 11位
     *
     * @param phoneNumber phone number
     * @return address
     */
    @Override
    public MobileAddress search(String phoneNumber) {
        String code = "";
        if (!StringUtils.isBlank(phoneNumber) && phoneNumber.length() > 10) {
            if (phoneNumber.startsWith("0")) {
                code = phoneNumber.substring(0, 4);
            } else if (phoneNumber.startsWith("1")) {
                code = phoneNumber.substring(0, 7);
            }
        }
        return mobileAddressMap.get(code);
    }

    @Override
    public int init(String dataFile) throws IOException {
        return innerInit(dataFile);
    }

    private int innerInit(String dataFile) {
        log.info("begin init mobile number info");
        URL resource = MobileNumberUtils.class.getResource(dataFile);
        if (resource == null) {
            log.info("{} 读取失败", dataFile);
            return mobileAddressMap.size();
        }
        File file = new File(resource.getFile());
        log.info("init with file [{}]", file.getAbsolutePath());
        if (!file.exists()) {
            return mobileAddressMap.size();
        }
        try (FileInputStream reader = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(reader, StandardCharsets.UTF_8);
             BufferedReader bf = new BufferedReader(isr)) {
            String data;
            while ((data = bf.readLine()) != null) {
                String[] group = data.split("[\t ]");
                MobileAddress address = null;
                if (group.length == 5) {
                    address = new MobileAddress(group[0], group[1], group[2], group[3], group[4]);
                } else if (group.length == 4) {
                    address = new MobileAddress(group[0], group[1], group[2], group[2], group[3]);
                }
                if (address != null) {
                    mobileAddressMap.putIfAbsent(address.getCode(), address);
                    mobileAddressMap.putIfAbsent(address.getAreacode(), address);
                }
            }
            log.info("inited successfully, map size [{}]", mobileAddressMap.size());
        } catch (Exception ex) {
            log.error("", ex);
        }
        return mobileAddressMap.size();
    }

}
