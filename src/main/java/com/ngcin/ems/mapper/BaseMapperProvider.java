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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        requireNonNull(entity, "entity");

        Class<?> entityClass = entity.getClass();
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        initializeEntityForInsert(entity, tableInfo);

        SQL sql = new SQL().INSERT_INTO(tableInfo.tableName());
        addIdFieldIfNeeded(sql, tableInfo);

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
        requireNonNull(entity, "entity");

        Class<?> entityClass = entity.getClass();
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        initializeEntityForInsert(entity, tableInfo);

        SQL sql = new SQL().INSERT_INTO(tableInfo.tableName());
        addIdFieldIfNeeded(sql, tableInfo);

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
        @SuppressWarnings("unchecked")
        List<Object> entities = (List<Object>) params.get("list");

        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("Batch insert list cannot be null or empty");
        }

        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        for (Object entity : entities) {
            initializeEntityForInsert(entity, tableInfo);
        }

        List<TableFieldInfo> insertFields = new ArrayList<>();
        if (tableInfo.idField() != null && tableInfo.idField().idType() != IdType.AUTO) {
            insertFields.add(tableInfo.idField());
        }
        insertFields.addAll(tableInfo.getNonIdFields());

        String columns = insertFields.stream()
                .map(TableFieldInfo::column)
                .collect(Collectors.joining(", "));

        String values = IntStream.range(0, entities.size())
                .mapToObj(i -> "(" + insertFields.stream()
                        .map(f -> "#{list[" + i + "]." + f.getProperty() + "}")
                        .collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining(", "));

        return "INSERT INTO " + tableInfo.tableName() + " (" + columns + ") VALUES " + values;
    }

    /**
     * Generates UPDATE BY ID SQL for all fields (including null fields).
     *
     * @param entity the entity to update
     * @return the UPDATE SQL statement
     */
    public String updateById(Object entity) {
        requireNonNull(entity, "entity");

        Class<?> entityClass = entity.getClass();
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);
        requireIdField(tableInfo);

        SQL sql = new SQL().UPDATE(tableInfo.tableName());

        for (TableFieldInfo fieldInfo : tableInfo.getNonIdFields()) {
            if (fieldInfo.isVersion() || fieldInfo.isDeleted()) {
                continue;
            }
            sql.SET(buildSetClause(fieldInfo, null));
        }

        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            Object currentVersion = getFieldValue(versionField.field(), entity);
            if (currentVersion == null) {
                throw new MapperException("Version field cannot be null for optimistic locking");
            }
            applyVersionIncrement(sql, versionField);
        }

        sql.WHERE(buildWhereClause(tableInfo.idField(), null));

        if (tableInfo.hasVersion()) {
            sql.WHERE(buildWhereClause(tableInfo.versionField(), null));
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

        SQL sql = new SQL().UPDATE(tableInfo.tableName());

        for (TableFieldInfo fieldInfo : tableInfo.getNonIdFields()) {
            if (fieldInfo.isVersion() || fieldInfo.isDeleted()) {
                continue;
            }

            Object value = getFieldValue(fieldInfo.field(), entity);
            if (value != null) {
                sql.SET(buildSetClause(fieldInfo, "entity"));
            }
        }

        if (tableInfo.hasVersion()) {
            TableFieldInfo versionField = tableInfo.versionField();
            Object currentVersion = getFieldValue(versionField.field(), entity);
            if (currentVersion == null) {
                throw new MapperException("Version field cannot be null for optimistic locking");
            }
            applyVersionIncrement(sql, versionField);
        }

        sql.WHERE(buildWhereClause(tableInfo.idField(), "entity"));

        if (tableInfo.hasVersion()) {
            sql.WHERE(buildWhereClause(tableInfo.versionField(), "entity"));
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
        requireNonNull(id, "id");

        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);
        requireIdField(tableInfo);

        SQL sql = buildSelectBase(tableInfo);
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

        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .SELECT("COUNT(*)")
                .FROM(tableInfo.tableName());

        addEntityConditions(sql, tableInfo, entity);

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

        if (ids == null || ids.isEmpty()) {
            return new SQL()
                    .SELECT(buildSelectColumns(tableInfo))
                    .FROM(tableInfo.tableName())
                    .WHERE("1 = 0")
                    .toString();
        }

        SQL sql = buildSelectBase(tableInfo);
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

        SQL sql = buildSelectBase(tableInfo);
        return appendLimit(sql.toString(), DEFAULT_SELECT_ALL_LIMIT);
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

        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        addEntityConditions(sql, tableInfo, entity);

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

        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .SELECT(buildSelectColumns(tableInfo))
                .FROM(tableInfo.tableName());

        addEntityConditions(sql, tableInfo, entity);

        return appendLimit(sql.toString(), 1);
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
                    " does not support logical delete. Use hardDeleteById instead.");
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
    public String hardDeleteById(Object id, ProviderContext context) {
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

        if (!tableInfo.hasLogicDelete()) {
            throw new MapperException("Entity " + entityClass.getSimpleName() +
                    " does not support logical delete. Use hardDelete instead.");
        }

        Object entity = params.get(MapperConsts.ENTITY_WHERE);
        TableFieldInfo deletedField = tableInfo.deletedField();

        SQL sql = new SQL()
                .UPDATE(tableInfo.tableName())
                .SET(deletedField.column() + " = " + deletedField.deletedValue())
                .WHERE(deletedField.column() + " = " + deletedField.notDeletedValue());

        addEntityConditions(sql, tableInfo, entity);

        return sql.toString();
    }

    /**
     * Generates physical delete SQL by entity conditions (DELETE FROM).
     *
     * @param params  parameter map containing the entity
     * @param context the provider context
     * @return the DELETE SQL statement
     */
    public String hardDelete(Map<String, Object> params, ProviderContext context) {
        Class<?> entityClass = getType(context);
        TableInfo tableInfo = EntityClassResolver.resolve(entityClass);

        Object entity = params.get(MapperConsts.ENTITY_WHERE);

        SQL sql = new SQL()
                .DELETE_FROM(tableInfo.tableName());

        addEntityConditions(sql, tableInfo, entity);

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

        addEntityConditions(sql, tableInfo, entity);

        return sql.toString();
    }

}
