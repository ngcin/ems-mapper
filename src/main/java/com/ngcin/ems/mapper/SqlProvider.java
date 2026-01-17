package com.ngcin.ems.mapper;

import com.ngcin.ems.mapper.core.IdType;
import com.ngcin.ems.mapper.core.MapperConsts;
import com.ngcin.ems.mapper.ref.TableFieldInfo;
import com.ngcin.ems.mapper.ref.TableInfo;
import org.apache.ibatis.jdbc.SQL;

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
    private static final String VERSION_INCREMENT = " = %s + 1";

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
     * Builds base SELECT SQL with soft delete condition.
     * This template method reduces duplication in select methods.
     *
     * @param tableInfo the table metadata
     * @return SQL builder with SELECT, FROM, and soft delete WHERE clause
     */
    protected SQL buildSelectBase(TableInfo tableInfo) {
        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }
        return sql;
    }

    /**
     * Applies optimistic locking logic to UPDATE statements.
     * Adds version increment in SET clause and version check in WHERE clause.
     *
     * @param sql the SQL builder
     * @param tableInfo the table metadata
     * @param entity the entity being updated
     * @param prefix optional parameter prefix (e.g., "entity")
     * @throws MapperException if version field is null
     */
    protected void applyVersionLocking(SQL sql, TableInfo tableInfo, Object entity, String prefix) {
        if (!tableInfo.hasVersion()) {
            return;
        }

        TableFieldInfo versionField = tableInfo.versionField();
        Object currentVersion = getFieldValue(versionField.field(), entity);
        requireNonNull(currentVersion, "Version field for optimistic locking");

        applyVersionIncrement(sql, versionField);
        sql.WHERE(buildWhereClause(versionField, prefix));
    }

    /**
     * Appends LIMIT clause to SQL statement.
     * Centralizes LIMIT handling for easier dialect-specific customization.
     *
     * @param sql the SQL statement
     * @param limit the limit value
     * @return SQL with LIMIT clause appended
     */
    protected String appendLimit(String sql, int limit) {
        return sql + " LIMIT " + limit;
    }

    /**
     * Adds ID field to INSERT statement if not AUTO type.
     *
     * @param sql the SQL builder
     * @param tableInfo the table metadata
     */
    protected void addIdFieldIfNeeded(SQL sql, TableInfo tableInfo) {
        if (tableInfo.idField() != null && tableInfo.idField().idType() != IdType.AUTO) {
            sql.VALUES(tableInfo.idField().column(), buildValuePlaceholder(tableInfo.idField()));
        }
    }

    /**
     * Applies version increment to SET clause.
     *
     * @param sql the SQL builder
     * @param versionField the version field metadata
     */
    protected void applyVersionIncrement(SQL sql, TableFieldInfo versionField) {
        sql.SET(versionField.column() + String.format(VERSION_INCREMENT, versionField.column()));
    }

    /**
     * Adds entity conditions (soft delete + non-null fields) to SELECT/UPDATE/DELETE.
     *
     * @param sql the SQL builder
     * @param tableInfo the table metadata
     * @param entity the entity object (may be null for some operations)
     */
    protected void addEntityConditions(SQL sql, TableInfo tableInfo, Object entity) {
        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }

        if (entity != null) {
            String[] entityWheres = buildEntityWheres(tableInfo, entity);
            if (entityWheres.length > 0) {
                sql.WHERE(entityWheres);
            }
        }
    }

    /**
     * Validates that an object is not null.
     *
     * @param obj the object to check
     * @param paramName the parameter name for error message
     * @throws MapperException if object is null
     */
    protected void requireNonNull(Object obj, String paramName) {
        if (obj == null) {
            throw new MapperException(paramName + " cannot be null");
        }
    }

    /**
     * Validates that a TableInfo has an ID field.
     *
     * @param tableInfo the table metadata
     * @throws MapperException if ID field is missing
     */
    protected void requireIdField(TableInfo tableInfo) {
        if (tableInfo.idField() == null) {
            throw new MapperException("Entity " + tableInfo.entityClass().getSimpleName()
                + " must have an @Id field");
        }
    }

    /**
     * Builds SET clause for UPDATE statements.
     * Example: "column_name = #{propertyName}" or "column_name = #{prefix.propertyName}"
     *
     * @param field the field metadata
     * @param prefix optional parameter prefix (e.g., "entity", "et")
     * @return SET clause string
     */
    protected String buildSetClause(TableFieldInfo field, String prefix) {
        String property = prefix != null ? prefix + "." + field.getProperty() : field.getProperty();
        return field.column() + " = #{" + property + "}";
    }

    /**
     * Builds WHERE clause condition.
     * Example: "column_name = #{propertyName}" or "column_name = #{prefix.propertyName}"
     *
     * @param field the field metadata
     * @param prefix optional parameter prefix (e.g., "entity", "ew")
     * @return WHERE clause string
     */
    protected String buildWhereClause(TableFieldInfo field, String prefix) {
        String property = prefix != null ? prefix + "." + field.getProperty() : field.getProperty();
        return field.column() + " = #{" + property + "}";
    }

    /**
     * Initializes entity fields before insert (ID, version, soft delete).
     * This method handles:
     * - ID generation for UUID/SNOWFLAKE types (AUTO is handled by database)
     * - Soft delete field initialization to undeleted value (0)
     * - Version field initialization to 0
     *
     * @param entity the entity to initialize
     * @param tableInfo the table metadata
     */
    protected void initializeEntityForInsert(Object entity, TableInfo tableInfo) {
        // Handle ID generation for UUID/SNOWFLAKE
        if (tableInfo.idField() != null && tableInfo.idField().idType() != IdType.AUTO) {
            TableFieldInfo idField = tableInfo.idField();
            Object idValue = getFieldValue(idField.field(), entity);
            if (idValue == null) {
                idValue = generateId(idField.idType(), idField.getPropertyType());
                setFieldValue(idField.field(), entity, idValue);
            }
        }

        // Initialize soft delete field to undeleted value
        if (tableInfo.hasLogicDelete()) {
            TableFieldInfo deletedField = tableInfo.deletedField();
            if (getFieldValue(deletedField.field(), entity) == null) {
                int undeleted = Integer.parseInt(deletedField.notDeletedValue());
                setFieldValue(deletedField.field(), entity, undeleted);
            }
        }

        // Initialize version field to 0
        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            if (getFieldValue(versionField.field(), entity) == null) {
                setFieldValue(versionField.field(), entity, 0);
            }
        }
    }

    /**
     * Gets field value from entity.
     * Note: Field is already made accessible during metadata resolution in EntityClassResolver.
     */
    protected Object getFieldValue(Field field, Object entity) {
        try {
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to get field value: " + field.getName(), e);
        }
    }

    /**
     * Sets field value on entity.
     * Note: Field is already made accessible during metadata resolution in EntityClassResolver.
     */
    protected void setFieldValue(Field field, Object entity, Object value) {
        try {
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to set field value: " + field.getName(), e);
        }
    }
}
