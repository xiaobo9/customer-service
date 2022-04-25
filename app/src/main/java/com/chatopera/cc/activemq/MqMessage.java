package com.chatopera.cc.activemq;


import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true, fluent = true)
public class MqMessage {
    private String destination;
    private Type type;
    private String payload;
    private int delay;

    public MqMessage() {
        this.type = Type.QUEUE;
    }

    public enum Type {
        TOPIC,
        QUEUE;
    }

}
