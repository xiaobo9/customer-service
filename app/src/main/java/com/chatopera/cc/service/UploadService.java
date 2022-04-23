package com.chatopera.cc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;

@Slf4j
@Service
public class UploadService {
    public static final String UPLOAD = "upload";
    @Value("${web.upload-path}")
    private String path;

    private String uploadPath;

    public String getUploadPath() {
        return uploadPath;
    }

    @PostConstruct
    public void postConstruct() {
        log.info("创建上传目录, {}", path);
        File uploadDir = new File(path, UPLOAD);
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
