package com.chatopera.cc.peer.im;

import com.chatopera.cc.model.ChatMessage;
import com.chatopera.cc.peer.PeerContext;
import com.chatopera.cc.peer.PeerUtils;
import com.chatopera.cc.service.AgentAuditProxy;
import com.chatopera.cc.socketio.message.Message;
import com.chatopera.compose4j.Functional;
import com.chatopera.compose4j.Middleware;
import com.github.xiaobo9.commons.enums.Enums;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * 发送后的工作
 */
@Component
public class ComposeMw3 implements Middleware<PeerContext> {

    private final static Logger logger = LoggerFactory.getLogger(
            ComposeMw3.class);

    @Autowired
    private AgentAuditProxy agentAuditProxy;

    @Override
    public void apply(final PeerContext ctx, final Functional next) {
        logger.info(
                "[apply] receiverType {}, touser {}, msgType {}",
                ctx.getReceiverType(), ctx.getTouser(), ctx.getMsgType()
        );

        // 处理会话监控
        if (ctx.isSent()) {
            switch (ctx.getReceiverType()) {
                // 发送给坐席的消息成功后，同时也发送给会话监控，同时确保也路由到了会话监控
                case AGENT:
                    sendAgentAuditMessage(ctx);
                    break;
                default:
                    logger.info(
                            "[apply] other ReceiverType {}",
                            ctx.getReceiverType()
                    );
            }
        }

        next.apply();
    }

    /**
     * 发送消息给会话监控
     *
     * @param ctx
     * @return
     */
    private void sendAgentAuditMessage(final PeerContext ctx) {
        boolean send = true;
        Message message = ctx.getMessage();
        if (message.getChannelMessage() instanceof ChatMessage) {
            final ChatMessage msg = (ChatMessage) message.getChannelMessage();
            if (PeerUtils.isMessageInWritting(msg)) {
                send = false;
            }
        }

        if (!send || ctx.getMsgType() == Enums.MessageType.TRANSOUT) {
            // 忽略坐席转出事件
            return;
        }
        agentAuditProxy.publishMessage(message.getAgentUser(), message.getChannelMessage(),
                Enums.MessageType.toValue(("audit_" + ctx.getMsgType().toString()))
        );
    }
}
