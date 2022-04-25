package com.chatopera.cc.util.mobile;

import java.io.IOException;

public interface IMobileSearcher {
    /**
     * 初始化
     *
     * @return 数据条数
     * @throws IOException 异常
     */
    int init() throws IOException;

    /**
     * 查询
     *
     * @param phoneNumber phoneNumber
     * @return 归属地信息
     */
    MobileAddress search(String phoneNumber);
}
