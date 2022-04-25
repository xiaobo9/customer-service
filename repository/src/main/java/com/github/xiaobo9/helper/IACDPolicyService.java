package com.github.xiaobo9.helper;

import com.github.xiaobo9.entity.SessionConfig;

public interface IACDPolicyService {
    /**
     * 载入坐席 ACD策略配置
     *
     * @param orgi
     * @return
     */
    SessionConfig initSessionConfig(String organid, final String orgi);
}
