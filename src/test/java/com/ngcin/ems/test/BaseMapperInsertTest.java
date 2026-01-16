package com.ngcin.ems.test;

import com.ngcin.ems.test.mapper.UserMapper;
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

class BaseMapperInsertTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private UserMapper userMapper;

    @BeforeAll
    static void setUp() {
        // Create mybatis-config.xml content
        String mybatisConfig = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE configuration
                        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                        "https://mybatis.org/dtd/mybatis-3-config.dtd">
                <configuration>
                    <settings>
                        <setting name="mapUnderscoreToCamelCase" value="true"/>
                    </settings>
                    <environments default="development">
                        <environment id="development">
                            <transactionManager type="JDBC"/>
                            <dataSource type="POOLED">
                                <property name="driver" value="org.h2.Driver"/>
                                <property name="url" value="jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.UserMapper"/>
                    </mappers>
                </configuration>
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes());
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        // Initialize database schema
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE t_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL, " +
                    "email VARCHAR(100), " +
                    "age INT, " +
                    "create_time TIMESTAMP)");
            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    @BeforeEach
    void openSession() throws SQLException {
        session = sqlSessionFactory.openSession();
        userMapper = session.getMapper(UserMapper.class);

        // Clean up table before each test
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_user");
        }
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void testInsert_Success() {
        // Arrange
        User user = new User("testuser", "test@example.com", 25);

        // Act
        int result = userMapper.insert(user);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertNotNull(user.getId(), "ID should be generated after insert");

        // Verify the record in database using JDBC
        try (Connection conn = sqlSessionFactory.openSession().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM t_user WHERE id = " + user.getId())) {

            assertTrue(rs.next(), "Should find the record");
            assertEquals("testuser", rs.getString("username"));
            assertEquals("test@example.com", rs.getString("email"));
            assertEquals(25, rs.getInt("age"));
        } catch (SQLException e) {
            fail("Failed to verify database record: " + e.getMessage());
        }
    }

    @Test
    void testInsert_MultipleRecords() {
        // Arrange
        User user1 = new User("user1", "user1@example.com", 20);
        User user2 = new User("user2", "user2@example.com", 30);
        User user3 = new User("user3", "user3@example.com", 40);

        // Act
        userMapper.insert(user1);
        session.commit();
        userMapper.insert(user2);
        session.commit();
        userMapper.insert(user3);
        session.commit();

        // Assert
        int count = getCountFromDatabase();
        assertEquals(3, count, "Should have 3 records in database");

        assertNotNull(user1.getId(), "user1 ID should be generated");
        assertNotNull(user2.getId(), "user2 ID should be generated");
        assertNotNull(user3.getId(), "user3 ID should be generated");

        // Verify IDs are different
        assertNotEquals(user1.getId(), user2.getId(), "IDs should be unique");
        assertNotEquals(user2.getId(), user3.getId(), "IDs should be unique");
    }

    @Test
    void testInsert_WithNullValues() {
        // Arrange
        User user = new User();
        user.setUsername("nulluser");
        user.setEmail(null);
        user.setAge(null);
        user.setCreateTime(null);

        // Act
        int result = userMapper.insert(user);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertNotNull(user.getId(), "ID should be generated after insert");

        // Verify the record in database
        try (Connection conn = sqlSessionFactory.openSession().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM t_user WHERE id = " + user.getId())) {

            assertTrue(rs.next(), "Should find the record");
            assertEquals("nulluser", rs.getString("username"));
            assertNull(rs.getObject("email"), "email should be null");
            assertNull(rs.getObject("age"), "age should be null");
        } catch (SQLException e) {
            fail("Failed to verify database record: " + e.getMessage());
        }
    }

    @Test
    void testInsert_AutoIncrementId() {
        // Arrange
        User user = new User("autotest", "auto@example.com", 28);

        // Act
        userMapper.insert(user);
        session.commit();

        // Assert
        assertNotNull(user.getId(), "ID should be auto-generated");
        assertTrue(user.getId() > 0, "ID should be positive");

        // Verify the ID was actually set in database
        try (Connection conn = sqlSessionFactory.openSession().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM t_user WHERE username = 'autotest'")) {

            assertTrue(rs.next(), "Should find the record");
            Long dbId = rs.getLong("id");
            assertEquals(user.getId(), dbId, "Generated ID should match database ID");
        } catch (SQLException e) {
            fail("Failed to verify database record: " + e.getMessage());
        }
    }

    private int getCountFromDatabase() {
        try (Connection conn = sqlSessionFactory.openSession().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_user")) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count records", e);
        }
    }
}
