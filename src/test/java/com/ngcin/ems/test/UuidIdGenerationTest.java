package com.ngcin.ems.test;

import com.ngcin.ems.test.mapper.OrderMapper;
import com.ngcin.ems.test.entity.Order;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UUID ID generation strategy.
 */
class UuidIdGenerationTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private OrderMapper orderMapper;

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
                                <property name="url" value="jdbc:h2:mem:testdb3;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.OrderMapper"/>
                    </mappers>
                </configuration>
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes());
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        // Initialize database schema
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();

            // Create t_order table with UUID String type
            stmt.execute("CREATE TABLE t_order (" +
                    "order_id VARCHAR(36) PRIMARY KEY, " +
                    "order_no VARCHAR(50) NOT NULL, " +
                    "amount DECIMAL(10,2))");

            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    @BeforeEach
    void openSession() throws SQLException {
        session = sqlSessionFactory.openSession();
        orderMapper = session.getMapper(OrderMapper.class);

        // Clean up tables before each test
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_order");
        }
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Test UUID String type ID generation.
     */
    @Test
    void testInsert_WithUuidStringType() {
        // Arrange
        Order order = new Order("ORD001", new java.math.BigDecimal("99.99"));

        // Act
        int result = orderMapper.insert(order);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertNotNull(order.getOrderId(), "UUID should be generated");
        assertFalse(order.getOrderId().isEmpty(), "UUID should not be empty");

        // Verify UUID format (8-4-4-4-12 pattern)
        assertTrue(isValidUuid(order.getOrderId()), "Should be valid UUID format");

        // Verify in database
        String dbId = getDbStringId("t_order", "order_id", "order_no", "ORD001");
        assertEquals(order.getOrderId(), dbId, "Generated ID should match database ID");
    }

    /**
     * Test multiple UUID entities - verify uniqueness.
     */
    @Test
    void testInsert_MultipleUuidEntities() {
        // Arrange
        Order o1 = new Order("ORD001", new java.math.BigDecimal("10.00"));
        Order o2 = new Order("ORD002", new java.math.BigDecimal("20.00"));
        Order o3 = new Order("ORD003", new java.math.BigDecimal("30.00"));

        // Act
        orderMapper.insert(o1);
        session.commit();
        orderMapper.insert(o2);
        session.commit();
        orderMapper.insert(o3);
        session.commit();

        // Assert
        assertNotNull(o1.getOrderId(), "o1 UUID should be generated");
        assertNotNull(o2.getOrderId(), "o2 UUID should be generated");
        assertNotNull(o3.getOrderId(), "o3 UUID should be generated");

        // Verify uniqueness
        Set<String> ids = new HashSet<>();
        ids.add(o1.getOrderId());
        ids.add(o2.getOrderId());
        ids.add(o3.getOrderId());
        assertEquals(3, ids.size(), "All UUIDs should be unique");

        // Verify all valid UUID format
        assertTrue(isValidUuid(o1.getOrderId()), "o1 should have valid UUID format");
        assertTrue(isValidUuid(o2.getOrderId()), "o2 should have valid UUID format");
        assertTrue(isValidUuid(o3.getOrderId()), "o3 should have valid UUID format");
    }

    /**
     * Test that pre-set UUID is not regenerated.
     */
    @Test
    void testInsert_WithPreSetUuid() {
        // Arrange
        String preSetUuid = UUID.randomUUID().toString();
        Order order = new Order("ORD001", new java.math.BigDecimal("99.99"));
        order.setOrderId(preSetUuid);

        // Act
        int result = orderMapper.insert(order);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertEquals(preSetUuid, order.getOrderId(), "Pre-set UUID should not be changed");

        // Verify in database
        String dbId = getDbStringId("t_order", "order_id", "order_no", "ORD001");
        assertEquals(preSetUuid, dbId, "Pre-set UUID should match database ID");
    }

    /**
     * Test UUID format validation.
     */
    @Test
    void testInsert_UuidFormatValidation() {
        // Arrange
        Order order = new Order("ORD001", new java.math.BigDecimal("99.99"));

        // Act
        orderMapper.insert(order);
        session.commit();

        // Assert - verify UUID format: 8-4-4-4-12 hex digits
        String uuid = order.getOrderId();
        assertNotNull(uuid, "UUID should not be null");
        assertTrue(isValidUuid(uuid), "Should match UUID pattern: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");

        // Verify each part
        String[] parts = uuid.split("-");
        assertEquals(5, parts.length, "UUID should have 5 parts separated by hyphens");
        assertEquals(8, parts[0].length(), "First part should be 8 characters");
        assertEquals(4, parts[1].length(), "Second part should be 4 characters");
        assertEquals(4, parts[2].length(), "Third part should be 4 characters");
        assertEquals(4, parts[3].length(), "Fourth part should be 4 characters");
        assertEquals(12, parts[4].length(), "Fifth part should be 12 characters");

        // Verify all hex characters
        for (String part : parts) {
            assertTrue(part.matches("[0-9a-fA-F-]+"), "All parts should be hex characters");
        }
    }

    // Helper methods

    private boolean isValidUuid(String uuid) {
        return uuid != null && uuid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    private String getDbStringId(String tableName, String idColumn, String whereColumn, String whereValue) {
        try (Connection conn = sqlSessionFactory.openSession().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT " + idColumn + " FROM " + tableName + " WHERE " + whereColumn + " = '" + whereValue + "'")) {

            if (rs.next()) {
                return rs.getString(idColumn);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query ID from database", e);
        }
    }
}
