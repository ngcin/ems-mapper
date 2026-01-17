package com.ngcin.ems.mapper;

import com.ngcin.ems.mapper.core.IdType;
import com.ngcin.ems.mapper.core.MapperConsts;
import com.ngcin.ems.mapper.ref.EntityClassResolver;
import com.ngcin.ems.mapper.ref.TableFieldInfo;
import com.ngcin.ems.mapper.ref.TableInfo;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.apache.ibatis.jdbc.SQL;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Base mapper provider using org.apache.ibatis.jdbc.SQL for clean SQL generation.
 */
public class BaseMapperProvider extends SqlProvider {

    private static Class<?> getType(ProviderContext providerContext) {
        Class<?> mapperType = providerContext.getMapperType();
        ParameterizedType genericSuperclass = (ParameterizedType) mapperType.getGenericInterfaces()[0];
        return (Class<?>) genericSuperclass.getActualTypeArguments()[0];
    }

    /**
     * Generates INSERT SQL for non-null fields only (selective insert).
     *
     * @param entity the entity to insert
     * @return the INSERT SQL statement
     */
    public String insertSelective(Object entity) {
        Class<?> entityClass = entity.getClass();
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        SQL sql = new SQL().INSERT_INTO(tableInfo.tableName());

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
                sql.VALUES(idField.column(), buildValuePlaceholder(idField));
            }
        }

        // Initialize deleted field to undeleted value if null
        if (tableInfo.hasLogicDelete()) {
            TableFieldInfo deletedField = tableInfo.deletedField();
            Object deletedValue = getFieldValue(deletedField.field(), entity);
            if (deletedValue == null) {
                // Set to undeleted value (0)
                int undeleted = Integer.parseInt(deletedField.notDeletedValue());
                setFieldValue(deletedField.field(), entity, undeleted);
            }
        }

        // Initialize version field to 0 if null
        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            Object versionValue = getFieldValue(versionField.field(), entity);
            if (versionValue == null) {
                // Set to initial version (0)
                setFieldValue(versionField.field(), entity, 0);
            }
        }

        // Add only non-null fields
        for (TableFieldInfo fieldInfo : tableInfo.getNonIdFields()) {
            Object value = getFieldValue(fieldInfo.field(), entity);
            if (value != null) {
                sql.VALUES(fieldInfo.column(), buildValuePlaceholder(fieldInfo));
            }
        }

        return sql.toString();
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

        SQL sql = new SQL().INSERT_INTO(tableInfo.tableName());

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
                sql.VALUES(idField.column(), buildValuePlaceholder(idField));
            }
        }

        // Initialize deleted field to undeleted value if null
        if (tableInfo.hasLogicDelete()) {
            TableFieldInfo deletedField = tableInfo.deletedField();
            Object deletedValue = getFieldValue(deletedField.field(), entity);
            if (deletedValue == null) {
                // Set to undeleted value (0)
                int undeleted = Integer.parseInt(deletedField.notDeletedValue());
                setFieldValue(deletedField.field(), entity, undeleted);
            }
        }

        // Initialize version field to 0 if null
        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            Object versionValue = getFieldValue(versionField.field(), entity);
            if (versionValue == null) {
                // Set to initial version (0)
                setFieldValue(versionField.field(), entity, 0);
            }
        }

        // Add all non-ID fields
        for (TableFieldInfo fieldInfo : tableInfo.getNonIdFields()) {
            sql.VALUES(fieldInfo.column(), buildValuePlaceholder(fieldInfo));
        }

        return sql.toString();
    }

    /**
     * Generates batch INSERT SQL for multiple entities.
     *
     * <p>This method generates a single INSERT statement with multiple value sets:
     * INSERT INTO table (col1, col2) VALUES (?, ?), (?, ?), ...
     *
     * <p>ID generation behavior:
     * <ul>
     *   <li>AUTO: Database generates IDs, MyBatis backfills them into entities</li>
     *   <li>UUID: IDs are generated before INSERT if null</li>
     *   <li>SNOWFLAKE: IDs are generated before INSERT if null</li>
     * </ul>
     *
     * @param params parameter map containing the list of entities under key "list"
     * @param context the provider context
     * @return the batch INSERT SQL statement
     * @throws IllegalArgumentException if the list is null or empty
     */
    public String insertBatch(Map<String, Object> params, ProviderContext context) {
        // Step 1: Extract and validate parameters
        @SuppressWarnings("unchecked")
        List<Object> entities = (List<Object>) params.get("list");

        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("Batch insert list cannot be null or empty");
        }

        // Step 2: Get entity metadata
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);
        TableFieldInfo idField = tableInfo.idField();

        // Step 3: ID pre-generation for UUID/SNOWFLAKE
        if (idField != null && idField.idType() != IdType.AUTO) {
            for (Object entity : entities) {
                Object idValue = getFieldValue(idField.field(), entity);
                if (idValue == null) {
                    idValue = generateId(idField.idType(), idField.getPropertyType());
                    setFieldValue(idField.field(), entity, idValue);
                }
            }
        }

        // Step 4: Initialize soft delete field to 0
        TableFieldInfo deletedField = tableInfo.deletedField();
        if (deletedField != null) {
            for (Object entity : entities) {
                Object value = getFieldValue(deletedField.field(), entity);
                if (value == null) {
                    int undeleted = Integer.parseInt(deletedField.notDeletedValue());
                    setFieldValue(deletedField.field(), entity, undeleted);
                }
            }
        }

        // Step 5: Initialize version field to 0
        TableFieldInfo versionField = tableInfo.versionField();
        if (versionField != null) {
            for (Object entity : entities) {
                Object value = getFieldValue(versionField.field(), entity);
                if (value == null) {
                    setFieldValue(versionField.field(), entity, 0);
                }
            }
        }

        // Step 6: Collect fields to insert (non-@Ignore fields)
        List<TableFieldInfo> insertFields = new ArrayList<>();

        // Add ID field if not AUTO type
        if (idField != null && idField.idType() != IdType.AUTO) {
            insertFields.add(idField);
        }

        // Add all non-ID fields (getNonIdFields() already excludes @Ignore fields)
        insertFields.addAll(tableInfo.getNonIdFields());

        // Step 7: Build batch INSERT SQL
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableInfo.tableName()).append(" (");

        // Column names
        for (int i = 0; i < insertFields.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(insertFields.get(i).column());
        }
        sql.append(") VALUES ");

        // VALUES clauses - one per entity
        for (int entityIndex = 0; entityIndex < entities.size(); entityIndex++) {
            if (entityIndex > 0) sql.append(", ");
            sql.append("(");

            for (int fieldIndex = 0; fieldIndex < insertFields.size(); fieldIndex++) {
                if (fieldIndex > 0) sql.append(", ");
                TableFieldInfo field = insertFields.get(fieldIndex);

                // Use indexed placeholder: #{list[0].propertyName}
                sql.append("#{list[").append(entityIndex).append("].")
                   .append(field.getProperty()).append("}");
            }

            sql.append(")");
        }

        return sql.toString();
    }

    /**
     * Generates UPDATE BY ID SQL for all fields (including null fields).
     *
     * @param entity the entity to update
     * @return the UPDATE SQL statement
     */
    public String updateById(Object entity) {
        Class<?> entityClass = entity.getClass();
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        SQL sql = new SQL()
                .UPDATE(tableInfo.tableName());

        // Add SET clauses for all non-ID, non-version, non-deleted fields
        for (TableFieldInfo fieldInfo : tableInfo.getNonIdFields()) {
            if (fieldInfo.isVersion() || fieldInfo.isDeleted()) {
                continue;
            }
            sql.SET(fieldInfo.column() + " = #{" + fieldInfo.getProperty() + "}");
        }

        // Handle version field for optimistic locking
        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            Object currentVersion = getFieldValue(versionField.field(), entity);

            if (currentVersion == null) {
                throw new MapperException("Version field cannot be null for optimistic locking");
            }

            sql.SET(versionField.column() + " = " + versionField.column() + " + 1");
        }

        // WHERE clause
        TableFieldInfo idField = tableInfo.idField();
        sql.WHERE(idField.column() + " = #{" + idField.getProperty() + "}");

        // Add version check in WHERE clause for optimistic locking
        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            sql.WHERE(versionField.column() + " = #{" + versionField.getProperty() + "}");
        }

        return sql.toString();
    }

    /**
     * Generates UPDATE BY ID SQL for non-null fields only (selective update).
     *
     * @param params parameter map containing entity
     * @return the UPDATE SQL statement
     */
    public String updateSelectiveById(Map<String, Object> params) {
        Object entity = params.get("entity");
        Class<?> entityClass = entity.getClass();
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        SQL sql = new SQL()
                .UPDATE(tableInfo.tableName());

        // Add SET clauses for non-null fields only
        for (TableFieldInfo fieldInfo : tableInfo.getNonIdFields()) {
            if (fieldInfo.isVersion() || fieldInfo.isDeleted()) {
                continue;
            }

            Object value = getFieldValue(fieldInfo.field(), entity);
            if (value != null) {
                sql.SET(fieldInfo.column() + " = #{entity." + fieldInfo.getProperty() + "}");
            }
        }

        // Handle version field for optimistic locking
        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            Object currentVersion = getFieldValue(versionField.field(), entity);

            if (currentVersion == null) {
                throw new MapperException("Version field cannot be null for optimistic locking");
            }

            sql.SET(versionField.column() + " = " + versionField.column() + " + 1");
        }

        // WHERE clause
        TableFieldInfo idField = tableInfo.idField();
        sql.WHERE(idField.column() + " = #{entity." + idField.getProperty() + "}");

        // Add version check in WHERE clause for optimistic locking
        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            sql.WHERE(versionField.column() + " = #{entity." + versionField.getProperty() + "}");
        }

        return sql.toString();
    }

    /**
     * Generates SELECT BY ID SQL for an entity.
     *
     * @param id      the primary key value
     * @param context the provider context
     * @return the SELECT BY ID SQL statement
     */
    public String getById(Object id, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        // Add soft delete condition
        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }

        // Add ID condition
        sql.WHERE(tableInfo.idField().column() + " = #{id}");

        return sql.toString();
    }

    /**
     * Generates SELECT COUNT SQL with dynamic WHERE conditions based on entity fields.
     *
     * @param params  parameter map containing the entity under key MapperConsts.ENTITY_WHERE
     * @param context the provider context
     * @return the SELECT COUNT SQL statement
     */
    public String selectCount(Map<String, Object> params, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        // Extract entity from parameters
        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .SELECT("COUNT(*)")
                .FROM(tableInfo.tableName());

        // Add soft delete condition
        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }

        // Add conditions from non-null entity fields
        String[] entityWheres = buildEntityWheres(tableInfo, entity);
        if (entityWheres.length > 0) {
            sql.WHERE(entityWheres);
        }

        return sql.toString();
    }

    /**
     * Generates SELECT SQL for batch IDs (IN query).
     *
     * @param params  parameter map containing the IDs collection
     * @param context the provider context
     * @return the SELECT SQL statement with IN clause
     */
    public String selectBatchIds(Map<String, Object> params, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        @SuppressWarnings("unchecked")
        Collection<Serializable> ids = (Collection<Serializable>) params.get("ids");

        // Handle empty collection - return query that returns no results
        if (ids == null || ids.isEmpty()) {
            return new SQL()
                    .SELECT(buildSelectColumns(tableInfo))
                    .FROM(tableInfo.tableName())
                    .WHERE("1 = 0")
                    .toString();
        }

        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        // Add soft delete condition
        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }

        // Add IN condition
        sql.WHERE(buildInCondition(tableInfo.idField().column(), ids));

        return sql.toString();
    }

    /**
     * Generates SELECT ALL SQL for an entity.
     *
     * @param context the provider context
     * @return the SELECT ALL SQL statement
     */
    public String selectAll(ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        // Add soft delete condition
        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }

        // Add LIMIT (SQL class doesn't support LIMIT, need to append manually)
        return sql.toString() + " LIMIT " + DEFAULT_SELECT_ALL_LIMIT;
    }

    /**
     * Generates SELECT SQL with dynamic WHERE conditions based on entity fields.
     *
     * @param params  parameter map containing the entity under key MapperConsts.ENTITY_WHERE
     * @param context the provider context
     * @return the SELECT SQL statement
     */
    public String selectList(Map<String, Object> params, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        // Extract entity from parameters
        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        // Add soft delete condition
        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }

        // Add conditions from non-null entity fields
        String[] entityWheres = buildEntityWheres(tableInfo, entity);
        if (entityWheres.length > 0) {
            sql.WHERE(entityWheres);
        }

        return sql.toString();
    }

    /**
     * Generates SELECT SQL for a single result with LIMIT 1.
     *
     * @param params  parameter map containing the entity under key MapperConsts.ENTITY_WHERE
     * @param context the provider context
     * @return the SELECT SQL statement with LIMIT 1
     */
    public String selectOne(Map<String, Object> params, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        // Extract entity from parameters
        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        // Add soft delete condition
        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }

        // Add conditions from non-null entity fields
        String[] entityWheres = buildEntityWheres(tableInfo, entity);
        if (entityWheres.length > 0) {
            sql.WHERE(entityWheres);
        }

        // Add LIMIT 1
        return sql.toString() + " LIMIT 1";
    }

    /**
     * Generates logical delete SQL by ID (UPDATE deleted field).
     *
     * @param id      the primary key value
     * @param context the provider context
     * @return the UPDATE SQL statement
     */
    public String deleteById(Object id, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        // Check if table supports logical delete
        if (!tableInfo.hasLogicDelete()) {
            throw new MapperException("Entity " + entityClass.getSimpleName() +
                    " does not support logical delete. Use removeById instead.");
        }

        TableFieldInfo deletedField = tableInfo.deletedField();

        SQL sql = new SQL()
                .UPDATE(tableInfo.tableName())
                .SET(deletedField.column() + " = " + deletedField.deletedValue())
                .WHERE(tableInfo.idField().column() + " = #{id}")
                .WHERE(deletedField.column() + " = " + deletedField.notDeletedValue());

        return sql.toString();
    }

    /**
     * Generates physical delete SQL by ID (DELETE FROM).
     *
     * @param id      the primary key value
     * @param context the provider context
     * @return the DELETE SQL statement
     */
    public String removeById(Object id, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        SQL sql = new SQL()
                .DELETE_FROM(tableInfo.tableName())
                .WHERE(tableInfo.idField().column() + " = #{id}");

        return sql.toString();
    }

    /**
     * Generates logical delete SQL by entity conditions (UPDATE deleted field).
     *
     * @param params  parameter map containing the entity
     * @param context the provider context
     * @return the UPDATE SQL statement
     */
    public String delete(Map<String, Object> params, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        // Check if table supports logical delete
        if (!tableInfo.hasLogicDelete()) {
            throw new MapperException("Entity " + entityClass.getSimpleName() +
                    " does not support logical delete. Use remove instead.");
        }

        Object entity = params.get(MapperConsts.ENTITY_WHERE);
        TableFieldInfo deletedField = tableInfo.deletedField();

        SQL sql = new SQL()
                .UPDATE(tableInfo.tableName())
                .SET(deletedField.column() + " = " + deletedField.deletedValue())
                .WHERE(deletedField.column() + " = " + deletedField.notDeletedValue());

        // Add conditions from non-null entity fields
        String[] entityWheres = buildEntityWheres(tableInfo, entity);
        if (entityWheres.length > 0) {
            sql.WHERE(entityWheres);
        }

        return sql.toString();
    }

    /**
     * Generates physical delete SQL by entity conditions (DELETE FROM).
     *
     * @param params  parameter map containing the entity
     * @param context the provider context
     * @return the DELETE SQL statement
     */
    public String remove(Map<String, Object> params, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .DELETE_FROM(tableInfo.tableName());

        // Add conditions from non-null entity fields
        String[] entityWheres = buildEntityWheres(tableInfo, entity);
        if (entityWheres.length > 0) {
            sql.WHERE(entityWheres);
        }

        return sql.toString();
    }

    /**
     * Generates SELECT SQL for pagination with optional entity conditions.
     *
     * @param params  parameter map containing page and optional entity
     * @param context the provider context
     * @return the SELECT SQL statement
     */
    public String selectPage(Map<String, Object> params, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        // Add soft delete condition
        String softDeleteWhere = buildSoftDeleteWhere(tableInfo);
        if (softDeleteWhere != null) {
            sql.WHERE(softDeleteWhere);
        }

        // Add conditions from non-null entity fields if provided
        if (entity != null) {
            String[] entityWheres = buildEntityWheres(tableInfo, entity);
            if (entityWheres.length > 0) {
                sql.WHERE(entityWheres);
            }
        }

        return sql.toString();
    }

}
