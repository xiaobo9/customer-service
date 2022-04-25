
package com.github.xiaobo9.commons.utils.mobile;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class MobileNumberUtils {

    private static boolean isInit = false;

    private static String dataFile = "/config/mobile.data";

    @Setter
    @Getter
    private static IMobileSearcher searcher;

    /**
     * 根据呼入号码 找到对应 城市 , 需要传入的号码是 手机号 或者 固话号码，位数为 11位
     *
     * @param phoneNumber phone number
     * @return address
     */
    public static MobileAddress getAddress(String phoneNumber) {
        initIfNeed();
        return searcher.search(phoneNumber);
    }

    public static synchronized int init() throws IOException {
        int i = innerInit();
        isInit = true;
        return i;
    }

    private static void initIfNeed() {
        if (!isInit) {
            try {
                init();
            } catch (IOException e) {
                log.error("getAddress error: ", e);
            }
        }
    }

    private static int innerInit() throws IOException {
        MobileSearcher a = new MobileSearcher();
        searcher = a;
        return a.init(dataFile);
    }

    public static void initNone() {
        searcher = new IMobileSearcher() {
            @Override
            public int init(String dataFile) {
                return 0;
            }

            @Override
            public MobileAddress search(String phoneNumber) {
                return new MobileAddress("", "", "", "", "");
            }
        };
    }

    public static void setDataFile(String dataFile) {
        MobileNumberUtils.dataFile = dataFile;
    }
}
