package com.ngcin.ems.test.entity;

import com.ngcin.ems.mapper.annotations.Table;

/**
 * Test entity for three-level inheritance.
 * BaseEntity (has @Id) -> AuditableEntity (has createdAt, updatedAt) -> MultiLevelEntity (has name)
 */
@Table("t_multi_level")
public class MultiLevelEntity extends AuditableEntity {

    private String name;

    public MultiLevelEntity() {
    }

    public MultiLevelEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "MultiLevelEntity{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", createdAt=" + getCreatedAt() +
                ", updatedAt=" + getUpdatedAt() +
                '}';
    }
}
