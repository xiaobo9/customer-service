/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2019 Chatopera Inc, <https://www.chatopera.com>
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
package com.chatopera.cc.util;

import com.chatopera.cc.service.OnlineUserService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WebSseEmitterClient {
    private final static Logger logger = LoggerFactory.getLogger(WebSseEmitterClient.class);
    private ConcurrentMap<String, WebIMClient> imClientsMap = new ConcurrentHashMap<>();
    private OnlineUserService onlineUserService;

    public WebSseEmitterClient(OnlineUserService onlineUserService) {
        this.onlineUserService = onlineUserService;
    }

    public List<WebIMClient> getClients(String userid) {

        Collection<WebIMClient> values = imClientsMap.values();
        List<WebIMClient> clients = new ArrayList<WebIMClient>();
        for (WebIMClient client : values) {
            if (client.getUserid().equals(userid)) {
                clients.add(client);
            }
        }
        return clients;
    }

    public int size() {
        return imClientsMap.size();
    }

    public void putClient(String userid, WebIMClient client) {
        imClientsMap.put(client.getClient(), client);
    }

    public void removeClient(String userid, String client, boolean timeout) throws Exception {
        List<WebIMClient> keyClients = this.getClients(userid);
        for (int i = 0; i < keyClients.size(); i++) {
            WebIMClient webIMClient = keyClients.get(i);
            if (StringUtils.equals(webIMClient.getClient(), client)) {

                imClientsMap.remove(client);
                keyClients.remove(i);
                break;
            }
        }
        if (keyClients.size() == 0 && timeout) {
            onlineUserService.offline(userid, userid);
        }
    }
}
