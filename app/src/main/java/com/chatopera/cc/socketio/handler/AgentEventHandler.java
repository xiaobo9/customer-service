package com.chatopera.cc.socketio.handler;

import com.alibaba.fastjson.JSONObject;
import com.chatopera.cc.activemq.BrokerPublisher;
import com.chatopera.cc.activemq.MqMessage;
import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.IPUtils;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.cache.CacheService;
import com.chatopera.cc.model.ChatMessage;
import com.chatopera.cc.service.AgentSessionProxy;
import com.chatopera.cc.service.AgentProxyService;
import com.chatopera.cc.service.AgentUserService;
import com.chatopera.cc.service.UserService;
import com.chatopera.cc.socketio.client.NettyClients;
import com.chatopera.cc.socketio.message.AgentStatusMessage;
import com.chatopera.cc.socketio.message.InterventMessage;
import com.chatopera.cc.socketio.message.Message;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.xiaobo9.commons.enums.AgentUserStatusEnum;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.github.xiaobo9.entity.AgentStatus;
import com.github.xiaobo9.entity.AgentUser;
import com.github.xiaobo9.entity.User;
import com.github.xiaobo9.entity.WorkSession;
import com.github.xiaobo9.repository.AgentStatusRepository;
import com.github.xiaobo9.repository.WorkSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Date;

@Slf4j
@Component
public class AgentEventHandler {
    protected SocketIOServer server;
    private final BrokerPublisher brokerPublisher;
    private final AgentStatusRepository agentStatusRes;
    private final AgentUserService agentUserService;
    private final AgentProxyService agentServiceService;
    private final AgentSessionProxy agentSessionProxy;
    private final UserService userService;

    private final WorkSessionRepository workSessionRepository;

    private final CacheService cacheService;

    public AgentEventHandler(
            SocketIOServer server,
            BrokerPublisher brokerPublisher,
            AgentStatusRepository agentStatusRes,
            AgentUserService agentUserService,
            AgentProxyService agentServiceService,
            AgentSessionProxy agentSessionProxy,
            UserService userService, WorkSessionRepository workSessionRepository, CacheService cacheService) {
        this.server = server;
        this.brokerPublisher = brokerPublisher;
        this.agentStatusRes = agentStatusRes;
        this.agentUserService = agentUserService;
        this.agentServiceService = agentServiceService;
        this.agentSessionProxy = agentSessionProxy;
        this.userService = userService;
        this.workSessionRepository = workSessionRepository;
        this.cacheService = cacheService;
    }

    @OnConnect
    public void onConnect(SocketIOClient client) {
        HandshakeData handshakeData = client.getHandshakeData();
        final String userid = handshakeData.getSingleUrlParam("userid");
        final String orgi = handshakeData.getSingleUrlParam("orgi");
        final String session = handshakeData.getSingleUrlParam("session");
        final String admin = handshakeData.getSingleUrlParam("admin");
        final String connectid = UUIDUtils.getUUID();
        log.info("[onConnect] user: {}, orgi: {}, session: {}, admin: {}, connectid {}", userid, orgi, session, admin, connectid);

        if (StringUtils.isBlank(userid) || StringUtils.isBlank(session)) {
            return;
        }

        // 验证当前的SSO中的session是否和传入的session匹配
        if (agentSessionProxy.isInvalidSessionId(userid, session, orgi)) {
            // 该session信息不合法
            log.info("[onConnect] invalid sessionId {}", session);
            return;
        }

        client.set("agentno", userid);
        client.set("session", session);
        client.set("connectid", connectid);

        // 更新AgentStatus到数据库
        agentStatusRes.findOneByAgentnoAndOrgi(userid, orgi).ifPresent(p -> {
            p.setUpdatetime(new Date());
            p.setConnected(true);
            // 设置agentSkills
            p.setSkills(userService.getSkillsMapByAgentno(userid));
            agentStatusRes.save(p);
        });

        // 工作工作效率
        InetSocketAddress address = (InetSocketAddress) client.getRemoteAddress();

        int count = workSessionRepository.countByAgentAndDatestrAndOrgi(
                userid, DateFormatEnum.DAY.format(new Date()), orgi);

        String id = UUIDUtils.removeHyphen(client.getSessionId().toString());
        WorkSession workSession = createWorkSession(handshakeData, userid, orgi, session, admin, address, count, id);
        workSessionRepository.save(workSession);

        NettyClients.getInstance().putAgentEventClient(userid, client);
    }

    // 添加@OnDisconnect事件，客户端断开连接时调用，刷新客户端信息
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        HandshakeData handshakeData = client.getHandshakeData();
        String userid = handshakeData.getSingleUrlParam("userid");
        String orgi = handshakeData.getSingleUrlParam("orgi");
        String admin = handshakeData.getSingleUrlParam("admin");
        String session = handshakeData.getSingleUrlParam("session");
        String connectid = client.get("connectid");
        log.info("[onDisconnect] userId {}, orgi {}, admin {}, session {}, connectid {}",
                userid, orgi, admin, session, connectid);

        /**
         * 连接断开
         */
        if (NettyClients.getInstance().removeAgentEventClient(
                userid, UUIDUtils.removeHyphen(client.getSessionId().toString()), connectid) == 0) {
            // 该坐席和服务器没有连接了，但是也不能保证该坐席是停止办公了，可能稍后TA又打开网页
            // 所以，此处做一个30秒的延迟，如果该坐席30秒内没重新建立连接，则撤退该坐席
            // 更新该坐席状态，设置为"无连接"，不会分配新访客
            final AgentStatus agentStatus = MainContext.getCache().findOneAgentStatusByAgentnoAndOrig(userid, orgi);

            if (agentStatus != null) {
                agentStatus.setConnected(false);
                MainContext.getCache().putAgentStatusByOrgi(agentStatus, agentStatus.getOrgi());
            }

            /**
             * 业务断开
             * 在超时发生了一段时间后触发
             */
            JSONObject payload = new JSONObject();
            payload.put("userId", userid);
            payload.put("orgi", orgi);
            payload.put("isAdmin", StringUtils.isNotBlank(admin) && admin.equalsIgnoreCase("true"));
            brokerPublisher.send(new MqMessage().destination(Constants.WEBIM_SOCKETIO_AGENT_DISCONNECT)
                    .payload(payload.toJSONString()).type(MqMessage.Type.QUEUE).delay(Constants.WEBIM_SOCKETIO_AGENT_OFFLINE_THRESHOLD));
        }
    }

    // 消息接收入口，当接收到消息后，查找发送目标客户端，并且向该客户端发送消息，且给自己发送消息
    @OnEvent(value = "service")
    public void onServiceEvent(SocketIOClient client, AckRequest request, Message data) {

    }

    // 消息接收入口，当接收到消息后，查找发送目标客户端，并且向该客户端发送消息，且给自己发送消息
    @OnEvent(value = "status")
    public void onStatusEvent(SocketIOClient client, AckRequest request, AgentStatusMessage data) {

    }

    /**
     * 会话监控干预消息
     */
    @OnEvent(value = "intervention")
    public void onIntervetionEvent(final SocketIOClient client, final InterventMessage received) throws JsonProcessingException {
        final String agentno = client.get("agentno");
        final String session = client.get("session");
        final String connectid = client.get("connectid");
        log.info("[onIntervetionEvent] intervention: agentno {}, session {}, connectid {}, payload {}",
                agentno, session, connectid, received.toJsonObject());

        if (!received.valid()) {
            log.warn("[onEvent] intervention invalid message {}", received);
            return;
        }

        // 获得AgentUser
        final AgentUser agentUser = agentUserService.findById(received.getAgentuserid()).orElse(null);
        if (agentUser == null) {
            log.warn("未获取到 agent user: {}", received.getAgentuserid());
            return;
        }

        // 验证当前的SSO中的session是否和传入的session匹配
        if (agentSessionProxy.isInvalidSessionId(agentno, session, agentUser.getOrgi())) {
            // 该session信息不合法
            log.info("[onIntervetionEvent] invalid sessionId {}", session);
            // 强制退出
            client.sendEvent(Enums.MessageType.LEAVE.toString());
            return;
        }

        final User supervisor = userService.findById(received.getSupervisorid());
        final Date now = new Date();

        // 创建消息
        // 消息体
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(UUIDUtils.getUUID());
        chatMessage.setMessage(received.getContent());
        chatMessage.setCreatetime(now);
        chatMessage.setUpdatetime(now.getTime());

        // 访客接收消息，touser设置为agentUser userid
        chatMessage.setTouser(agentUser.getUserid());

        // 坐席发送消息，username设置为坐席
        chatMessage.setUsername(agentUser.getAgentname());
        chatMessage.setContextid(agentUser.getContextid());
        chatMessage.setUsession(agentUser.getUserid());
        chatMessage.setAgentserviceid(agentUser.getAgentserviceid());
        chatMessage.setAgentuser(agentUser.getId());

        /**
         * note 消息为会话监控干预消息的区分
         * 消息 setCalltype 是呼出，并且 intervented = true
         */
        // 消息中继续使用该会话的坐席发出，所以，访客看到的消息，依然是以同一坐席的名义
        chatMessage.setUserid(agentUser.getAgentno());
        // 坐席会话监控消息，设置为监控人员
        chatMessage.setCreater(supervisor.getId());
        // 监控人员名字
        chatMessage.setSupervisorname(supervisor.getUname());
        chatMessage.setChannel(agentUser.getChannel());
        chatMessage.setAppid(agentUser.getAppid());
        chatMessage.setOrgi(agentUser.getOrgi());
        chatMessage.setIntervented(true);

        // 消息类型
        chatMessage.setType(Enums.MessageType.MESSAGE.toString());
        chatMessage.setMsgtype(received.toMediaType().toString());
        chatMessage.setCalltype(Enums.CallType.OUT.toString());

        agentServiceService.sendChatMessageByAgent(chatMessage, agentUser);
    }

    /**
     * 接收到坐席通过WebIM发送的消息
     *
     * @param client
     * @param request
     * @param received
     */
    // 消息接收入口，当接收到消息后，查找发送目标客户端，并且向该客户端发送消息，且给自己发送消息
    @OnEvent(value = "message")
    public void onMessageEvent(final SocketIOClient client, final AckRequest request, final ChatMessage received) {
        final String agentno = client.get("agentno");
        final String session = client.get("session");
        final String connectid = client.get("connectid");
        received.setSessionid(session);

        log.info("[onMessageEvent] message: agentUserId {}, agentno {}, toUser {}, channel {}, orgi {}, appId {}, userId {}, sessionId {}, connectid {}",
                received.getAgentuser(), agentno, received.getTouser(),
                received.getChannel(), received.getOrgi(), received.getAppid(), received.getUserid(),
                session, connectid);


        // 验证当前的SSO中的session是否和传入的session匹配
        if (agentSessionProxy.isInvalidSessionId(agentno, session, received.getOrgi())) {
            // 该session信息不合法
            log.info("[onMessageEvent] invalid sessionId {}", session);
            // 强制退出
            client.sendEvent(Enums.MessageType.LEAVE.toString());
            return;
        }

        AgentUser agentUser = cacheService.findOneAgentUserByUserIdAndOrgi(received.getTouser(), received.getOrgi()).orElse(null);
        if (agentUser == null) {
            log.warn("为获取到 agent user {} {}", received.getTouser(), received.getOrgi());
            return;
        }

        // 判断用户在线状态，如果用户在线则通过webim发送 检查收发双方的信息匹配
        if (agentno == null || !StringUtils.equals(agentno, agentUser.getAgentno()) || AgentUserStatusEnum.END.check(agentUser.getStatus())) {
            log.warn("[onEvent] message: unknown condition.");
            return;
        }
        log.info("[onEvent] condition：visitor online.");

        // 消息体
        received.setCalltype(Enums.CallType.OUT.toString());
        if (StringUtils.isNotBlank(agentUser.getAgentno())) {
            received.setTouser(agentUser.getUserid());
        }

        received.setId(UUIDUtils.getUUID());
        received.setChannel(agentUser.getChannel());
        received.setUsession(agentUser.getUserid());
        received.setUsername(agentUser.getAgentname());
        received.setContextid(agentUser.getContextid());

        received.setAgentserviceid(agentUser.getAgentserviceid());
        received.setCreater(agentUser.getAgentno());

        if (StringUtils.equals(Enums.MediaType.COOPERATION.toString(), received.getMsgtype())) {
            received.setMsgtype(Enums.MediaType.COOPERATION.toString());
        } else {
            received.setMsgtype(Enums.MediaType.TEXT.toString());
        }

        agentServiceService.sendChatMessageByAgent(received, agentUser);

    }

    private WorkSession createWorkSession(HandshakeData handshakeData, String userid, String orgi, String session, String admin, InetSocketAddress address, int count, String id) {
        WorkSession workSession = new WorkSession();
        Date date = new Date();
        workSession.setCreatetime(date);
        workSession.setBegintime(date);
        workSession.setAgent(userid);
        workSession.setAgentno(userid);
        workSession.setAgentno(userid);
        workSession.setAdmin("true".equalsIgnoreCase(admin));

        workSession.setFirsttime(count == 0);

        workSession.setIpaddr(IPUtils.getIpAddress(handshakeData.getHttpHeaders(), address.getHostString()));
        workSession.setHostname(address.getHostName());
        workSession.setUserid(userid);
        workSession.setClientid(id);
        workSession.setSessionid(session);
        workSession.setOrgi(orgi);

        workSession.setDatestr(DateFormatEnum.DAY.format(date));

        return workSession;
    }
}
