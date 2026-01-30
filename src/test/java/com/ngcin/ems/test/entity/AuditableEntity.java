package com.ngcin.ems.test.entity;

import java.time.LocalDateTime;

/**
 * Middle-level entity for testing multi-level inheritance.
 * Extends BaseEntity (has @Id) and adds audit fields.
 */
public class AuditableEntity extends BaseEntity {

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AuditableEntity() {
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
