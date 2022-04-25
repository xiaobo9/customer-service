package com.chatopera.cc.activemq;

import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.jms.Destination;

@Component
public class BrokerPublisher {

    final static private Logger logger = LoggerFactory.getLogger(BrokerPublisher.class);

    @Autowired
    private JmsTemplate jmsTemplate;

    @PostConstruct
    public void setup() {
        logger.info("[ActiveMQ Publisher] setup successfully.");
    }

    public void send(MqMessage msg) {
        try {
            Destination destination;
            if (MqMessage.Type.TOPIC.equals(msg.type())) {
                destination = new ActiveMQTopic(msg.destination());
            } else {
                destination = new ActiveMQQueue(msg.destination());
            }
            jmsTemplate.convertAndSend(destination, msg.payload(), m -> {
                if (msg.delay() > 0) {
                    m.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, 1000L * msg.delay());
                }
                return m;
            });
        } catch (Exception e) {
            logger.warn("[send] error happens.", e);
        }
    }

}