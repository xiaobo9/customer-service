/*
 * Copyright (C) 2019 Chatopera Inc, All rights reserved.
 * <https://www.chatopera.com>
 * This software and related documentation are provided under a license agreement containing
 * restrictions on use and disclosure and are protected by intellectual property laws.
 * Except as expressly permitted in your license agreement or allowed by law, you may not use,
 * copy, reproduce, translate, broadcast, modify, license, transmit, distribute, exhibit, perform,
 * publish, or display any part, in any form, or by any means. Reverse engineering, disassembly,
 * or decompilation of this software, unless required by law for interoperability, is prohibited.
 */
package com.chatopera.cc.activemq;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.socketio.client.NettyClients;
import com.chatopera.cc.util.SerializeUtil;
import com.github.xiaobo9.commons.jackson.JacksonUtils;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * WebIM Agent
 */
@Component
public class AgentSubscription {

    private final static Logger logger = LoggerFactory.getLogger(AgentSubscription.class);

    @Value("${application.node.id}")
    private String appNodeId;

    @Autowired
    private BrokerPublisher brokerPublisher;

    /**
     * Publish Message into ActiveMQ
     *
     * @param j
     */
    public void publish(JsonObject j) {
        j.addProperty("node", appNodeId);
        brokerPublisher.send(new MqMessage().destination(Constants.INSTANT_MESSAGING_MQ_TOPIC_AGENT)
                .payload(j.toString()).type(MqMessage.Type.TOPIC));
    }

    @JmsListener(destination = Constants.INSTANT_MESSAGING_MQ_TOPIC_AGENT, containerFactory = "jmsListenerContainerTopic")
    public void onMessage(final String payload) {
        logger.info("[onMessage] payload {}", payload);
        try {
            JsonData jsonData = JacksonUtils.readValue(payload, JsonData.class);
            logger.debug("[onMessage] message body {}", jsonData);

            if (jsonData.getId() == null) {
                logger.warn("[onMessage] Invalid payload, id is null");
                return;
            }

            Serializable data = SerializeUtil.deserialize(jsonData.getData());
            NettyClients.getInstance().sendAgentEventMessage(jsonData.getId(), jsonData.getEvent(), data);
        } catch (Exception e) {
            logger.error("onMessage", e);
        }
    }

    @Getter
    @Setter
    @ToString
    static class JsonData {
        private String id;
        private String event;
        private String data;
    }
}
