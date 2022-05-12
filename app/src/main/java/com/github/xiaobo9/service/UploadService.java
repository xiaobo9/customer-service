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

package com.github.xiaobo9.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;

@Slf4j
@Service
public class UploadService {
    public static final String UPLOAD = "upload";

    @Getter
    @Value("${web.upload-path}")
    private String dataPath;

    @Getter
    private String uploadPath;

    @PostConstruct
    public void postConstruct() {
        log.info("创建上传目录, {}", dataPath);
        File uploadDir = new File(dataPath, UPLOAD);
        uploadPath = uploadDir.getAbsolutePath();
        if (uploadDir.exists()) {
            return;
        }
        if (uploadDir.mkdirs()) {
            return;
        }
        throw new RuntimeException("创建上传目录失败");
    }
}
