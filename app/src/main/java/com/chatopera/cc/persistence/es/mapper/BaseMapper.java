package com.chatopera.cc.persistence.es.mapper;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.common.document.DocumentField;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.core.AbstractResultMapper;
import org.springframework.data.elasticsearch.core.EntityMapper;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentEntity;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentProperty;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.context.MappingContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedList;

public abstract class BaseMapper extends AbstractResultMapper {
    protected MappingContext<? extends ElasticsearchPersistentEntity<?>, ElasticsearchPersistentProperty> mappingContext;

    public BaseMapper(EntityMapper entityMapper) {
        super(entityMapper);
    }

    protected String buildJSONFromFields(Collection<DocumentField> values) {
        JsonFactory nodeFactory = new JsonFactory();
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             JsonGenerator generator = nodeFactory.createGenerator(stream, JsonEncoding.UTF8);) {
            generator.writeStartObject();
            for (DocumentField value : values) {
                if (value.getValues().size() > 1) {
                    generator.writeArrayFieldStart(value.getName());
                    for (Object val : value.getValues()) {
                        generator.writeObject(val);
                    }
                    generator.writeEndArray();
                } else {
                    generator.writeObjectField(value.getName(), value.getValue());
                }
            }
            generator.writeEndObject();
            generator.flush();
            return new String(stream.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public <T> T mapResult(GetResponse response, Class<T> clazz) {
        T result = mapEntity(response.getSourceAsString(), clazz);
        if (result != null) {
            setPersistentEntityId(result, response.getId(), clazz);
        }
        return result;
    }

    @Override
    public <T> LinkedList<T> mapResults(MultiGetResponse responses, Class<T> clazz) {
        LinkedList<T> list = new LinkedList<>();
        for (MultiGetItemResponse response : responses.getResponses()) {
            if (!response.isFailed() && response.getResponse().isExists()) {
                T result = mapEntity(response.getResponse().getSourceAsString(), clazz);
                setPersistentEntityId(result, response.getResponse().getId(), clazz);
                list.add(result);
            }
        }
        return list;
    }

    protected <T> void setPersistentEntityId(T result, String id, Class<T> clazz) {
        if (mappingContext != null && clazz.isAnnotationPresent(Document.class)) {

            ElasticsearchPersistentEntity<?> persistentEntity = mappingContext.getPersistentEntity(clazz);
            PersistentProperty<?> idProperty = persistentEntity.getIdProperty();

            // Only deal with String because ES generated Ids are strings !
            if (idProperty != null && idProperty.getType().isAssignableFrom(String.class)) {
                persistentEntity.getPropertyAccessor(result).setProperty(idProperty, id);
            }
        }
    }
}
