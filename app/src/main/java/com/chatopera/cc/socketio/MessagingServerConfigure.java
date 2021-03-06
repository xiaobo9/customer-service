/*
 * Copyright 2022 xiaobo9 <https://github.com/xiaobo9>
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

import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.socketio.MsgExceptionListener;
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
import java.nio.file.Files;
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
        config.setExceptionListener(new MsgExceptionListener());
        ssl(config);
        config.setWorkerThreads(threads <= MAX_WORK_THREADS ? threads : MAX_WORK_THREADS);
        SocketConfig socketConfig = config.getSocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setSoLinger(0);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setTcpKeepAlive(true);

        return server = new SocketIOServer(config);
    }

    private void ssl(Configuration config) throws IOException, NoSuchAlgorithmException {
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
                try (InputStream stream = Files.newInputStream(new File(path, "ssl/" + keyStore).toPath())) {
                    config.setKeyStore(stream);
                }
            }
        }
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