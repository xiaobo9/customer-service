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

package com.chatopera.cc.service;

import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.entity.AttachmentFile;
import com.github.xiaobo9.repository.AttachmentRepository;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;

@Service
public class AttachmentService {
    @Value("${web.upload-path}")
    private String path;

    @Autowired
    private AttachmentRepository attachementRes;

    public String processAttachmentFile(
            final MultipartFile file,
            final String fileid,
            final String orgi,
            final String creator
    ) throws IOException {
        String id = null;

        if (file.getSize() > 0) {            //文件尺寸 限制 ？在 启动 配置中 设置 的最大值，其他地方不做限制
            AttachmentFile attachmentFile = new AttachmentFile();
            attachmentFile.setCreater(creator);
            attachmentFile.setOrgi(orgi);
            attachmentFile.setModel(Enums.ModelType.WEBIM.toString());
            attachmentFile.setFilelength((int) file.getSize());
            if (file.getContentType() != null && file.getContentType().length() > 255) {
                attachmentFile.setFiletype(file.getContentType().substring(0, 255));
            } else {
                attachmentFile.setFiletype(file.getContentType());
            }
            String originalFilename = URLDecoder.decode(file.getOriginalFilename(), "utf-8");
            File uploadFile = new File(originalFilename);
            if (uploadFile.getName() != null && uploadFile.getName().length() > 255) {
                attachmentFile.setTitle(uploadFile.getName().substring(0, 255));
            } else {
                attachmentFile.setTitle(uploadFile.getName());
            }
            if (StringUtils.isNotBlank(attachmentFile.getFiletype()) && attachmentFile.getFiletype().indexOf("image") >= 0) {
                attachmentFile.setImage(true);
            }
            attachmentFile.setFileid(fileid);
            attachementRes.save(attachmentFile);
            FileUtils.writeByteArrayToFile(new File(path, "upload/" + fileid), file.getBytes());
            id = attachmentFile.getId();
        }
        return id;
    }
}
