package com.ngcin.ems.mapper.ref;

import com.ngcin.ems.mapper.core.IdType;

import java.lang.reflect.Field;

/**
 * Metadata for a single field in an entity class.
 *
 * @param field the Java Field object
 * @param column the database column name
 * @param jdbcType JDBC type code (from @Column.jdbcType)
 * @param isId true if this field is marked with @Id
 * @param idType ID generation type (AUTO, INPUT, UUID, SNOWFLAKE)
 * @param isVersion true if marked with @Version
 * @param isDeleted true if marked with @Deleted (soft delete)
 * @param deletedValue value representing deleted state
 * @param notDeletedValue value representing not deleted state
 * @param isUnique true if marked with @Unique
 */
public record TableFieldInfo(
    Field field,
    String column,
    Integer jdbcType,
    boolean isId,
    IdType idType,
    boolean isVersion,
    boolean isDeleted,
    String deletedValue,
    String notDeletedValue,
    boolean isUnique
) {
    /** The Java property name (field name). */
    public String getProperty() {
        return field.getName();
    }

    /** The Java property type (field type). */
    public Class<?> getPropertyType() {
        return field.getType();
    }
}
