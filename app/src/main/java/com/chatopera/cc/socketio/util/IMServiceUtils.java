package com.chatopera.cc.socketio.util;

import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.service.AgentUserService;
import com.github.xiaobo9.commons.enums.Enums;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IMServiceUtils {
    private final static Logger logger = LoggerFactory.getLogger(IMServiceUtils.class);

    private static CacheService cacheService;
    private static AgentUserService agentUserService;

    public static void shiftOpsType(final String userId, final String orgi, final Enums.OptType opsType) {
        getCache().findOneAgentUserByUserIdAndOrgi(userId, orgi).ifPresent(p -> {
            switch (opsType) {
                case CHATBOT:
                    p.setOpttype(Enums.OptType.CHATBOT.toString());
                    p.setChatbotops(true);
                    break;
                case HUMAN:
                    p.setOpttype(Enums.OptType.HUMAN.toString());
                    p.setChatbotops(false);
                    break;
                default:
                    logger.warn("shiftOpsType unknown type.");
                    break;
            }
            getAgentUserProxy().save(p);
        });
    }

    /**
     * Lazy load cache mgr
     *
     * @return
     */
    static private CacheService getCache() {
        if (cacheService == null) {
            cacheService = MainContext.getContext().getBean(CacheService.class);
        }
        return cacheService;
    }

    private static AgentUserService getAgentUserProxy() {
        if (agentUserService == null) {
            agentUserService = MainContext.getContext().getBean(
                    AgentUserService.class);
        }
        return agentUserService;
    }


}
