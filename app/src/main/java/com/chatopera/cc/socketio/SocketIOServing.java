/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chatopera.cc.socketio;

import com.chatopera.cc.activemq.BrokerPublisher;
import com.github.xiaobo9.commons.enums.Enums;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.proxy.AgentProxy;
import com.chatopera.cc.proxy.AgentSessionProxy;
import com.chatopera.cc.proxy.AgentUserProxy;
import com.chatopera.cc.proxy.UserProxy;
import com.chatopera.cc.socketio.handler.AgentEventHandler;
import com.chatopera.cc.socketio.handler.EntIMEventHandler;
import com.chatopera.cc.socketio.handler.IMEventHandler;
import com.corundumstudio.socketio.SocketIONamespace;
import com.corundumstudio.socketio.SocketIOServer;
import com.github.xiaobo9.repository.AgentStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class SocketIOServing implements CommandLineRunner {
    private final SocketIOServer server;
    private final SocketIONamespace imSocketNameSpace;
    private final SocketIONamespace agentSocketIONameSpace;
    private final SocketIONamespace entIMSocketIONameSpace;

    @Autowired
    private BrokerPublisher brokerPublisher;
    @Autowired
    private AgentStatusRepository agentStatusRes;
    @Autowired
    private AgentUserProxy agentUserProxy;
    @Autowired
    private AgentProxy agentProxy;
    @Autowired
    private AgentSessionProxy agentSessionProxy;
    @Autowired
    private UserProxy userProxy;

    @Autowired
    public SocketIOServing(SocketIOServer server) {
        this.server = server;
        // 访客聊天
        imSocketNameSpace = server.addNamespace(Enums.NameSpaceEnum.IM.getNamespace());
        // 坐席聊天
        agentSocketIONameSpace = server.addNamespace(Enums.NameSpaceEnum.AGENT.getNamespace());
        // 企业聊天
        entIMSocketIONameSpace = server.addNamespace(Enums.NameSpaceEnum.ENTIM.getNamespace());
    }

    @Bean(name = "imNamespace")
    public SocketIONamespace getIMSocketIONameSpace(SocketIOServer server) {
        imSocketNameSpace.addListeners(new IMEventHandler(server));
        return imSocketNameSpace;
    }

    @Bean(name = "agentNamespace")
    public SocketIONamespace getAgentSocketIONameSpace(SocketIOServer server) {
        agentSocketIONameSpace.addListeners(new AgentEventHandler(server,
                brokerPublisher,
                agentStatusRes,
                agentUserProxy,
                agentProxy,
                agentSessionProxy,
                userProxy
        ));
        return agentSocketIONameSpace;
    }

    @Bean(name = "entimNamespace")
    public SocketIONamespace getEntIMSocketIONameSpace(EntIMEventHandler handler) {
        entIMSocketIONameSpace.addListeners(handler);
        return entIMSocketIONameSpace;
    }


    public SocketIOServer getServer() {
        return server;
    }

    @Override
    public void run(String... args) throws Exception {
        server.start();
        MainContext.setIMServerStatus(true);    // IMServer 启动成功
    }
}  
