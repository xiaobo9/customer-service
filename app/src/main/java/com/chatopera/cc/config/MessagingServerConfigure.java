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
package com.chatopera.cc.config;

import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.exception.InstantMessagingExceptionListener;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

@org.springframework.context.annotation.Configuration
public class MessagingServerConfigure {
    public static final int MAX_WORK_THREADS = 1000;
    @Value("${uk.im.server.port}")
    private Integer port;

    @Value("${cs.im.server.ssl.port}")
    private Integer sslPort;

    @Value("${web.upload-path}")
    private String path;

    @Value("${uk.im.server.threads:20}")
    private int threads;

    private SocketIOServer server;

    public Integer getWebIMPort() {
        return sslPort != null ? sslPort : port;
    }

    @Bean
    public SocketIOServer socketIOServer() throws NoSuchAlgorithmException, IOException {
        Configuration config = new Configuration();
        config.setPort(port);
        config.setExceptionListener(new InstantMessagingExceptionListener());

        File sslFile = new File(path, "ssl/https.properties");
        if (sslFile.exists()) {
            Properties sslProperties = new Properties();
            try (FileInputStream in = new FileInputStream(sslFile)) {
                sslProperties.load(in);
            }
            String keyStore = sslProperties.getProperty("key-store");
            String storePassword = sslProperties.getProperty("key-store-password");
            if (StringUtils.isNotBlank(keyStore) && StringUtils.isNotBlank(storePassword)) {
                config.setKeyStorePassword(MainUtils.decryption(storePassword));
                try (InputStream stream = new FileInputStream(new File(path, "ssl/" + keyStore))) {
                    config.setKeyStore(stream);
                }
            }
        }

        config.setWorkerThreads(threads <= MAX_WORK_THREADS ? threads : MAX_WORK_THREADS);
        config.setAuthorizationListener(data -> true);
        SocketConfig socketConfig = config.getSocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setSoLinger(0);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setTcpKeepAlive(true);

        return server = new SocketIOServer(config);
    }

    @Bean
    public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketServer) {
        return new SpringAnnotationScanner(socketServer);
    }

    @PreDestroy
    public void destroy() {
        server.stop();
    }
}  