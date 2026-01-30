package com.ngcin.ems.test.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.ngcin.ems.mapper.annotations.Id;
import com.ngcin.ems.mapper.annotations.Table;
import com.ngcin.ems.mapper.core.IdType;
import com.ngcin.ems.mapper.json.JsonNodeValue;

@Table("t_json_entity")
public class JsonEntity {

    @Id(type = IdType.AUTO)
    private Long id;

    private String name;

    private JsonNode metadata;

    private JsonNode tags;

    private JsonNodeValue config;

    public JsonEntity() {
    }

    public JsonEntity(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    public JsonNode getTags() {
        return tags;
    }

    public void setTags(JsonNode tags) {
        this.tags = tags;
    }

    public JsonNodeValue getConfig() {
        return config;
    }

    public void setConfig(JsonNodeValue config) {
        this.config = config;
    }
}
