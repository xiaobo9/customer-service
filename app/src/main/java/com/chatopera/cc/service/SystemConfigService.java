package com.chatopera.cc.service;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.cache.Cache;
import com.chatopera.cc.model.SystemConfig;
import com.chatopera.cc.persistence.repository.SystemConfigRepository;
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
    private Cache cache;

    /**
     * 获取系统配置
     *
     * @return system config bean
     */
    @NotNull
    public SystemConfig getSystemConfig() {
        SystemConfig systemConfig = cache.findOneSystemByIdAndOrgi("systemConfig", Constants.SYSTEM_ORGI);
        if (systemConfig == null) {
            systemConfig = repository.findByOrgi(Constants.SYSTEM_ORGI);
        }
        // FIXME 默认配置
        return systemConfig != null ? systemConfig : new SystemConfig();
    }
}
