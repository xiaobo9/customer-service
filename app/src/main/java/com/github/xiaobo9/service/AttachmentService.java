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

import com.chatopera.cc.basic.Constants;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.kit.StringKit;
import com.github.xiaobo9.entity.AttachmentFile;
import com.github.xiaobo9.repository.AttachmentRepository;
import com.github.xiaobo9.service.UploadService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Objects;

@Service
public class AttachmentService {
    private final AttachmentRepository attachmentRes;

    private final UploadService uploadService;

    public AttachmentService(AttachmentRepository attachmentRes, UploadService uploadService) {
        this.attachmentRes = attachmentRes;
        this.uploadService = uploadService;
    }

    public AttachmentFile processAttachmentFile(MultipartFile multipart, String fileid, String orgi, String creator)
            throws IOException {
        String originalFilename = Objects.requireNonNull(multipart.getOriginalFilename());

        AttachmentFile attachmentFile = new AttachmentFile();
        attachmentFile.setCreater(creator);
        attachmentFile.setOrgi(orgi);
        attachmentFile.setFileid(fileid);
        attachmentFile.setModel(Enums.ModelType.WEBIM.toString());
        attachmentFile.setFilelength(multipart.getSize());

        attachmentFile.setFiletype(StringKit.subLongString(multipart.getContentType(), 255));
        attachmentFile.setTitle(StringKit.subLongString(URLDecoder.decode(originalFilename, "utf-8"), 255));

        attachmentFile.setImage(StringUtils.contains(attachmentFile.getFiletype(), Constants.ATTACHMENT_TYPE_IMAGE));

        attachmentRes.save(attachmentFile);

        FileUtils.writeByteArrayToFile(new File(uploadService.getUploadPath(), fileid), multipart.getBytes());

        return attachmentFile;
    }
}
