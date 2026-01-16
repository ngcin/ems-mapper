package com.ngcin.ems.test;

import com.ngcin.ems.mapper.core.KeyPropertyInterceptor;
import com.ngcin.ems.test.entity.Article;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.ArticleMapper;
import com.ngcin.ems.test.mapper.UserMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for remove by ID (removeById method).
 */
public class RemoveByIdTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;
    private ArticleMapper articleMapper;
    private UserMapper userMapper;

    @BeforeAll
    public static void setUpClass() throws Exception {
        // Create H2 in-memory database
        String mybatisConfig = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                  "http://mybatis.org/dtd/mybatis-3-config.dtd">
                <configuration>
                  <environments default="development">
                    <environment id="development">
                      <transactionManager type="JDBC"/>
                      <dataSource type="POOLED">
                        <property name="driver" value="org.h2.Driver"/>
                        <property name="url" value="jdbc:h2:mem:testdb2;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                        <property name="username" value="sa"/>
                        <property name="password" value=""/>
                      </dataSource>
                    </environment>
                  </environments>
                </configuration>
                """;

        InputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes(StandardCharsets.UTF_8));
        SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        sqlSessionFactory = builder.build(inputStream);

        // Register mappers and interceptor
        Configuration configuration = sqlSessionFactory.getConfiguration();
        configuration.addMapper(ArticleMapper.class);
        configuration.addMapper(UserMapper.class);
        configuration.addInterceptor(new KeyPropertyInterceptor());

        // Create tables
        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getConnection().createStatement().execute(
                    "CREATE TABLE t_article (id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                            "title VARCHAR(255), content TEXT, deleted INT DEFAULT 0)"
            );
            session.getConnection().createStatement().execute(
                    "CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                            "username VARCHAR(255), email VARCHAR(255), age INT, create_time TIMESTAMP)"
            );
        }
    }

    @BeforeEach
    public void setUp() {
        sqlSession = sqlSessionFactory.openSession(true); // auto-commit
        articleMapper = sqlSession.getMapper(ArticleMapper.class);
        userMapper = sqlSession.getMapper(UserMapper.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Clean up tables
        sqlSession.getConnection().createStatement().execute("DELETE FROM t_article");
        sqlSession.getConnection().createStatement().execute("DELETE FROM t_user");
        sqlSession.close();
    }

    @Test
    public void testRemoveById_Success() throws Exception {
        // Insert article
        Article article = new Article();
        article.setTitle("Test Article");
        article.setContent("Test Content");
        articleMapper.insert(article);

        Long id = article.getId();
        assertNotNull(id);

        // Remove by ID
        int result = articleMapper.removeById(id);
        assertEquals(1, result);

        // Verify article is completely removed from database
        Article deleted = articleMapper.getById(id);
        assertNull(deleted);

        // Verify record doesn't exist in database at all
        ResultSet rs = sqlSession.getConnection().createStatement()
                .executeQuery("SELECT COUNT(*) FROM t_article WHERE id = " + id);
        rs.next();
        assertEquals(0, rs.getInt(1));
    }

    @Test
    public void testRemoveById_UserWithoutSoftDelete() throws Exception {
        // Insert user (User entity doesn't have @Deleted field)
        User user = new User();
        user.setUsername("Test User");
        user.setEmail("test@example.com");
        userMapper.insert(user);

        Long id = user.getId();

        // Remove should work for entities without soft delete
        int result = userMapper.removeById(id);
        assertEquals(1, result);

        // Verify user is completely removed
        User deleted = userMapper.getById(id);
        assertNull(deleted);

        // Verify record doesn't exist in database
        ResultSet rs = sqlSession.getConnection().createStatement()
                .executeQuery("SELECT COUNT(*) FROM t_user WHERE id = " + id);
        rs.next();
        assertEquals(0, rs.getInt(1));
    }

    @Test
    public void testRemoveById_NonExistentId() {
        // Try to delete non-existent ID
        int result = articleMapper.removeById(99999L);
        assertEquals(0, result);
    }

    @Test
    public void testRemoveById_MultipleRecords() {
        // Insert multiple articles
        Article article1 = new Article();
        article1.setTitle("Article 1");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Article 2");
        articleMapper.insert(article2);

        // Remove first article
        int result = articleMapper.removeById(article1.getId());
        assertEquals(1, result);

        // Verify first article is removed
        assertNull(articleMapper.getById(article1.getId()));

        // Verify second article still exists
        assertNotNull(articleMapper.getById(article2.getId()));
    }
}
