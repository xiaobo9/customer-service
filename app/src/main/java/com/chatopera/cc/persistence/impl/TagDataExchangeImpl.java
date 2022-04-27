package com.chatopera.cc.persistence.impl;

import com.chatopera.cc.persistence.interfaces.DataExchangeInterface;
import com.github.xiaobo9.entity.Tag;
import com.github.xiaobo9.repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

@Service("tagdata")
public class TagDataExchangeImpl implements DataExchangeInterface {
    @Autowired
    private TagRepository tagRes;

    public String getDataByIdAndOrgi(String id, String orgi) {
        Tag tag = tagRes.findByOrgiAndId(orgi, id);
        return tag != null ? tag.getTag() : id;
    }

    @Override
    public List<Serializable> getListDataByIdAndOrgi(String id, String creater, String orgi) {
        return null;
    }

    public void process(Object data, String orgi) {

    }
}
