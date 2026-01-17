package com.ngcin.ems.test.entity;

import com.ngcin.ems.mapper.annotations.Id;
import com.ngcin.ems.mapper.core.IdType;

public class BaseEntity {
    @Id(type = IdType.SNOWFLAKE)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
