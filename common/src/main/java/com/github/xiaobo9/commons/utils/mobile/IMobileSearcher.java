package com.github.xiaobo9.commons.utils.mobile;

import java.io.IOException;

public interface IMobileSearcher {
    /**
     * 初始化
     *
     * @param dataFile 数据文件
     * @return 数据条数
     * @throws IOException 异常
     */
    int init(String dataFile) throws IOException;

    /**
     * 查询
     *
     * @param phoneNumber phoneNumber
     * @return 归属地信息
     */
    MobileAddress search(String phoneNumber);
}
