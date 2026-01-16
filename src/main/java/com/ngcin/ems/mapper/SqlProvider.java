package com.ngcin.ems.mapper;

import com.ngcin.ems.mapper.core.IdType;
import com.ngcin.ems.mapper.core.MapperConsts;
import com.ngcin.ems.mapper.ref.TableFieldInfo;
import com.ngcin.ems.mapper.ref.TableInfo;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base SQL provider with utility methods for SQL generation.
 * Uses org.apache.ibatis.jdbc.SQL for cleaner SQL construction.
 */
public class SqlProvider {
    private static final AtomicLong SNOWFLAKE_COUNTER = new AtomicLong(0);
    private static final long EPOCH = 1609459200000L; // 2021-01-01 00:00:00 UTC
    protected static final int DEFAULT_SELECT_ALL_LIMIT = 1000;

    /**
     * Builds SELECT columns as String array for SQL class.
     *
     * @param tableInfo the table metadata
     * @return array of column names
     */
    protected String[] buildSelectColumns(TableInfo tableInfo) {
        return tableInfo.fields().stream()
                .map(TableFieldInfo::column)
                .toArray(String[]::new);
    }

    /**
     * Builds soft delete WHERE condition.
     *
     * @param tableInfo the table metadata
     * @return WHERE condition string, or null if no soft delete
     */
    protected String buildSoftDeleteWhere(TableInfo tableInfo) {
        if (!tableInfo.hasLogicDelete()) {
            return null;
        }
        TableFieldInfo deletedField = tableInfo.deletedField();
        return deletedField.column() + " = " + deletedField.notDeletedValue();
    }

    /**
     * Builds WHERE conditions from non-null entity fields.
     * Excludes ID field, version field, and deleted field.
     *
     * @param tableInfo the table metadata
     * @param entity    the entity object
     * @return array of WHERE conditions
     */
    protected String[] buildEntityWheres(TableInfo tableInfo, Object entity) {
        List<String> conditions = new ArrayList<>();

        for (TableFieldInfo fieldInfo : tableInfo.fields()) {
            // Skip ID, version, and deleted fields
            if (fieldInfo.isId() || fieldInfo.isVersion() || fieldInfo.isDeleted()) {
                continue;
            }

            Object value = getFieldValue(fieldInfo.field(), entity);
            if (value != null) {
                conditions.add(fieldInfo.column() + " = #{" +
                        MapperConsts.ENTITY_WHERE + "." +
                        fieldInfo.getProperty() + "}");
            }
        }

        return conditions.toArray(new String[0]);
    }

    /**
     * Builds IN condition for batch ID queries.
     *
     * @param column the column name
     * @param values the collection of values
     * @return IN condition string like "id IN (#{ids[0]}, #{ids[1]}, ...)"
     */
    protected String buildInCondition(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("IN condition values cannot be empty");
        }

        StringBuilder condition = new StringBuilder(column).append(" IN (");

        int index = 0;
        for (Object value : values) {
            if (index > 0) {
                condition.append(", ");
            }
            condition.append("#{ids[").append(index).append("]}");
            index++;
        }

        condition.append(")");
        return condition.toString();
    }

    /**
     * Builds parameter placeholder with type information.
     *
     * @param field the field metadata
     * @return placeholder string like "#{property,javaType=...,jdbcType=...}"
     */
    protected String buildValuePlaceholder(TableFieldInfo field) {
        StringBuilder placeholder = new StringBuilder("#{");
        placeholder.append(field.getProperty());

        if (field.getPropertyType() == UUID.class) {
            placeholder.append(",javaType=java.util.UUID");
        }

        if (field.jdbcType() != null) {
            placeholder.append(",jdbcType=")
                    .append(org.apache.ibatis.type.JdbcType.forCode(field.jdbcType()).name());
        }

        placeholder.append("}");
        return placeholder.toString();
    }

    /**
     * Generates ID value based on the specified type.
     */
    protected Object generateId(IdType idType, Class<?> targetType) {
        return switch (idType) {
            case UUID -> {
                if (targetType == String.class) {
                    yield UUID.randomUUID().toString();
                } else if (targetType == UUID.class) {
                    yield UUID.randomUUID();
                } else {
                    throw new IllegalArgumentException("Unsupported target type for UUID: " + targetType);
                }
            }
            case SNOWFLAKE -> generateSnowflakeId(targetType);
            case AUTO -> throw new IllegalStateException("AUTO ID should not be generated here");
        };
    }

    /**
     * Generates a Snowflake ID.
     * Simple implementation: timestamp + node + sequence
     */
    protected Object generateSnowflakeId(Class<?> targetType) {
        long timestamp = System.currentTimeMillis() - EPOCH;
        long node = 1; // In production, this should be configurable
        long sequence = SNOWFLAKE_COUNTER.getAndIncrement() % 4096;

        long snowflakeId = (timestamp << 22) | (node << 12) | sequence;

        if (targetType == Long.class || targetType == long.class) {
            return snowflakeId;
        } else if (targetType == String.class) {
            return String.valueOf(snowflakeId);
        } else {
            throw new IllegalArgumentException("Unsupported target type for SNOWFLAKE: " + targetType);
        }
    }

    /**
     * Gets field value from entity.
     */
    protected Object getFieldValue(Field field, Object entity) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to get field value: " + field.getName(), e);
        }
    }

    /**
     * Sets field value on entity.
     */
    protected void setFieldValue(Field field, Object entity, Object value) {
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to set field value: " + field.getName(), e);
        }
    }
}
