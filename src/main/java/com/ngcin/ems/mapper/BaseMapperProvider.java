package com.ngcin.ems.mapper;

import com.ngcin.ems.mapper.core.IdType;
import com.ngcin.ems.mapper.ref.EntityClassResolver;
import com.ngcin.ems.mapper.ref.TableFieldInfo;
import com.ngcin.ems.mapper.ref.TableInfo;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.apache.ibatis.jdbc.SQL;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class BaseMapperProvider {

    private static final AtomicLong SNOWFLAKE_COUNTER = new AtomicLong(0);
    private static final long EPOCH = 1609459200000L; // 2021-01-01 00:00:00 UTC

    private static Class<?> getType(ProviderContext providerContext) {
        Class<?> mapperType = providerContext.getMapperType();
        ParameterizedType genericSuperclass = (ParameterizedType) mapperType.getGenericInterfaces()[0];
        return (Class<?>) genericSuperclass.getActualTypeArguments()[0];
    }

    /**
     * Generates INSERT SQL for an entity.
     *
     * @param entity the entity to insert
     * @return the INSERT SQL statement
     */
    public String insert(Object entity) {
        Class<?> entityClass = entity.getClass();
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        SQL sql = new SQL();
        sql.INSERT_INTO(tableInfo.tableName());

        List<TableFieldInfo> insertFields = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Handle ID field based on generation strategy
        if (tableInfo.idField() != null) {
            TableFieldInfo idField = tableInfo.idField();
            IdType idType = idField.idType();

            if (idType == IdType.AUTO) {
                // For AUTO, don't include ID in INSERT (database will generate it)
                // Do nothing, skip adding ID field
            } else {
                // For UUID and SNOWFLAKE, generate ID before insert
                Object idValue = getFieldValue(idField.field(), entity);
                if (idValue == null) {
                    idValue = generateId(idType, idField.getPropertyType());
                    setFieldValue(idField.field(), entity, idValue);
                }
                insertFields.add(idField);
            }
        }

        // Add all non-ID fields
        for (TableFieldInfo fieldInfo : tableInfo.fields()) {
            if (!fieldInfo.isId()) {
                insertFields.add(fieldInfo);
            }
        }

        // Build columns and VALUES placeholders
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();

        for (int i = 0; i < insertFields.size(); i++) {
            TableFieldInfo fieldInfo = insertFields.get(i);
            if (i > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(fieldInfo.column());
            placeholders.append("#{").append(fieldInfo.getProperty()).append("}");
        }

        String sqlStatement = "INSERT INTO " + tableInfo.tableName() + " (" + columns + ") VALUES (" + placeholders + ")";
        return sqlStatement;
    }

    /**
     * Generates ID value based on the specified type.
     */
    private Object generateId(IdType idType, Class<?> targetType) {
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
    private Object generateSnowflakeId(Class<?> targetType) {
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
    private Object getFieldValue(Field field, Object entity) {
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
    private void setFieldValue(Field field, Object entity, Object value) {
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to set field value: " + field.getName(), e);
        }
    }
}
