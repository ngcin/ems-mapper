package com.ngcin.ems.test;

import com.ngcin.ems.mapper.core.Page;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PaginationInterceptor.
 *
 * <p>This test class verifies that the PaginationInterceptor correctly:
 * <ul>
 *   <li>Executes COUNT queries to get total records</li>
 *   <li>Applies LIMIT/OFFSET clauses for pagination</li>
 *   <li>Sets total count on Page objects</li>
 *   <li>Handles edge cases (empty results, overflow)</li>
 * </ul>
 */
class PaginationInterceptorTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private UserMapper userMapper;

    @BeforeAll
    static void setUp() {
        // Create mybatis-config.xml content with PaginationInterceptor registered
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
                        <plugin interceptor="com.ngcin.ems.mapper.core.PaginationInterceptor">
                            <property name="dialectType" value="mysql"/>
                            <property name="overflow" value="false"/>
                        </plugin>
                    </plugins>
                    <environments default="development">
                        <environment id="development">
                            <transactionManager type="JDBC"/>
                            <dataSource type="POOLED">
                                <property name="driver" value="org.h2.Driver"/>
                                <property name="url" value="jdbc:h2:mem:testdb_pagination;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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

            // Create t_user table
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

    /**
     * Test basic pagination - first page.
     */
    @Test
    void testPagination_FirstPage() {
        // Arrange - insert 25 users
        for (int i = 1; i <= 25; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query first page (10 records per page)
        Page<User> page = new Page<>(1, 10);
        List<User> results = userMapper.selectPage(page, null);

        // Assert
        assertNotNull(results, "Results should not be null");
        assertEquals(10, results.size(), "Should return 10 records");
        assertEquals(25, page.getTotal(), "Total should be 25");
        assertEquals(3, page.getPages(), "Should have 3 pages");
        assertEquals("user1", results.get(0).getUsername(), "First user should be user1");
    }

    /**
     * Test pagination - second page.
     */
    @Test
    void testPagination_SecondPage() {
        // Arrange - insert 25 users
        for (int i = 1; i <= 25; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query second page
        Page<User> page = new Page<>(2, 10);
        List<User> results = userMapper.selectPage(page, null);

        // Assert
        assertEquals(10, results.size(), "Should return 10 records");
        assertEquals(25, page.getTotal(), "Total should be 25");
        assertEquals("user11", results.get(0).getUsername(), "First user on page 2 should be user11");
    }

    /**
     * Test pagination - last page with partial results.
     */
    @Test
    void testPagination_LastPagePartial() {
        // Arrange - insert 25 users
        for (int i = 1; i <= 25; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query third page (should have 5 records)
        Page<User> page = new Page<>(3, 10);
        List<User> results = userMapper.selectPage(page, null);

        // Assert
        assertEquals(5, results.size(), "Should return 5 records on last page");
        assertEquals(25, page.getTotal(), "Total should be 25");
        assertEquals(3, page.getPages(), "Should have 3 pages");
        assertEquals("user21", results.get(0).getUsername(), "First user on page 3 should be user21");
    }

    /**
     * Test pagination with different page sizes.
     */
    @Test
    void testPagination_DifferentPageSizes() {
        // Arrange - insert 50 users
        for (int i = 1; i <= 50; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act & Assert - page size 5
        Page<User> page5 = new Page<>(1, 5);
        List<User> results5 = userMapper.selectPage(page5, null);
        assertEquals(5, results5.size(), "Should return 5 records");
        assertEquals(50, page5.getTotal(), "Total should be 50");
        assertEquals(10, page5.getPages(), "Should have 10 pages with size 5");

        // Act & Assert - page size 20
        Page<User> page20 = new Page<>(1, 20);
        List<User> results20 = userMapper.selectPage(page20, null);
        assertEquals(20, results20.size(), "Should return 20 records");
        assertEquals(50, page20.getTotal(), "Total should be 50");
        assertEquals(3, page20.getPages(), "Should have 3 pages with size 20");
    }

    /**
     * Test pagination with empty results.
     */
    @Test
    void testPagination_EmptyResults() {
        // Arrange - no users inserted

        // Act
        Page<User> page = new Page<>(1, 10);
        List<User> results = userMapper.selectPage(page, null);

        // Assert
        assertNotNull(results, "Results should not be null");
        assertEquals(0, results.size(), "Should return 0 records");
        assertEquals(0, page.getTotal(), "Total should be 0");
        assertEquals(0, page.getPages(), "Should have 0 pages");
    }

    /**
     * Test pagination with single record.
     */
    @Test
    void testPagination_SingleRecord() {
        // Arrange - insert 1 user
        User user = new User("user1", "user1@test.com", 25);
        userMapper.insert(user);
        session.commit();

        // Act
        Page<User> page = new Page<>(1, 10);
        List<User> results = userMapper.selectPage(page, null);

        // Assert
        assertEquals(1, results.size(), "Should return 1 record");
        assertEquals(1, page.getTotal(), "Total should be 1");
        assertEquals(1, page.getPages(), "Should have 1 page");
    }

    /**
     * Test pagination with exact page size match.
     */
    @Test
    void testPagination_ExactPageSizeMatch() {
        // Arrange - insert exactly 20 users (2 pages of 10)
        for (int i = 1; i <= 20; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query second page
        Page<User> page = new Page<>(2, 10);
        List<User> results = userMapper.selectPage(page, null);

        // Assert
        assertEquals(10, results.size(), "Should return 10 records");
        assertEquals(20, page.getTotal(), "Total should be 20");
        assertEquals(2, page.getPages(), "Should have exactly 2 pages");
    }

    /**
     * Test pagination with WHERE conditions.
     */
    @Test
    void testPagination_WithConditions() {
        // Arrange - insert users with different ages
        for (int i = 1; i <= 30; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + (i % 3));
            userMapper.insert(user);
        }
        session.commit();

        // Act - query users with age = 21, first page
        User queryUser = new User();
        queryUser.setAge(21);
        Page<User> page = new Page<>(1, 5);
        List<User> results = userMapper.selectPage(page, queryUser);

        // Assert
        assertNotNull(results, "Results should not be null");
        assertEquals(5, results.size(), "Should return 5 records");
        assertEquals(10, page.getTotal(), "Total should be 10 (users with age 21)");
        assertEquals(2, page.getPages(), "Should have 2 pages");

        // Verify all results have age = 21
        for (User user : results) {
            assertEquals(21, user.getAge(), "All users should have age 21");
        }
    }

    /**
     * Test pagination beyond available pages (overflow scenario).
     */
    @Test
    void testPagination_BeyondAvailablePages() {
        // Arrange - insert 15 users
        for (int i = 1; i <= 15; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query page 5 (only 2 pages exist)
        Page<User> page = new Page<>(5, 10);
        List<User> results = userMapper.selectPage(page, null);

        // Assert - should return empty results
        assertNotNull(results, "Results should not be null");
        assertEquals(0, results.size(), "Should return 0 records when beyond available pages");
        assertEquals(15, page.getTotal(), "Total should still be 15");
    }

    /**
     * Test pagination with page size of 1.
     */
    @Test
    void testPagination_PageSizeOne() {
        // Arrange - insert 5 users
        for (int i = 1; i <= 5; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query page 3 with size 1
        Page<User> page = new Page<>(3, 1);
        List<User> results = userMapper.selectPage(page, null);

        // Assert
        assertEquals(1, results.size(), "Should return 1 record");
        assertEquals(5, page.getTotal(), "Total should be 5");
        assertEquals(5, page.getPages(), "Should have 5 pages");
        assertEquals("user3", results.get(0).getUsername(), "Should be user3");
    }

    /**
     * Test pagination with large page size.
     */
    @Test
    void testPagination_LargePageSize() {
        // Arrange - insert 20 users
        for (int i = 1; i <= 20; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query with page size 100 (larger than total records)
        Page<User> page = new Page<>(1, 100);
        List<User> results = userMapper.selectPage(page, null);

        // Assert
        assertEquals(20, results.size(), "Should return all 20 records");
        assertEquals(20, page.getTotal(), "Total should be 20");
        assertEquals(1, page.getPages(), "Should have 1 page");
    }

    /**
     * Test that Page object is correctly updated with total count.
     */
    @Test
    void testPagination_PageObjectUpdated() {
        // Arrange - insert 35 users
        for (int i = 1; i <= 35; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act
        Page<User> page = new Page<>(2, 10);
        assertEquals(0, page.getTotal(), "Total should be 0 before query");

        List<User> results = userMapper.selectPage(page, null);

        // Assert - Page object should be updated
        assertEquals(35, page.getTotal(), "Total should be updated to 35");
        assertEquals(4, page.getPages(), "Pages should be calculated as 4");
        assertEquals(10, results.size(), "Should return 10 records");
    }
}
