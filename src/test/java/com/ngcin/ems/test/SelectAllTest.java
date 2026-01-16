package com.ngcin.ems.test;

import com.ngcin.ems.test.entity.Article;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.ArticleMapper;
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
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for selectAll method in BaseMapper.
 */
class SelectAllTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private UserMapper userMapper;
    private ArticleMapper articleMapper;

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
                                <property name="url" value="jdbc:h2:mem:testdb6;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.UserMapper"/>
                        <mapper class="com.ngcin.ems.test.mapper.ArticleMapper"/>
                    </mappers>
                </configuration>
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes());
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        // Initialize database schema
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();

            // Create t_user table (no soft delete)
            stmt.execute("CREATE TABLE t_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL, " +
                    "email VARCHAR(100), " +
                    "age INT, " +
                    "create_time TIMESTAMP)");

            // Create t_article table with soft delete
            stmt.execute("CREATE TABLE t_article (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(100) NOT NULL, " +
                    "content VARCHAR(500), " +
                    "deleted INT DEFAULT 0)");

            stmt.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    @BeforeEach
    void openSession() {
        session = sqlSessionFactory.openSession();
        userMapper = session.getMapper(UserMapper.class);
        articleMapper = session.getMapper(ArticleMapper.class);

        // Clean up tables before each test
        try {
            Connection conn = session.getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM t_article");
                stmt.execute("DELETE FROM t_user");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean up tables", e);
        }
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Test selectAll with entity without soft delete field.
     */
    @Test
    void testSelectAll_WithoutSoftDelete() {
        // Arrange - insert some users
        userMapper.insert(new User("user1", "user1@test.com", 20));
        session.commit();
        userMapper.insert(new User("user2", "user2@test.com", 30));
        session.commit();
        userMapper.insert(new User("user3", "user3@test.com", 40));
        session.commit();

        // Act
        List<User> users = userMapper.selectAll();

        // Assert
        assertNotNull(users, "User list should not be null");
        assertEquals(3, users.size(), "Should return 3 users");
        assertEquals("user1", users.get(0).getUsername());
        assertEquals("user2", users.get(1).getUsername());
        assertEquals("user3", users.get(2).getUsername());
    }

    /**
     * Test selectAll with entity with soft delete field - should filter deleted records.
     */
    @Test
    void testSelectAll_WithSoftDelete() {
        // Arrange - insert articles with mixed deleted status
        Article a1 = new Article("Title1", "Content1");
        articleMapper.insert(a1);
        session.commit();

        Article a2 = new Article("Title2", "Content2");
        articleMapper.insert(a2);
        session.commit();

        Article a3 = new Article("Title3", "Content3");
        articleMapper.insert(a3);
        session.commit();

        // Manually update a2 to deleted status
        try {
            Connection conn = session.getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE t_article SET deleted = 1 WHERE id = " + a2.getId());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update article", e);
        }

        // Act
        List<Article> articles = articleMapper.selectAll();

        // Assert
        assertNotNull(articles, "Article list should not be null");
        assertEquals(2, articles.size(), "Should only return non-deleted articles");
        assertEquals("Title1", articles.get(0).getTitle());
        assertEquals("Title3", articles.get(1).getTitle());
    }

    /**
     * Test selectAll returns empty list when no records.
     */
    @Test
    void testSelectAll_EmptyTable() {
        // Act
        List<User> users = userMapper.selectAll();

        // Assert
        assertNotNull(users, "User list should not be null");
        assertTrue(users.isEmpty(), "Should return empty list");
    }

    /**
     * Test selectAll limit - should not exceed 1000 records.
     */
    @Test
    void testSelectAll_Limit() {
        // Arrange - insert 1500 users (exceeds limit)
        for (int i = 1; i <= 1500; i++) {
            userMapper.insert(new User("user" + i, "user" + i + "@test.com", 20 + (i % 50)));
            session.commit();
        }

        // Act
        List<User> users = userMapper.selectAll();

        // Assert
        assertNotNull(users, "User list should not be null");
        assertEquals(1000, users.size(), "Should return max 1000 users");
    }

    /**
     * Test selectAll returns all fields correctly mapped.
     */
    @Test
    void testSelectAll_AllFieldsMapped() {
        // Arrange
        userMapper.insert(new User("testuser", "test@test.com", 25));
        session.commit();

        // Act
        List<User> users = userMapper.selectAll();

        // Assert
        assertNotNull(users, "User list should not be null");
        assertEquals(1, users.size());
        User user = users.get(0);
        assertNotNull(user.getId(), "ID should be set");
        assertNotNull(user.getUsername(), "Username should be set");
        assertNotNull(user.getEmail(), "Email should be set");
        assertNotNull(user.getAge(), "Age should be set");
        assertNotNull(user.getCreateTime(), "CreateTime should be set");
    }

    /**
     * Test selectAll with soft delete - all records deleted should return empty list.
     */
    @Test
    void testSelectAll_AllDeleted() {
        // Arrange - insert and mark all as deleted
        Article a1 = new Article("Title1", "Content1");
        articleMapper.insert(a1);
        session.commit();

        Article a2 = new Article("Title2", "Content2");
        articleMapper.insert(a2);
        session.commit();

        // Mark all as deleted
        try {
            Connection conn = session.getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE t_article SET deleted = 1");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update articles", e);
        }

        // Act
        List<Article> articles = articleMapper.selectAll();

        // Assert
        assertNotNull(articles, "Article list should not be null");
        assertTrue(articles.isEmpty(), "Should return empty list when all records are deleted");
    }
}
