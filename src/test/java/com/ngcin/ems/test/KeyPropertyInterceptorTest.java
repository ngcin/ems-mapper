package com.ngcin.ems.test;

import com.ngcin.ems.test.mapper.ProductMapper;
import com.ngcin.ems.test.mapper.UserMapper;
import com.ngcin.ems.test.entity.Product;
import com.ngcin.ems.test.entity.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KeyPropertyInterceptor to verify that auto-generated keys
 * are correctly mapped to entities with custom ID field names.
 */
class KeyPropertyInterceptorTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private UserMapper userMapper;
    private ProductMapper productMapper;

    @BeforeAll
    static void setUp() {
        // Create mybatis-config.xml content with KeyPropertyInterceptor registered
        String mybatisConfig = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE configuration
                        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                        "https://mybatis.org/dtd/mybatis-3-config.dtd">
                <configuration>
                    <settings>
                        <setting name="logImpl" value="SLF4J"/>
                        <setting name="mapUnderscoreToCamelCase" value="true"/>
                    </settings>
                    <plugins>
                        <plugin interceptor="com.ngcin.ems.mapper.core.KeyPropertyInterceptor"/>
                    </plugins>
                    <environments default="development">
                        <environment id="development">
                            <transactionManager type="JDBC"/>
                            <dataSource type="POOLED">
                                <property name="driver" value="org.h2.Driver"/>
                                <property name="url" value="jdbc:h2:mem:testdb_keyproperty;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.UserMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.ProductMapper"/>
                    </mappers>
                </configuration>
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes());
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        // Initialize database schema
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();

            // Create t_user table with "id" column (standard field name)
            stmt.execute("CREATE TABLE t_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL, " +
                    "email VARCHAR(100), " +
                    "age INT, " +
                    "create_time TIMESTAMP)");

            // Create t_product table with "product_id" column (custom field name)
            stmt.execute("CREATE TABLE t_product (" +
                    "product_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "product_name VARCHAR(100) NOT NULL, " +
                    "price DECIMAL(10,2))");

            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    @BeforeEach
    void openSession() throws SQLException {
        session = sqlSessionFactory.openSession();
        userMapper = session.getMapper(UserMapper.class);
        productMapper = session.getMapper(ProductMapper.class);

        // Clean up tables before each test
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_product");
            stmt.execute("DELETE FROM t_user");
        }
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Test backward compatibility: entity with "id" field should work as before.
     */
    @Test
    void testInsert_WithStandardIdField() {
        // Arrange
        User user = new User("testuser", "test@example.com", 25);

        // Act
        int result = userMapper.insert(user);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertNotNull(user.getId(), "ID should be generated and set back to entity");
        assertTrue(user.getId() > 0, "ID should be positive");

        // Verify in database
        Long dbId = getDbId("t_user", "id", "username", "testuser");
        assertEquals(user.getId(), dbId, "Generated ID should match database ID");
    }

    /**
     * Test custom ID field name: Product entity uses "productId" instead of "id".
     */
    @Test
    void testInsert_WithCustomIdField_ProductId() {
        // Arrange
        Product product = new Product("Laptop", 999.99);

        // Act
        int result = productMapper.insert(product);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertNotNull(product.getProductId(), "productId should be generated and set back to entity");
        assertTrue(product.getProductId() > 0, "productId should be positive");

        // Verify in database
        Long dbId = getDbId("t_product", "product_id", "product_name", "Laptop");
        assertEquals(product.getProductId(), dbId, "Generated productId should match database product_id");
    }

    /**
     * Test multiple inserts with custom ID field name.
     */
    @Test
    void testInsert_MultipleWithCustomIdField() {
        // Arrange
        Product p1 = new Product("Mouse", 29.99);
        Product p2 = new Product("Keyboard", 79.99);
        Product p3 = new Product("Monitor", 299.99);

        // Act
        productMapper.insert(p1);
        session.commit();
        productMapper.insert(p2);
        session.commit();
        productMapper.insert(p3);
        session.commit();

        // Assert
        assertNotNull(p1.getProductId(), "p1 productId should be generated");
        assertNotNull(p2.getProductId(), "p2 productId should be generated");
        assertNotNull(p3.getProductId(), "p3 productId should be generated");

        // Verify IDs are unique
        assertNotEquals(p1.getProductId(), p2.getProductId(), "IDs should be unique");
        assertNotEquals(p2.getProductId(), p3.getProductId(), "IDs should be unique");
        assertNotEquals(p1.getProductId(), p3.getProductId(), "IDs should be unique");

        // Verify records in database
        int count = getCount("t_product");
        assertEquals(3, count, "Should have 3 records in database");
    }

    /**
     * Test that standard and custom ID fields can coexist.
     */
    @Test
    void testInsert_MixedStandardAndCustomIdFields() {
        // Arrange
        User user = new User("user1", "user1@test.com", 30);
        Product product = new Product("Desk", 499.99);

        // Act
        userMapper.insert(user);
        session.commit();
        productMapper.insert(product);
        session.commit();

        // Assert
        assertNotNull(user.getId(), "User ID should be generated");
        assertNotNull(product.getProductId(), "Product productId should be generated");

        // Verify both records in database
        assertEquals(1, getCount("t_user"), "Should have 1 user record");
        assertEquals(1, getCount("t_product"), "Should have 1 product record");
    }

    // Helper methods

    private Long getDbId(String tableName, String idColumn, String whereColumn, String whereValue) {
        try (Connection conn = sqlSessionFactory.openSession().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT " + idColumn + " FROM " + tableName + " WHERE " + whereColumn + " = '" + whereValue + "'")) {

            if (rs.next()) {
                return rs.getLong(idColumn);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query ID from database", e);
        }
    }

    private int getCount(String tableName) {
        try (Connection conn = sqlSessionFactory.openSession().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count records", e);
        }
    }
}
