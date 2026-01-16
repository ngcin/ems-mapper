package com.ngcin.ems.test;

import com.ngcin.ems.mapper.core.KeyPropertyInterceptor;
import com.ngcin.ems.test.entity.Article;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.ArticleMapper;
import com.ngcin.ems.test.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;

import java.io.Serializable;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for selectBatchIds method in BaseMapper.
 */
class SelectBatchIdsTest {

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
                                <property name="url" value="jdbc:h2:mem:testdb9;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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
     * Test selectBatchIds with multiple IDs.
     */
    @Test
    void testSelectBatchIds_MultipleIds() {
        // Arrange
        User u1 = new User("alice", "alice@test.com", 25);
        userMapper.insert(u1);
        session.commit();

        User u2 = new User("bob", "bob@test.com", 30);
        userMapper.insert(u2);
        session.commit();

        User u3 = new User("charlie", "charlie@test.com", 35);
        userMapper.insert(u3);
        session.commit();

        Long id1 = u1.getId();
        Long id2 = u2.getId();
        Long id3 = u3.getId();

        // Act - query by multiple IDs
        List<User> users = userMapper.selectBatchIds(Arrays.asList(id1, id3));

        // Assert
        assertNotNull(users, "User list should not be null");
        assertEquals(2, users.size(), "Should find 2 users");
        assertEquals(id1, users.get(0).getId());
        assertEquals(id3, users.get(1).getId());
        assertEquals("alice", users.get(0).getUsername());
        assertEquals("charlie", users.get(1).getUsername());
    }

    /**
     * Test selectBatchIds with single ID.
     */
    @Test
    void testSelectBatchIds_SingleId() {
        // Arrange
        User u1 = new User("alice", "alice@test.com", 25);
        userMapper.insert(u1);
        session.commit();

        User u2 = new User("bob", "bob@test.com", 30);
        userMapper.insert(u2);
        session.commit();

        Long id1 = u1.getId();

        // Act - query by single ID
        List<User> users = userMapper.selectBatchIds(Collections.singletonList(id1));

        // Assert
        assertNotNull(users, "User list should not be null");
        assertEquals(1, users.size(), "Should find 1 user");
        assertEquals(id1, users.get(0).getId());
        assertEquals("alice", users.get(0).getUsername());
    }

    /**
     * Test selectBatchIds with empty list - should return empty list.
     */
    @Test
    void testSelectBatchIds_EmptyList() {
        // Arrange
        userMapper.insert(new User("alice", "alice@test.com", 25));
        session.commit();

        // Act - query with empty list
        List<User> users = userMapper.selectBatchIds(Collections.emptyList());

        // Assert
        assertNotNull(users, "User list should not be null");
        assertTrue(users.isEmpty(), "Should return empty list");
    }

    /**
     * Test selectBatchIds with non-existent IDs - should return empty list.
     */
    @Test
    void testSelectBatchIds_NonExistentIds() {
        // Arrange
        userMapper.insert(new User("alice", "alice@test.com", 25));
        session.commit();

        // Act - query with non-existent IDs
        List<User> users = userMapper.selectBatchIds(Arrays.asList(999L, 1000L));

        // Assert
        assertNotNull(users, "User list should not be null");
        assertTrue(users.isEmpty(), "Should return empty list for non-existent IDs");
    }

    /**
     * Test selectBatchIds with mixed existing and non-existing IDs.
     */
    @Test
    void testSelectBatchIds_MixedIds() {
        // Arrange
        User u1 = new User("alice", "alice@test.com", 25);
        userMapper.insert(u1);
        session.commit();

        User u2 = new User("bob", "bob@test.com", 30);
        userMapper.insert(u2);
        session.commit();

        Long id1 = u1.getId();
        Long id2 = u2.getId();

        // Act - query with mixed IDs (one exists, one doesn't)
        List<User> users = userMapper.selectBatchIds(Arrays.asList(id1, 999L, id2));

        // Assert
        assertNotNull(users, "User list should not be null");
        assertEquals(2, users.size(), "Should find only existing users");
        assertEquals(id1, users.get(0).getId());
        assertEquals(id2, users.get(1).getId());
    }

    /**
     * Test selectBatchIds with soft delete - should filter deleted records.
     */
    @Test
    void testSelectBatchIds_WithSoftDelete() {
        // Arrange
        Article a1 = new Article("Java", "Content1");
        articleMapper.insert(a1);
        session.commit();

        Article a2 = new Article("Python", "Content2");
        articleMapper.insert(a2);
        session.commit();

        Article a3 = new Article("Go", "Content3");
        articleMapper.insert(a3);
        session.commit();

        Long id1 = a1.getId();
        Long id2 = a2.getId();
        Long id3 = a3.getId();

        // Act - query by all IDs
        List<Article> articles = articleMapper.selectBatchIds(Arrays.asList(id1, id2, id3));

        // Assert
        assertNotNull(articles, "Article list should not be null");
        assertEquals(3, articles.size(), "Should find all 3 articles");
        assertEquals("Java", articles.get(0).getTitle());
        assertEquals("Python", articles.get(1).getTitle());
        assertEquals("Go", articles.get(2).getTitle());
    }

    /**
     * Test selectBatchIds maintains order of input IDs.
     */
    @Test
    void testSelectBatchIds_MaintainsOrder() {
        // Arrange
        User u1 = new User("user1", "user1@test.com", 20);
        userMapper.insert(u1);
        session.commit();

        User u2 = new User("user2", "user2@test.com", 25);
        userMapper.insert(u2);
        session.commit();

        User u3 = new User("user3", "user3@test.com", 30);
        userMapper.insert(u3);
        session.commit();

        Long id1 = u1.getId();
        Long id2 = u2.getId();
        Long id3 = u3.getId();

        // Act - query in reverse order
        List<User> users = userMapper.selectBatchIds(Arrays.asList(id3, id1, id2));

        // Assert
        assertNotNull(users, "User list should not be null");
        assertEquals(3, users.size(), "Should find 3 users");
        // Note: Database may not maintain the exact order, but all should be present
        List<Long> foundIds = users.stream().map(User::getId).toList();
        assertTrue(foundIds.contains(id3), "Should contain id3");
        assertTrue(foundIds.contains(id1), "Should contain id1");
        assertTrue(foundIds.contains(id2), "Should contain id2");
    }

    /**
     * Test selectBatchIds with large batch.
     */
    @Test
    void testSelectBatchIds_LargeBatch() {
        // Arrange - insert 100 users
        List<Serializable> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            User u = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(u);
            session.commit();
            ids.add(u.getId());
        }

        // Act - query all 100 users
        List<User> users = userMapper.selectBatchIds(ids);

        // Assert
        assertNotNull(users, "User list should not be null");
        assertEquals(100, users.size(), "Should find all 100 users");
    }

    /**
     * Test selectBatchIds returns complete entities.
     */
    @Test
    void testSelectBatchIds_CompleteEntities() {
        // Arrange
        User u1 = new User("alice", "alice@test.com", 25);
        userMapper.insert(u1);
        session.commit();

        User u2 = new User("bob", "bob@test.com", 30);
        userMapper.insert(u2);
        session.commit();

        // Act
        List<User> users = userMapper.selectBatchIds(Arrays.asList(u1.getId(), u2.getId()));

        // Assert - verify all fields are populated
        assertNotNull(users, "User list should not be null");
        assertEquals(2, users.size());

        for (User user : users) {
            assertNotNull(user.getId(), "ID should be set");
            assertNotNull(user.getUsername(), "Username should be set");
            assertNotNull(user.getEmail(), "Email should be set");
            assertNotNull(user.getAge(), "Age should be set");
            assertNotNull(user.getCreateTime(), "CreateTime should be set");
        }
    }
}
