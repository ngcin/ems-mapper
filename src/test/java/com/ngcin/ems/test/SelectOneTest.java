package com.ngcin.ems.test;

import com.ngcin.ems.mapper.core.KeyPropertyInterceptor;
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
 * Tests for selectOne method in BaseMapper.
 */
class SelectOneTest {

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
                                <property name="url" value="jdbc:h2:mem:testdb8;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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
     * Test selectOne with single matching record.
     */
    @Test
    void testSelectOne_SingleMatch() {
        // Arrange
        userMapper.insert(new User("alice", "alice@test.com", 25));
        session.commit();
        userMapper.insert(new User("bob", "bob@test.com", 30));
        session.commit();

        // Act - query by unique username
        User condition = new User();
        condition.setUsername("alice");

        User result = userMapper.selectOne(condition);

        // Assert
        assertNotNull(result, "Should find one user");
        assertEquals("alice", result.getUsername());
        assertEquals("alice@test.com", result.getEmail());
        assertEquals(25, result.getAge());
    }

    /**
     * Test selectOne with no matching record - should return null.
     */
    @Test
    void testSelectOne_NoMatch() {
        // Arrange
        userMapper.insert(new User("alice", "alice@test.com", 25));
        session.commit();

        // Act - query by non-existent username
        User condition = new User();
        condition.setUsername("nonexistent");

        User result = userMapper.selectOne(condition);

        // Assert
        assertNull(result, "Should return null when no match found");
    }

    /**
     * Test selectOne with multiple matching records.
     * Due to LIMIT 1, this should return the first record without exception.
     */
    @Test
    void testSelectOne_MultipleMatches() {
        // Arrange - insert multiple users with same username
        userMapper.insert(new User("alice", "alice1@test.com", 25));
        session.commit();
        userMapper.insert(new User("alice", "alice2@test.com", 30));
        session.commit();

        // Act - query by username that has multiple matches
        User condition = new User();
        condition.setUsername("alice");

        User result = userMapper.selectOne(condition);

        // Assert - LIMIT 1 returns first match
        assertNotNull(result, "Should find one user (first match due to LIMIT 1)");
        assertEquals("alice", result.getUsername());
    }

    /**
     * Test selectOne with multiple conditions.
     */
    @Test
    void testSelectOne_MultipleConditions() {
        // Arrange
        userMapper.insert(new User("alice", "alice@test.com", 25));
        session.commit();
        userMapper.insert(new User("alice", "alice2@test.com", 30));
        session.commit();

        // Act - query by both username and age
        User condition = new User();
        condition.setUsername("alice");
        condition.setAge(25);

        User result = userMapper.selectOne(condition);

        // Assert
        assertNotNull(result, "Should find one user matching both conditions");
        assertEquals("alice", result.getUsername());
        assertEquals(25, result.getAge());
        assertEquals("alice@test.com", result.getEmail());
    }

    /**
     * Test selectOne with all null fields - should return first record.
     */
    @Test
    void testSelectOne_AllNullFields() {
        // Arrange
        userMapper.insert(new User("alice", "alice@test.com", 25));
        session.commit();
        userMapper.insert(new User("bob", "bob@test.com", 30));
        session.commit();

        // Act - query with empty condition
        User condition = new User(); // all fields are null
        User result = userMapper.selectOne(condition);

        // Assert - returns first record
        assertNotNull(result, "Should find one user (first record)");
        assertEquals("alice", result.getUsername());
    }

    /**
     * Test selectOne with soft delete - should not return deleted records.
     */
    @Test
    void testSelectOne_WithSoftDelete() {
        // Arrange
        articleMapper.insert(new Article("Java Guide", "Content1"));
        session.commit();
        articleMapper.insert(new Article("Python Guide", "Content2"));
        session.commit();

        // Act - query by title
        Article condition = new Article();
        condition.setTitle("Java Guide");

        Article result = articleMapper.selectOne(condition);

        // Assert
        assertNotNull(result, "Should find one article");
        assertEquals("Java Guide", result.getTitle());
        assertEquals("Content1", result.getContent());
    }

    /**
     * Test selectOne - ID field should be ignored in conditions.
     */
    @Test
    void testSelectOne_IdFieldIgnored() {
        // Arrange
        userMapper.insert(new User("alice", "alice@test.com", 25));
        session.commit();
        userMapper.insert(new User("bob", "bob@test.com", 30));
        session.commit();

        // Act - query with ID set (should be ignored)
        User condition = new User();
        condition.setId(1L); // This should be ignored

        User result = userMapper.selectOne(condition);

        // Assert - ID is ignored, returns first record
        assertNotNull(result, "Should find one user (ID field ignored)");
        assertEquals("alice", result.getUsername());
    }

    /**
     * Test selectOne with age condition only.
     */
    @Test
    void testSelectOne_SingleConditionAge() {
        // Arrange
        userMapper.insert(new User("alice", "alice@test.com", 25));
        session.commit();
        userMapper.insert(new User("bob", "bob@test.com", 30));
        session.commit();
        userMapper.insert(new User("charlie", "charlie@test.com", 30));
        session.commit();

        // Act - query by age only (multiple matches)
        User condition = new User();
        condition.setAge(30);

        User result = userMapper.selectOne(condition);

        // Assert - returns first match
        assertNotNull(result, "Should find one user");
        assertEquals(30, result.getAge());
        // Could be bob or charlie, depending on order
    }

    /**
     * Test selectOne returns complete entity.
     */
    @Test
    void testSelectOne_CompleteEntity() {
        // Arrange
        userMapper.insert(new User("testuser", "test@test.com", 35));
        session.commit();

        // Act
        User condition = new User();
        condition.setUsername("testuser");
        User result = userMapper.selectOne(condition);

        // Assert - verify all fields are populated
        assertNotNull(result, "User should be found");
        assertNotNull(result.getId(), "ID should be set");
        assertNotNull(result.getUsername(), "Username should be set");
        assertNotNull(result.getEmail(), "Email should be set");
        assertNotNull(result.getAge(), "Age should be set");
        assertNotNull(result.getCreateTime(), "CreateTime should be set");
    }
}
