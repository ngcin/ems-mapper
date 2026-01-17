package com.ngcin.ems.test;

import com.ngcin.ems.mapper.IPage;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the page() method in BaseMapper.
 *
 * <p>This test class verifies that the page() method correctly:
 * <ul>
 *   <li>Returns an IPage object with records populated</li>
 *   <li>Sets the total count on the Page object</li>
 *   <li>Calculates the correct number of pages</li>
 *   <li>Works with and without query conditions</li>
 * </ul>
 */
class PageMethodTest {

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
                                <property name="url" value="jdbc:h2:mem:testdb_page_method;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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
     * Test page() method returns IPage with records populated.
     */
    @Test
    void testPage_ReturnsIPageWithRecords() {
        // Arrange - insert 25 users
        for (int i = 1; i <= 25; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query first page using page() method
        Page<User> page = new Page<>(1, 10);
        IPage<User> result = userMapper.page(page, null);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertSame(page, result, "Should return the same page object");
        assertNotNull(result.getRecords(), "Records should not be null");
        assertEquals(10, result.getRecords().size(), "Should return 10 records");
        assertEquals(25, result.getTotal(), "Total should be 25");
        assertEquals(3, result.getPages(), "Should have 3 pages");
    }

    /**
     * Test page() method with query conditions.
     */
    @Test
    void testPage_WithQueryConditions() {
        // Arrange - insert users with different ages
        for (int i = 1; i <= 30; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + (i % 3));
            userMapper.insert(user);
        }
        session.commit();

        // Act - query users with age = 21
        User queryUser = new User();
        queryUser.setAge(21);
        Page<User> page = new Page<>(1, 5);
        IPage<User> result = userMapper.page(page, queryUser);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(5, result.getRecords().size(), "Should return 5 records");
        assertEquals(10, result.getTotal(), "Total should be 10 (users with age 21)");
        assertEquals(2, result.getPages(), "Should have 2 pages");

        // Verify all results have age = 21
        for (User user : result.getRecords()) {
            assertEquals(21, user.getAge(), "All users should have age 21");
        }
    }

    /**
     * Test page() method with second page.
     */
    @Test
    void testPage_SecondPage() {
        // Arrange - insert 25 users
        for (int i = 1; i <= 25; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query second page
        Page<User> page = new Page<>(2, 10);
        IPage<User> result = userMapper.page(page, null);

        // Assert
        assertEquals(10, result.getRecords().size(), "Should return 10 records");
        assertEquals(25, result.getTotal(), "Total should be 25");
        assertEquals(2, result.getCurrent(), "Current page should be 2");
        assertEquals("user11", result.getRecords().get(0).getUsername(), "First user on page 2 should be user11");
    }

    /**
     * Test page() method with empty results.
     */
    @Test
    void testPage_EmptyResults() {
        // Arrange - no users inserted

        // Act
        Page<User> page = new Page<>(1, 10);
        IPage<User> result = userMapper.page(page, null);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getRecords(), "Records should not be null");
        assertEquals(0, result.getRecords().size(), "Should return 0 records");
        assertEquals(0, result.getTotal(), "Total should be 0");
        assertEquals(0, result.getPages(), "Should have 0 pages");
    }

    /**
     * Test page() method with last page having partial results.
     */
    @Test
    void testPage_LastPagePartial() {
        // Arrange - insert 23 users
        for (int i = 1; i <= 23; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query third page (should have 3 records)
        Page<User> page = new Page<>(3, 10);
        IPage<User> result = userMapper.page(page, null);

        // Assert
        assertEquals(3, result.getRecords().size(), "Should return 3 records on last page");
        assertEquals(23, result.getTotal(), "Total should be 23");
        assertEquals(3, result.getPages(), "Should have 3 pages");
    }

    /**
     * Test page() method with different page sizes.
     */
    @Test
    void testPage_DifferentPageSizes() {
        // Arrange - insert 50 users
        for (int i = 1; i <= 50; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act & Assert - page size 5
        Page<User> page5 = new Page<>(1, 5);
        IPage<User> result5 = userMapper.page(page5, null);
        assertEquals(5, result5.getRecords().size(), "Should return 5 records");
        assertEquals(50, result5.getTotal(), "Total should be 50");
        assertEquals(10, result5.getPages(), "Should have 10 pages with size 5");

        // Act & Assert - page size 20
        Page<User> page20 = new Page<>(1, 20);
        IPage<User> result20 = userMapper.page(page20, null);
        assertEquals(20, result20.getRecords().size(), "Should return 20 records");
        assertEquals(50, result20.getTotal(), "Total should be 50");
        assertEquals(3, result20.getPages(), "Should have 3 pages with size 20");
    }

    /**
     * Test page() method returns the same page object that was passed in.
     */
    @Test
    void testPage_ReturnsSamePageObject() {
        // Arrange - insert 15 users
        for (int i = 1; i <= 15; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act
        Page<User> page = new Page<>(1, 10);
        IPage<User> result = userMapper.page(page, null);

        // Assert - should return the same object reference
        assertSame(page, result, "Should return the same page object");
        assertEquals(10, page.getRecords().size(), "Original page object should have records set");
        assertEquals(15, page.getTotal(), "Original page object should have total set");
    }

    /**
     * Test page() method with single record.
     */
    @Test
    void testPage_SingleRecord() {
        // Arrange - insert 1 user
        User user = new User("user1", "user1@test.com", 25);
        userMapper.insert(user);
        session.commit();

        // Act
        Page<User> page = new Page<>(1, 10);
        IPage<User> result = userMapper.page(page, null);

        // Assert
        assertEquals(1, result.getRecords().size(), "Should return 1 record");
        assertEquals(1, result.getTotal(), "Total should be 1");
        assertEquals(1, result.getPages(), "Should have 1 page");
        assertEquals("user1", result.getRecords().get(0).getUsername(), "Should be user1");
    }

    /**
     * Test page() method with complex query conditions.
     */
    @Test
    void testPage_ComplexQueryConditions() {
        // Arrange - insert users with specific patterns
        for (int i = 1; i <= 40; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + (i % 5));
            userMapper.insert(user);
        }
        session.commit();

        // Act - query users with age = 22, second page
        User queryUser = new User();
        queryUser.setAge(22);
        Page<User> page = new Page<>(2, 3);
        IPage<User> result = userMapper.page(page, queryUser);

        // Assert
        assertEquals(3, result.getRecords().size(), "Should return 3 records");
        assertEquals(8, result.getTotal(), "Total should be 8 (users with age 22)");
        assertEquals(3, result.getPages(), "Should have 3 pages");
        assertEquals(2, result.getCurrent(), "Current page should be 2");

        // Verify all results have age = 22
        for (User user : result.getRecords()) {
            assertEquals(22, user.getAge(), "All users should have age 22");
        }
    }

    /**
     * Test that page() method properly integrates with PaginationInterceptor.
     */
    @Test
    void testPage_IntegrationWithPaginationInterceptor() {
        // Arrange - insert 100 users
        for (int i = 1; i <= 100; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // Act - query multiple pages
        Page<User> page1 = new Page<>(1, 10);
        IPage<User> result1 = userMapper.page(page1, null);

        Page<User> page5 = new Page<>(5, 10);
        IPage<User> result5 = userMapper.page(page5, null);

        Page<User> page10 = new Page<>(10, 10);
        IPage<User> result10 = userMapper.page(page10, null);

        // Assert - all pages should have correct total
        assertEquals(100, result1.getTotal(), "Page 1 should have total 100");
        assertEquals(100, result5.getTotal(), "Page 5 should have total 100");
        assertEquals(100, result10.getTotal(), "Page 10 should have total 100");

        // Assert - all pages should have correct page count
        assertEquals(10, result1.getPages(), "Should have 10 pages");
        assertEquals(10, result5.getPages(), "Should have 10 pages");
        assertEquals(10, result10.getPages(), "Should have 10 pages");

        // Assert - records should be different for each page
        assertNotEquals(result1.getRecords().get(0).getUsername(),
                result5.getRecords().get(0).getUsername(),
                "Page 1 and Page 5 should have different first records");
    }
}
