package com.ngcin.ems.mapper.ref;

import com.ngcin.ems.mapper.annotations.Column;
import com.ngcin.ems.mapper.annotations.Deleted;
import com.ngcin.ems.mapper.annotations.Id;
import com.ngcin.ems.mapper.annotations.Table;
import com.ngcin.ems.mapper.annotations.Unique;
import com.ngcin.ems.mapper.annotations.Version;
import com.ngcin.ems.mapper.annotations.Ignore;

import com.ngcin.ems.mapper.core.IdType;
import org.apache.ibatis.type.JdbcType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves entity class metadata including table name, fields, and annotations.
 *
 * <p>This class parses entity classes and their field annotations to build
 * {@link TableInfo} and {@link TableFieldInfo} objects that describe the
 * mapping between Java entities and database tables.
 *
 * <p>Results are cached in a thread-safe ConcurrentHashMap for performance.
 */
public class EntityClassResolver {

    /** Cache for resolved TableInfo objects. */
    private static final Map<Class<?>, TableInfo> TABLE_INFO_CACHE = new ConcurrentHashMap<>();

    /**
     * Resolves entity class metadata, using cache if already resolved.
     *
     * @param entityClass the entity class to resolve
     * @return TableInfo containing metadata for the entity
     * @throws IllegalArgumentException if entity lacks @Table annotation
     */
    public static TableInfo resolve(Class<?> entityClass) {
        return TABLE_INFO_CACHE.computeIfAbsent(entityClass, EntityClassResolver::doResolve);
    }

    /**
     * Performs actual resolution of entity class metadata.
     *
     * <p>Process:
     * <ol>
     *   <li>Get table name from @Table annotation</li>
     *   <li>Iterate all declared fields</li>
     *   <li>Parse annotations (@Id, @Column, @Version, @Deleted, @Unique)</li>
     *   <li>Build TableFieldInfo for each field</li>
     *   <li>Return TableInfo with all metadata</li>
     * </ol>
     *
     * @param entityClass the entity class to resolve
     * @return TableInfo containing metadata
     */
    private static TableInfo doResolve(Class<?> entityClass) {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        if (tableAnnotation == null) {
            throw new IllegalArgumentException("Entity class " + entityClass.getName() + " must have @Table annotation");
        }

        String tableName = tableAnnotation.value();

        // Collect fields from entire class hierarchy
        List<Field> allDeclaredFields = new ArrayList<>();
        Class<?> currentClass = entityClass;
        while (currentClass != null && currentClass != Object.class) {
            Field[] declaredFields = currentClass.getDeclaredFields();
            allDeclaredFields.addAll(Arrays.asList(declaredFields));
            currentClass = currentClass.getSuperclass();
        }

        TableFieldInfo idField = null;
        TableFieldInfo versionField = null;
        TableFieldInfo deletedField = null;
        List<TableFieldInfo> allFields = new ArrayList<>();
        List<TableFieldInfo> uniqueFields = new ArrayList<>();
        Set<String> processedFieldNames = new HashSet<>();

        for (Field field : allDeclaredFields) {
            // Skip duplicate field names (child class fields take precedence)
            if (processedFieldNames.contains(field.getName())) {
                continue;
            }
            processedFieldNames.add(field.getName());

            field.setAccessible(true);

            if (field.isAnnotationPresent(Ignore.class)) {
                continue;
            }

            String column = resolveColumnName(field);
            Integer jdbcType = resolveJdbcType(field);

            boolean isId = field.isAnnotationPresent(Id.class);
            IdType idType = isId ? field.getAnnotation(Id.class).type() : null;

            boolean isVersion = field.isAnnotationPresent(Version.class);
            boolean isDeleted = field.isAnnotationPresent(Deleted.class);
            String deletedValue = null;
            String notDeletedValue = null;

            if (isDeleted) {
                Deleted deleted = field.getAnnotation(Deleted.class);
                deletedValue = deleted.deleted();
                notDeletedValue = deleted.undeleted();
            }

            boolean isUnique = field.isAnnotationPresent(Unique.class);

            TableFieldInfo fieldInfo = new TableFieldInfo(
                    field,
                    column,
                    jdbcType,
                    isId,
                    idType,
                    isVersion,
                    isDeleted,
                    deletedValue,
                    notDeletedValue,
                    isUnique
            );

            if (isId) {
                idField = fieldInfo;
            } else if (isVersion) {
                versionField = fieldInfo;
            } else if (isDeleted) {
                deletedField = fieldInfo;
            }

            if (isUnique) {
                uniqueFields.add(fieldInfo);
            }

            allFields.add(fieldInfo);
        }

        return new TableInfo(
                entityClass,
                tableName,
                idField,
                allFields,
                versionField,
                deletedField,
                uniqueFields
        );
    }

    /**
     * Resolves the database column name for a field.
     *
     * <p>Priority:
     * <ol>
     *   <li>@Column(name) value if specified</li>
     *   <li>Field name converted to snake_case</li>
     * </ol>
     *
     * @param field the field to resolve
     * @return the database column name
     */
    private static String resolveColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isEmpty()) {
            return column.name();
        }
        return camelToSnake(field.getName());
    }

    private static Integer resolveJdbcType(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && column.jdbcType() != JdbcType.UNDEFINED) {
            return column.jdbcType().TYPE_CODE;
        }
        return null;
    }

    /**
     * Converts camelCase field name to snake_case column name.
     *
     * <p>Handles edge cases correctly:
     * <ul>
     *   <li>userName → user_name</li>
     *   <li>ID → id</li>
     *   <li>userID → user_id</li>
     *   <li>XMLHttpRequest → xml_http_request</li>
     *   <li>getHTTPResponseCode → get_http_response_code</li>
     * </ul>
     *
     * @param camelCase the camelCase string
     * @return snake_case equivalent
     */
    public static String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            char nextChar = (i < camelCase.length() - 1) ? camelCase.charAt(i + 1) : '\0';

            if (Character.isUpperCase(c)) {
                // Current is uppercase
                boolean prevIsLower = (i > 0) && Character.isLowerCase(camelCase.charAt(i - 1));
                boolean nextIsLower = Character.isLowerCase(nextChar);

                // Add underscore before if: prev is lower, or next is lower and not first char
                if (prevIsLower || (nextIsLower && i > 0)) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static void clearCache() {
        TABLE_INFO_CACHE.clear();
    }
}
