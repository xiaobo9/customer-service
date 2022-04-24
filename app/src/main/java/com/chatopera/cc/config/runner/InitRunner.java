package com.chatopera.cc.config.runner;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.util.SystemEnvHelper;
import com.chatopera.cc.util.mobile.MobileNumberUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InitRunner implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        log.info("enable modules");
        modules();

        log.info("mobile number init");
        MobileNumberUtils.init();
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
