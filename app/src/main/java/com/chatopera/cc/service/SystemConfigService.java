package com.chatopera.cc.service;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.cache.CacheService;
import com.github.xiaobo9.entity.SystemConfig;
import com.github.xiaobo9.repository.SystemConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;

@Slf4j
@Service
public class SystemConfigService {
    @Autowired
    private SystemConfigRepository repository;
    @Autowired
    private CacheService cacheService;

    /**
     * 获取系统配置
     *
     * @return system config bean
     */
    @NotNull
    public SystemConfig getSystemConfig() {
        SystemConfig systemConfig = cacheService.findOneSystemByIdAndOrgi("systemConfig", Constants.SYSTEM_ORGI);
        if (systemConfig == null) {
            systemConfig = repository.findByOrgi(Constants.SYSTEM_ORGI);
        }
        // FIXME 默认配置
        return systemConfig != null ? systemConfig : new SystemConfig();
    }
}
