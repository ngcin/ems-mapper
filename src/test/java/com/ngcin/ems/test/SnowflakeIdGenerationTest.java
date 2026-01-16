package com.ngcin.ems.test;

import com.ngcin.ems.test.mapper.PaymentMapper;
import com.ngcin.ems.test.mapper.TransactionMapper;
import com.ngcin.ems.test.entity.Payment;
import com.ngcin.ems.test.entity.Transaction;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SNOWFLAKE ID generation strategy.
 */
class SnowflakeIdGenerationTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private PaymentMapper paymentMapper;
    private TransactionMapper transactionMapper;

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
                                <property name="url" value="jdbc:h2:mem:testdb4;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.PaymentMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.TransactionMapper"/>
                    </mappers>
                </configuration>
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes());
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        // Initialize database schema
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();

            // Create t_payment table with SNOWFLAKE Long type
            stmt.execute("CREATE TABLE t_payment (" +
                    "payment_id BIGINT PRIMARY KEY, " +
                    "payment_no VARCHAR(50) NOT NULL, " +
                    "amount DECIMAL(10,2))");

            // Create t_transaction table with SNOWFLAKE String type
            stmt.execute("CREATE TABLE t_transaction (" +
                    "transaction_id VARCHAR(20) PRIMARY KEY, " +
                    "txn_no VARCHAR(50) NOT NULL, " +
                    "status VARCHAR(20))");

            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    @BeforeEach
    void openSession() throws SQLException {
        session = sqlSessionFactory.openSession();
        paymentMapper = session.getMapper(PaymentMapper.class);
        transactionMapper = session.getMapper(TransactionMapper.class);

        // Clean up tables before each test
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_transaction");
            stmt.execute("DELETE FROM t_payment");
        }
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Test SNOWFLAKE Long type ID generation.
     */
    @Test
    void testInsert_WithSnowflakeLongType() {
        // Arrange
        Payment payment = new Payment("PAY001", new java.math.BigDecimal("100.00"));

        // Act
        int result = paymentMapper.insert(payment);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertNotNull(payment.getPaymentId(), "SNOWFLAKE ID should be generated");
        assertTrue(payment.getPaymentId() > 0, "SNOWFLAKE ID should be positive");

        // Verify in database
        Long dbId = getDbLongId("t_payment", "payment_id", "payment_no", "PAY001");
        assertEquals(payment.getPaymentId(), dbId, "Generated ID should match database ID");
    }

    /**
     * Test SNOWFLAKE String type ID generation.
     */
    @Test
    void testInsert_WithSnowflakeStringType() {
        // Arrange
        Transaction transaction = new Transaction("TXN001", "PENDING");

        // Act
        int result = transactionMapper.insert(transaction);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertNotNull(transaction.getTransactionId(), "SNOWFLAKE ID should be generated");
        assertFalse(transaction.getTransactionId().isEmpty(), "SNOWFLAKE ID should not be empty");

        // Verify it's a valid number
        Long idValue = Long.parseLong(transaction.getTransactionId());
        assertTrue(idValue > 0, "SNOWFLAKE ID should be positive");

        // Verify in database
        String dbId = getDbStringId("t_transaction", "transaction_id", "txn_no", "TXN001");
        assertEquals(transaction.getTransactionId(), dbId, "Generated ID should match database ID");
    }

    /**
     * Test multiple SNOWFLAKE entities - verify uniqueness and incrementing.
     */
    @Test
    void testInsert_MultipleSnowflakeEntities() {
        // Arrange
        Payment p1 = new Payment("PAY001", new java.math.BigDecimal("10.00"));
        Payment p2 = new Payment("PAY002", new java.math.BigDecimal("20.00"));
        Payment p3 = new Payment("PAY003", new java.math.BigDecimal("30.00"));

        // Act
        paymentMapper.insert(p1);
        session.commit();
        paymentMapper.insert(p2);
        session.commit();
        paymentMapper.insert(p3);
        session.commit();

        // Assert
        assertNotNull(p1.getPaymentId(), "p1 SNOWFLAKE ID should be generated");
        assertNotNull(p2.getPaymentId(), "p2 SNOWFLAKE ID should be generated");
        assertNotNull(p3.getPaymentId(), "p3 SNOWFLAKE ID should be generated");

        // Verify uniqueness
        Set<Long> ids = new HashSet<>();
        ids.add(p1.getPaymentId());
        ids.add(p2.getPaymentId());
        ids.add(p3.getPaymentId());
        assertEquals(3, ids.size(), "All SNOWFLAKE IDs should be unique");

        // Verify incrementing (should be monotonic increasing due to same millisecond)
        assertTrue(p2.getPaymentId() > p1.getPaymentId(), "IDs should be increasing");
        assertTrue(p3.getPaymentId() > p2.getPaymentId(), "IDs should be increasing");
    }

    /**
     * Test that pre-set SNOWFLAKE ID is not regenerated.
     */
    @Test
    void testInsert_WithPreSetSnowflakeId() {
        // Arrange
        Long preSetId = 123456789L;
        Payment payment = new Payment("PAY001", new java.math.BigDecimal("100.00"));
        payment.setPaymentId(preSetId);

        // Act
        int result = paymentMapper.insert(payment);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertEquals(preSetId, payment.getPaymentId(), "Pre-set ID should not be changed");

        // Verify in database
        Long dbId = getDbLongId("t_payment", "payment_id", "payment_no", "PAY001");
        assertEquals(preSetId, dbId, "Pre-set ID should match database ID");
    }

    /**
     * Test SNOWFLAKE ID uniqueness and properties.
     */
    @Test
    void testInsert_SnowflakeUniqueness() {
        // Arrange - create 100 payments to stress test uniqueness
        Set<Long> ids = new HashSet<>();
        int count = 100;

        // Act
        for (int i = 0; i < count; i++) {
            Payment payment = new Payment("PAY" + i, new java.math.BigDecimal("1.00"));
            paymentMapper.insert(payment);
            session.commit();

            Long id = payment.getPaymentId();
            assertNotNull(id, "ID should be generated");
            assertTrue(ids.add(id), "ID should be unique: duplicate found " + id);
        }

        // Assert
        assertEquals(count, ids.size(), "All 100 IDs should be unique");
    }

    /**
     * Test SNOWFLAKE ID ordering (time-based ordering).
     */
    @Test
    void testInsert_SnowflakeOrdering() throws InterruptedException {
        // Arrange
        Payment p1 = new Payment("PAY001", new java.math.BigDecimal("10.00"));

        // Act - insert first payment
        paymentMapper.insert(p1);
        session.commit();

        // Small delay to ensure different timestamp
        Thread.sleep(2);

        Payment p2 = new Payment("PAY002", new java.math.BigDecimal("20.00"));
        paymentMapper.insert(p2);
        session.commit();

        // Assert
        assertTrue(p2.getPaymentId() > p1.getPaymentId(),
            "Later payment should have larger SNOWFLAKE ID (timestamp-based)");

        // The difference should be reasonable (timestamp portion increased)
        long diff = p2.getPaymentId() - p1.getPaymentId();
        assertTrue(diff > 0, "Difference should be positive");
        // Account for sequence overflow: at least one millisecond difference
        // which is (1 << 22) = 4194304 in SNOWFLAKE representation
        assertTrue(diff >= 4194304 || diff < 10000,
            "Difference should reflect timestamp or sequence change");
    }

    /**
     * Test SNOWFLAKE ID structure validation.
     */
    @Test
    void testInsert_SnowflakeStructureValidation() {
        // Arrange
        Payment payment = new Payment("PAY001", new java.math.BigDecimal("100.00"));

        // Act
        paymentMapper.insert(payment);
        session.commit();

        // Assert - verify SNOWFLAKE structure
        long id = payment.getPaymentId();

        // Extract components (based on implementation):
        // - timestamp: bits 41-22 (41 bits from right, after 22 bits for node+sequence)
        // - node: bits 21-12 (10 bits)
        // - sequence: bits 11-0 (12 bits)

        long sequence = id & 0xFFF;  // lowest 12 bits
        long node = (id >> 12) & 0x3FF;  // next 10 bits
        long timestamp = (id >> 22);  // remaining bits

        // Validate sequence (0-4095)
        assertTrue(sequence >= 0 && sequence <= 4095, "Sequence should be in range 0-4095");
        assertEquals(1, node, "Node should be 1 (hardcoded in implementation)");

        // Validate timestamp (should be reasonable - not too far from current time)
        long epoch = 1609459200000L; // 2021-01-01 00:00:00 UTC
        long currentMillis = System.currentTimeMillis();
        long generatedTime = epoch + timestamp;

        // Generated time should be close to current time (within 1 minute tolerance)
        assertTrue(Math.abs(currentMillis - generatedTime) < 60000,
            "Generated timestamp should be close to current time");
    }

    // Helper methods

    private Long getDbLongId(String tableName, String idColumn, String whereColumn, String whereValue) {
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
