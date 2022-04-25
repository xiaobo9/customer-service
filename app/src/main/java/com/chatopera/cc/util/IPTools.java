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
package com.chatopera.cc.util;

import com.chatopera.cc.basic.MainContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.lionsoul.ip2region.DataBlock;
import org.lionsoul.ip2region.DbConfig;
import org.lionsoul.ip2region.DbMakerConfigException;
import org.lionsoul.ip2region.DbSearcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Slf4j
public class IPTools {
    private static final String IP_DATA_PATH = "WEB-INF/data/ip/ip2region.db";
    private static final IPTools iptools = new IPTools();
    private DbSearcher _searcher = null;
    private static boolean inited = false;

    public static IPTools getInstance() {
        return iptools;
    }

    public static IP findGeography(String remote) {
        return getInstance().findGeographyInner(remote);
    }

    public IP findGeographyInner(String remote) {
        IP ip = new IP();
        try {
            DataBlock block = _searcher.binarySearch(remote != null ? remote : "127.0.0.1");
            if (block == null || block.getRegion() == null) {
                return ip;
            }
            String[] region = block.getRegion().split("[|]");
            if (region.length != 5) {
                return ip;
            }
            ip.setCountry(region[0]);
            ip.setRegion(dealString(region[1]));
            ip.setProvince(dealString(region[2]));
            ip.setCity(dealString(region[3]));
            ip.setIsp(dealString(region[4]));

        } catch (Exception ex) {
            log.warn("", ex);
        }
        return ip;
    }

    private static String dealString(String str) {
        if (StringUtils.isBlank(str) || str.equalsIgnoreCase("null")) {
            return "";
        }
        return str;
    }

    public static synchronized void init() {
        if (inited) {
            return;
        }
        try {
            String property = MainContext.getContext().getEnvironment().getProperty("web.upload-path");
            File dbFile = new File(property, "ipdata/ipdata.db");
            if (!dbFile.exists()) {
                ClassLoader classLoader = IPTools.class.getClassLoader();
                InputStream stream = classLoader.getResourceAsStream(IP_DATA_PATH);
                FileUtils.copyInputStreamToFile(Objects.requireNonNull(stream), dbFile);
            }
            iptools._searcher = new DbSearcher(new DbConfig(), dbFile.getAbsolutePath());
            inited = true;
        } catch (DbMakerConfigException | IOException e) {
            log.warn("", e);
        }
    }

}
