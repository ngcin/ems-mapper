package com.ngcin.ems.test;

import com.ngcin.ems.test.entity.*;
import com.ngcin.ems.test.mapper.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for BaseMapper.insertBatch() method.
 *
 * Tests cover:
 * - AUTO ID type (User entity)
 * - UUID ID type (Order entity)
 * - SNOWFLAKE ID type (Transaction entity)
 * - Soft delete field initialization (Article entity)
 * - Version field initialization (ProductV2 entity)
 * - Edge cases (empty list, single entity, large batch)
 * - ID preset scenarios
 */
class BaseMapperInsertBatchTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private UserMapper userMapper;
    private OrderMapper orderMapper;
    private TransactionMapper transactionMapper;
    private ArticleMapper articleMapper;
    private ProductV2Mapper productV2Mapper;

    @BeforeAll
    static void setUp() {
        // Create mybatis-config.xml content with KeyPropertyInterceptor
        String mybatisConfig = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE configuration
                        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                        "https://mybatis.org/dtd/mybatis-3-config.dtd">
                <configuration>
                    <settings>
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
                                <property name="url" value="jdbc:h2:mem:testdb_insert_batch;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.UserMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.OrderMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.TransactionMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.ArticleMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.ProductV2Mapper"/>
                    </mappers>
                </configuration>
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes());
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        // Initialize database schema
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();

            // Create t_user table
            stmt.execute("CREATE TABLE t_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL, " +
                    "email VARCHAR(100), " +
                    "age INT, " +
                    "create_time TIMESTAMP)");

            // Create t_order table
            stmt.execute("CREATE TABLE t_order (" +
                    "order_id VARCHAR(36) PRIMARY KEY, " +
                    "order_no VARCHAR(50), " +
                    "amount DECIMAL(10,2))");

            // Create t_transaction table
            stmt.execute("CREATE TABLE t_transaction (" +
                    "transaction_id VARCHAR(36) PRIMARY KEY, " +
                    "txn_no VARCHAR(50), " +
                    "status VARCHAR(20))");

            // Create t_article table (with soft delete)
            stmt.execute("CREATE TABLE t_article (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(200), " +
                    "content TEXT, " +
                    "deleted INT DEFAULT 0)");

            // Create t_product_v2 table (with version)
            stmt.execute("CREATE TABLE t_product_v2 (" +
                    "product_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "product_name VARCHAR(100), " +
                    "price DOUBLE, " +
                    "version INT DEFAULT 0)");

            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    @BeforeEach
    void openSession() throws SQLException {
        session = sqlSessionFactory.openSession();
        userMapper = session.getMapper(UserMapper.class);
        orderMapper = session.getMapper(OrderMapper.class);
        transactionMapper = session.getMapper(TransactionMapper.class);
        articleMapper = session.getMapper(ArticleMapper.class);
        productV2Mapper = session.getMapper(ProductV2Mapper.class);

        // Clean up tables before each test
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_user");
            stmt.execute("DELETE FROM t_order");
            stmt.execute("DELETE FROM t_transaction");
            stmt.execute("DELETE FROM t_article");
            stmt.execute("DELETE FROM t_product_v2");
        }
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    // ========== Basic Functionality Tests ==========

    /**
     * Test 1: AUTO ID type batch insert success
     */
    @Test
    void testInsertBatch_AutoId_Success() throws SQLException {
        // Arrange - create 10 users
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            users.add(new User("user" + i, "user" + i + "@test.com", 20 + i));
        }

        // Act
        int result = userMapper.insertBatch(users);
        session.commit();

        // Assert - verify return value
        assertEquals(10, result, "Should return 10 for 10 inserted records");

        // Assert - verify all IDs are backfilled and unique
        Set<Long> ids = new HashSet<>();
        for (User user : users) {
            assertNotNull(user.getId(), "ID should be backfilled");
            assertTrue(user.getId() > 0, "ID should be positive");
            ids.add(user.getId());
        }
        assertEquals(10, ids.size(), "All IDs should be unique");

        // Assert - verify database records
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_user");
            rs.next();
            assertEquals(10, rs.getInt(1), "Database should have 10 records");
        }
    }

    /**
     * Test 2: UUID ID type batch insert success
     */
    @Test
    void testInsertBatch_UuidId_Success() throws SQLException {
        // Arrange - create 5 orders
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            orders.add(new Order("ORD" + i, new BigDecimal("100.00")));
        }

        // Act
        int result = orderMapper.insertBatch(orders);
        session.commit();

        // Assert - verify return value
        assertEquals(5, result, "Should return 5 for 5 inserted records");

        // Assert - verify all IDs are generated (UUID format)
        Set<String> ids = new HashSet<>();
        for (Order order : orders) {
            assertNotNull(order.getOrderId(), "UUID should be generated");
            assertTrue(order.getOrderId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                    "Should be valid UUID format");
            ids.add(order.getOrderId());
        }
        assertEquals(5, ids.size(), "All UUIDs should be unique");

        // Assert - verify database records
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_order");
            rs.next();
            assertEquals(5, rs.getInt(1), "Database should have 5 records");
        }
    }

    /**
     * Test 3: SNOWFLAKE ID type batch insert success
     */
    @Test
    void testInsertBatch_SnowflakeId_Success() throws SQLException {
        // Arrange - create 8 transactions
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            transactions.add(new Transaction("TXN" + i, "PENDING"));
        }

        // Act
        int result = transactionMapper.insertBatch(transactions);
        session.commit();

        // Assert - verify return value
        assertEquals(8, result, "Should return 8 for 8 inserted records");

        // Assert - verify all IDs are generated (SNOWFLAKE format - String)
        Set<String> ids = new HashSet<>();
        for (Transaction txn : transactions) {
            assertNotNull(txn.getTransactionId(), "SNOWFLAKE ID should be generated");
            assertFalse(txn.getTransactionId().isEmpty(), "SNOWFLAKE ID should not be empty");
            ids.add(txn.getTransactionId());
        }
        assertEquals(8, ids.size(), "All SNOWFLAKE IDs should be unique");

        // Assert - verify database records
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_transaction");
            rs.next();
            assertEquals(8, rs.getInt(1), "Database should have 8 records");
        }
    }

    // ========== Edge Case Tests ==========

    /**
     * Test 4: Empty list throws exception
     */
    @Test
    void testInsertBatch_EmptyList_ThrowsException() {
        // Arrange
        List<User> emptyList = new ArrayList<>();

        // Act & Assert
        // MyBatis wraps the IllegalArgumentException in PersistenceException
        Exception exception = assertThrows(
                Exception.class,
                () -> userMapper.insertBatch(emptyList),
                "Should throw exception for empty list"
        );
        assertTrue(exception.getMessage().contains("cannot be null or empty") ||
                   exception.getCause().getMessage().contains("cannot be null or empty"),
                "Exception message should indicate empty list");
    }

    /**
     * Test 5: Null list throws exception
     */
    @Test
    void testInsertBatch_NullList_ThrowsException() {
        // Act & Assert
        assertThrows(
                Exception.class,
                () -> userMapper.insertBatch(null),
                "Should throw exception for null list"
        );
    }

    /**
     * Test 6: Single entity batch insert
     */
    @Test
    void testInsertBatch_SingleEntity_Success() throws SQLException {
        // Arrange
        List<User> users = new ArrayList<>();
        users.add(new User("singleuser", "single@test.com", 30));

        // Act
        int result = userMapper.insertBatch(users);
        session.commit();

        // Assert
        assertEquals(1, result, "Should return 1 for 1 inserted record");
        assertNotNull(users.get(0).getId(), "ID should be backfilled");

        // Verify database
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_user");
            rs.next();
            assertEquals(1, rs.getInt(1), "Database should have 1 record");
        }
    }

    /**
     * Test 7: Large batch insert (100 records)
     */
    @Test
    void testInsertBatch_LargeBatch_Success() throws SQLException {
        // Arrange - create 100 users
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            users.add(new User("user" + i, "user" + i + "@test.com", 20 + (i % 50)));
        }

        // Act
        int result = userMapper.insertBatch(users);
        session.commit();

        // Assert
        assertEquals(100, result, "Should return 100 for 100 inserted records");

        // Verify all IDs are unique
        Set<Long> ids = new HashSet<>();
        for (User user : users) {
            assertNotNull(user.getId(), "ID should be backfilled");
            ids.add(user.getId());
        }
        assertEquals(100, ids.size(), "All 100 IDs should be unique");

        // Verify database
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_user");
            rs.next();
            assertEquals(100, rs.getInt(1), "Database should have 100 records");
        }
    }

    // ========== Special Field Tests ==========

    /**
     * Test 8: Soft delete field initialized to 0
     */
    @Test
    void testInsertBatch_WithDeletedField_InitializedToZero() throws SQLException {
        // Arrange - create articles without setting deleted field
        List<Article> articles = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Article article = new Article();
            article.setTitle("Article " + i);
            article.setContent("Content " + i);
            article.setDeleted(null); // Explicitly set to null
            articles.add(article);
        }

        // Act
        int result = articleMapper.insertBatch(articles);
        session.commit();

        // Assert
        assertEquals(5, result, "Should return 5 for 5 inserted records");

        // Verify deleted field is initialized to 0
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT deleted FROM t_article");
            while (rs.next()) {
                assertEquals(0, rs.getInt("deleted"), "Deleted field should be initialized to 0");
            }
        }
    }

    /**
     * Test 9: Version field initialized to 0
     */
    @Test
    void testInsertBatch_WithVersionField_InitializedToZero() throws SQLException {
        // Arrange - create products without setting version field
        List<ProductV2> products = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ProductV2 product = new ProductV2();
            product.setProductName("Product " + i);
            product.setPrice(100.0 + i);
            product.setVersion(null); // Explicitly set to null
            products.add(product);
        }

        // Act
        int result = productV2Mapper.insertBatch(products);
        session.commit();

        // Assert
        assertEquals(5, result, "Should return 5 for 5 inserted records");

        // Verify version field is initialized to 0
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT version FROM t_product_v2");
            while (rs.next()) {
                assertEquals(0, rs.getInt("version"), "Version field should be initialized to 0");
            }
        }
    }

    // ========== ID Preset Tests ==========

    /**
     * Test 10: UUID preset IDs are not overwritten
     */
    @Test
    void testInsertBatch_UuidId_PresetIds_NotOverwritten() throws SQLException {
        // Arrange - create orders with some preset UUIDs
        List<Order> orders = new ArrayList<>();
        
        // First 2 orders with preset UUIDs
        Order order1 = new Order("ORD1", new BigDecimal("100.00"));
        order1.setOrderId("12345678-1234-1234-1234-123456789012");
        orders.add(order1);
        
        Order order2 = new Order("ORD2", new BigDecimal("200.00"));
        order2.setOrderId("87654321-4321-4321-4321-210987654321");
        orders.add(order2);
        
        // Last 3 orders without preset UUIDs
        orders.add(new Order("ORD3", new BigDecimal("300.00")));
        orders.add(new Order("ORD4", new BigDecimal("400.00")));
        orders.add(new Order("ORD5", new BigDecimal("500.00")));

        // Act
        int result = orderMapper.insertBatch(orders);
        session.commit();

        // Assert
        assertEquals(5, result, "Should return 5 for 5 inserted records");
        
        // Verify preset IDs are not overwritten
        assertEquals("12345678-1234-1234-1234-123456789012", orders.get(0).getOrderId(), 
                "Preset UUID should not be overwritten");
        assertEquals("87654321-4321-4321-4321-210987654321", orders.get(1).getOrderId(), 
                "Preset UUID should not be overwritten");
        
        // Verify non-preset IDs are generated
        assertNotNull(orders.get(2).getOrderId(), "UUID should be generated");
        assertNotNull(orders.get(3).getOrderId(), "UUID should be generated");
        assertNotNull(orders.get(4).getOrderId(), "UUID should be generated");
        
        // Verify all IDs are unique
        Set<String> ids = new HashSet<>();
        for (Order order : orders) {
            ids.add(order.getOrderId());
        }
        assertEquals(5, ids.size(), "All IDs should be unique");
    }

    /**
     * Test 11: SNOWFLAKE preset IDs are not overwritten
     */
    @Test
    void testInsertBatch_SnowflakeId_PresetIds_NotOverwritten() throws SQLException {
        // Arrange - create transactions with some preset SNOWFLAKE IDs
        List<Transaction> transactions = new ArrayList<>();
        
        // First 2 transactions with preset IDs
        Transaction txn1 = new Transaction("TXN1", "PENDING");
        txn1.setTransactionId("1234567890123456789");
        transactions.add(txn1);
        
        Transaction txn2 = new Transaction("TXN2", "COMPLETED");
        txn2.setTransactionId("9876543210987654321");
        transactions.add(txn2);
        
        // Last 3 transactions without preset IDs
        transactions.add(new Transaction("TXN3", "PENDING"));
        transactions.add(new Transaction("TXN4", "FAILED"));
        transactions.add(new Transaction("TXN5", "PENDING"));

        // Act
        int result = transactionMapper.insertBatch(transactions);
        session.commit();

        // Assert
        assertEquals(5, result, "Should return 5 for 5 inserted records");
        
        // Verify preset IDs are not overwritten
        assertEquals("1234567890123456789", transactions.get(0).getTransactionId(), 
                "Preset SNOWFLAKE ID should not be overwritten");
        assertEquals("9876543210987654321", transactions.get(1).getTransactionId(), 
                "Preset SNOWFLAKE ID should not be overwritten");
        
        // Verify non-preset IDs are generated
        assertNotNull(transactions.get(2).getTransactionId(), "SNOWFLAKE ID should be generated");
        assertNotNull(transactions.get(3).getTransactionId(), "SNOWFLAKE ID should be generated");
        assertNotNull(transactions.get(4).getTransactionId(), "SNOWFLAKE ID should be generated");
        
        // Verify all IDs are unique
        Set<String> ids = new HashSet<>();
        for (Transaction txn : transactions) {
            ids.add(txn.getTransactionId());
        }
        assertEquals(5, ids.size(), "All IDs should be unique");
    }
}
