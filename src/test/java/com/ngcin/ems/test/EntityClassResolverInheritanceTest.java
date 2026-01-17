package com.ngcin.ems.test;

import com.ngcin.ems.mapper.core.IdType;
import com.ngcin.ems.mapper.ref.EntityClassResolver;
import com.ngcin.ems.mapper.ref.TableFieldInfo;
import com.ngcin.ems.mapper.ref.TableInfo;
import com.ngcin.ems.test.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EntityClassResolver inheritance support.
 * Verifies that fields from parent classes are correctly resolved.
 */
class EntityClassResolverInheritanceTest {

    @AfterEach
    void clearCache() {
        // Clear cache after each test to ensure clean state
        EntityClassResolver.clearCache();
    }

    @Test
    void testResolve_EntityWithInheritedId_Success() {
        // Act
        TableInfo tableInfo = EntityClassResolver.resolve(InheritedUser.class);

        // Assert
        assertNotNull(tableInfo, "TableInfo should not be null");
        assertNotNull(tableInfo.idField(), "ID field should be found from parent class");
        assertEquals("id", tableInfo.idField().field().getName(), "ID field name should be 'id'");
        assertTrue(tableInfo.idField().isId(), "Field should be marked as ID");
        assertEquals(IdType.SNOWFLAKE, tableInfo.idField().idType(), "ID type should be SNOWFLAKE");
    }

    @Test
    void testResolve_EntityWithInheritedId_AllFieldsIncluded() {
        // Act
        TableInfo tableInfo = EntityClassResolver.resolve(InheritedUser.class);

        // Assert
        assertNotNull(tableInfo, "TableInfo should not be null");
        assertEquals(3, tableInfo.fields().size(), "Should have 3 fields: id, username, email");

        // Get field names
        List<String> fieldNames = tableInfo.fields().stream()
                .map(f -> f.field().getName())
                .collect(Collectors.toList());

        assertTrue(fieldNames.contains("id"), "Should contain 'id' field from parent");
        assertTrue(fieldNames.contains("username"), "Should contain 'username' field");
        assertTrue(fieldNames.contains("email"), "Should contain 'email' field");
    }

    @Test
    void testResolve_MultiLevelInheritance_Success() {
        // Act
        TableInfo tableInfo = EntityClassResolver.resolve(MultiLevelEntity.class);

        // Assert
        assertNotNull(tableInfo, "TableInfo should not be null");
        assertNotNull(tableInfo.idField(), "ID field should be found from BaseEntity");
        assertEquals("id", tableInfo.idField().field().getName(), "ID field should be 'id'");

        // Verify all fields from all levels are collected
        List<String> fieldNames = tableInfo.fields().stream()
                .map(f -> f.field().getName())
                .collect(Collectors.toList());

        assertTrue(fieldNames.contains("id"), "Should contain 'id' from BaseEntity");
        assertTrue(fieldNames.contains("createdAt"), "Should contain 'createdAt' from AuditableEntity");
        assertTrue(fieldNames.contains("updatedAt"), "Should contain 'updatedAt' from AuditableEntity");
        assertTrue(fieldNames.contains("name"), "Should contain 'name' from MultiLevelEntity");
        assertEquals(4, tableInfo.fields().size(), "Should have 4 fields from all inheritance levels");
    }

    @Test
    void testResolve_NoInheritance_StillWorks() {
        // Act - Test with existing entity that doesn't use inheritance
        TableInfo tableInfo = EntityClassResolver.resolve(Product.class);

        // Assert
        assertNotNull(tableInfo, "TableInfo should not be null");
        assertNotNull(tableInfo.idField(), "ID field should be found");
        assertEquals("productId", tableInfo.idField().field().getName(), "ID field should be 'productId'");
        assertEquals("t_product", tableInfo.tableName(), "Table name should be 't_product'");

        // Verify Product fields are present
        List<String> fieldNames = tableInfo.fields().stream()
                .map(f -> f.field().getName())
                .collect(Collectors.toList());

        assertTrue(fieldNames.contains("productId"), "Should contain 'productId' field");
        assertTrue(fieldNames.contains("productName"), "Should contain 'productName' field");
        assertTrue(fieldNames.contains("price"), "Should contain 'price' field");
    }

    @Test
    void testResolve_CacheWorksWithInheritance() {
        // Arrange - Clear cache first
        EntityClassResolver.clearCache();

        // Act - Resolve same entity twice
        TableInfo info1 = EntityClassResolver.resolve(InheritedUser.class);
        TableInfo info2 = EntityClassResolver.resolve(InheritedUser.class);

        // Assert - Should return same instance from cache
        assertSame(info1, info2, "Should return same TableInfo instance from cache");
        assertNotNull(info1.idField(), "Cached TableInfo should have ID field");
    }
}
