package com.ngcin.ems.test;

import com.ngcin.ems.test.mapper.OrderMapper;
import com.ngcin.ems.test.mapper.PaymentMapper;
import com.ngcin.ems.test.mapper.ProductMapper;
import com.ngcin.ems.test.mapper.UserMapper;
import com.ngcin.ems.test.entity.Order;
import com.ngcin.ems.test.entity.Payment;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for getById method in BaseMapper.
 */
class GetByIdTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private UserMapper userMapper;
    private OrderMapper orderMapper;
    private PaymentMapper paymentMapper;
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
                                <property name="url" value="jdbc:h2:mem:testdb5;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.UserMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.OrderMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.PaymentMapper"/>
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

            // Create t_user table with AUTO ID
            stmt.execute("CREATE TABLE t_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL, " +
                    "email VARCHAR(100), " +
                    "age INT, " +
                    "create_time TIMESTAMP)");

            // Create t_order table with UUID ID
            stmt.execute("CREATE TABLE t_order (" +
                    "order_id VARCHAR(36) PRIMARY KEY, " +
                    "order_no VARCHAR(50) NOT NULL, " +
                    "amount DECIMAL(10,2))");

            // Create t_payment table with SNOWFLAKE Long ID
            stmt.execute("CREATE TABLE t_payment (" +
                    "payment_id BIGINT PRIMARY KEY, " +
                    "payment_no VARCHAR(50) NOT NULL, " +
                    "amount DECIMAL(10,2))");

            // Create t_product table with custom ID field (productId)
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
        orderMapper = session.getMapper(OrderMapper.class);
        paymentMapper = session.getMapper(PaymentMapper.class);
        productMapper = session.getMapper(ProductMapper.class);

        // Clean up tables before each test
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_product");
            stmt.execute("DELETE FROM t_payment");
            stmt.execute("DELETE FROM t_order");
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
     * Test getById with AUTO ID type.
     */
    @Test
    void testGetById_WithAutoId() {
        // Arrange - insert a user
        User user = new User("testuser", "test@example.com", 25);
        userMapper.insert(user);
        session.commit();
        Long userId = user.getId();

        // Act
        User foundUser = userMapper.getById(userId);

        // Assert
        assertNotNull(foundUser, "User should be found");
        assertEquals(userId, foundUser.getId(), "ID should match");
        assertEquals("testuser", foundUser.getUsername(), "Username should match");
        assertEquals("test@example.com", foundUser.getEmail(), "Email should match");
        assertEquals(25, foundUser.getAge(), "Age should match");
    }

    /**
     * Test getById with UUID ID type.
     */
    @Test
    void testGetById_WithUuidId() throws SQLException {
        // Arrange - insert an order
        Order order = new Order("ORD001", new BigDecimal("99.99"));
        orderMapper.insert(order);
        session.commit();
        String orderId = order.getOrderId();

        // Act
        Order foundOrder = orderMapper.getById(orderId);
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select * from  t_order");
            if (resultSet.next()) {
                String orderId1 = resultSet.getString("order_id");
                System.out.println(orderId1);
            }
        }
        // Assert
        assertNotNull(foundOrder, "Order should be found");
        assertEquals(orderId, foundOrder.getOrderId(), "Order ID should match");
        assertEquals("ORD001", foundOrder.getOrderNo(), "Order number should match");
        assertEquals(new BigDecimal("99.99"), foundOrder.getAmount(), "Amount should match");
    }

    /**
     * Test getById with SNOWFLAKE ID type (Long).
     */
    @Test
    void testGetById_WithSnowflakeId() {
        // Arrange - insert a payment
        Payment payment = new Payment("PAY001", new BigDecimal("100.00"));
        paymentMapper.insert(payment);
        session.commit();
        Long paymentId = payment.getPaymentId();

        // Act
        Payment foundPayment = paymentMapper.getById(paymentId);

        // Assert
        assertNotNull(foundPayment, "Payment should be found");
        assertEquals(paymentId, foundPayment.getPaymentId(), "Payment ID should match");
        assertEquals("PAY001", foundPayment.getPaymentNo(), "Payment number should match");
        assertEquals(new BigDecimal("100.00"), foundPayment.getAmount(), "Amount should match");
    }

    /**
     * Test getById with custom ID field name (productId instead of id).
     */
    @Test
    void testGetById_WithCustomIdFieldName() {
        // Arrange - insert a product
        Product product = new Product("Laptop", 999.99);
        productMapper.insert(product);
        session.commit();
        Long productId = product.getProductId();

        // Act
        Product foundProduct = productMapper.getById(productId);

        // Assert
        assertNotNull(foundProduct, "Product should be found");
        assertEquals(productId, foundProduct.getProductId(), "Product ID should match");
        assertEquals("Laptop", foundProduct.getProductName(), "Product name should match");
        assertEquals(999.99, foundProduct.getPrice(), "Price should match");
    }

    /**
     * Test getById with non-existent ID - should return null.
     */
    @Test
    void testGetById_NotFound() {
        // Arrange
        Long nonExistentId = 99999L;

        // Act
        User foundUser = userMapper.getById(nonExistentId);

        // Assert
        assertNull(foundUser, "Should return null for non-existent ID");
    }

    /**
     * Test getById with null ID - should return null or throw exception.
     */
    @Test
    void testGetById_WithNullId() {
        // Act
        User foundUser = userMapper.getById(null);

        // Assert
        assertNull(foundUser, "Should return null for null ID");
    }

    /**
     * Test getById after multiple inserts - finds correct record.
     */
    @Test
    void testGetById_MultipleRecords() {
        // Arrange - insert multiple users
        User u1 = new User("user1", "user1@test.com", 20);
        User u2 = new User("user2", "user2@test.com", 30);
        User u3 = new User("user3", "user3@test.com", 40);

        userMapper.insert(u1);
        session.commit();
        userMapper.insert(u2);
        session.commit();
        userMapper.insert(u3);
        session.commit();

        Long id2 = u2.getId();

        // Act - get the second user
        User foundUser = userMapper.getById(id2);

        // Assert
        assertNotNull(foundUser, "User should be found");
        assertEquals(id2, foundUser.getId(), "Should find correct user by ID");
        assertEquals("user2", foundUser.getUsername(), "Should be user2");
    }

    /**
     * Test getById with different ID types - verify all work correctly.
     */
    @Test
    void testGetById_MixedIdTypes() {
        // Arrange - insert entities with different ID types
        User user = new User("testuser", "test@test.com", 25);
        Order order = new Order("ORD001", new BigDecimal("50.00"));
        Payment payment = new Payment("PAY001", new BigDecimal("25.00"));

        userMapper.insert(user);
        session.commit();
        orderMapper.insert(order);
        session.commit();
        paymentMapper.insert(payment);
        session.commit();

        Long userId = user.getId();
        String orderId = order.getOrderId();
        Long paymentId = payment.getPaymentId();

        // Act
        User foundUser = userMapper.getById(userId);
        Order foundOrder = orderMapper.getById(orderId);
        Payment foundPayment = paymentMapper.getById(paymentId);

        // Assert
        assertNotNull(foundUser, "User should be found");
        assertNotNull(foundOrder, "Order should be found");
        assertNotNull(foundPayment, "Payment should be found");

        assertEquals("testuser", foundUser.getUsername());
        assertEquals("ORD001", foundOrder.getOrderNo());
        assertEquals("PAY001", foundPayment.getPaymentNo());
    }

    /**
     * Test getById returns complete entity with all fields.
     */
    @Test
    void testGetById_AllFieldsMapped() {
        // Arrange
        User user = new User("fulluser", "full@test.com", 35);
        userMapper.insert(user);
        session.commit();
        Long userId = user.getId();

        // Act
        User foundUser = userMapper.getById(userId);

        // Assert - verify all fields are mapped
        assertNotNull(foundUser, "User should be found");
        assertNotNull(foundUser.getId(), "ID should be set");
        assertNotNull(foundUser.getUsername(), "Username should be set");
        assertNotNull(foundUser.getEmail(), "Email should be set");
        assertNotNull(foundUser.getAge(), "Age should be set");
        assertNotNull(foundUser.getCreateTime(), "CreateTime should be set");
    }
}
