package com.chatopera.cc.config.runner;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.util.DateConverter;
import com.chatopera.cc.util.IPTools;
import com.chatopera.cc.util.SystemEnvHelper;
import com.chatopera.cc.util.mobile.MobileNumberUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class AppInitRunner implements CommandLineRunner {
    @Value("${enable.mobile.search:false}")
    private boolean enableMobile;

    @Override
    public void run(String... args) throws Exception {
        log.info("enable modules");
        modules();

        log.info("mobile number init");
        if (enableMobile) {
            MobileNumberUtils.init();
        } else {
            MobileNumberUtils.initNone();
        }

        log.info("init ip data");
        IPTools.init();

        ConvertUtils.register(new DateConverter(), Date.class);
    }

    /**
     * 记载模块
     */
    private void modules() {
        // CRM模块
        if (StringUtils.equalsIgnoreCase(SystemEnvHelper.parseFromApplicationProps("cskefu.modules.contacts"), "true")) {
            MainContext.enableModule(Constants.CSKEFU_MODULE_CONTACTS);
        }

        // 会话监控模块 Customer Chats Audit
        if (StringUtils.equalsIgnoreCase(SystemEnvHelper.parseFromApplicationProps("cskefu.modules.cca"), "true")) {
            MainContext.enableModule(Constants.CSKEFU_MODULE_CCA);
        }

        // 企业聊天模块
        if (StringUtils.equalsIgnoreCase(SystemEnvHelper.parseFromApplicationProps("cskefu.modules.entim"), "true")) {
            MainContext.enableModule(Constants.CSKEFU_MODULE_ENTIM);
        }

        // 数据报表
        if (StringUtils.equalsIgnoreCase(SystemEnvHelper.parseFromApplicationProps("cskefu.modules.report"), "true")) {
            MainContext.enableModule(Constants.CSKEFU_MODULE_REPORT);
        }
    }

}
