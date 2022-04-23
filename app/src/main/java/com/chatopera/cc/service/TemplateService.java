package com.chatopera.cc.service;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.cache.Cache;
import com.chatopera.cc.model.Template;
import com.chatopera.cc.persistence.repository.TemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TemplateService {

    @Autowired
    private TemplateRepository repository;

    @Autowired
    private Cache cache;

    public Template getTemplate(String id) {
        Template template = cache.findOneSystemByIdAndOrgi(id, Constants.SYSTEM_ORGI);
        if (template == null) {
            // FIXME 并发处理
            template = repository.findByIdAndOrgi(id, Constants.SYSTEM_ORGI);
            cache.putSystemByIdAndOrgi(id, Constants.SYSTEM_ORGI, template);
        }
        return template;
    }

}
