package com.ngcin.ems.mapper.ref;

import java.util.List;

/**
 * Metadata for an entity class and its mapping to a database table.
 *
 * @param entityClass the Java entity class
 * @param tableName the database table name (from @Table.value)
 * @param idField metadata for the primary key field
 * @param fields metadata for all non-ignored fields
 * @param versionField metadata for @Version field (null if not present)
 * @param deletedField metadata for @Deleted field (null if not present)
 * @param uniqueFields metadata for @Unique fields
 */
public record TableInfo(
    Class<?> entityClass,
    String tableName,
    TableFieldInfo idField,
    List<TableFieldInfo> fields,
    TableFieldInfo versionField,
    TableFieldInfo deletedField,
    List<TableFieldInfo> uniqueFields
) {
    /**
     * Checks if this table has a version field.
     *
     * @return true if @Version field exists
     */
    public boolean hasVersion() {
        return versionField != null;
    }

    /**
     * Checks if this table uses soft delete.
     *
     * @return true if @Deleted field exists
     */
    public boolean hasLogicDelete() {
        return deletedField != null;
    }

    /**
     * Checks if this table has any unique constraints.
     *
     * @return true if any @Unique fields exist
     */
    public boolean hasUniqueFields() {
        return uniqueFields != null && !uniqueFields.isEmpty();
    }

    /**
     * Returns all non-primary-key fields.
     *
     * @return list of fields excluding the id field
     */
    public List<TableFieldInfo> getNonIdFields() {
        return fields.stream().filter(f -> !f.isId()).toList();
    }
}
