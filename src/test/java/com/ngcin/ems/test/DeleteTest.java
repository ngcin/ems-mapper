package com.ngcin.ems.test;

import com.ngcin.ems.mapper.MapperException;
import com.ngcin.ems.mapper.core.KeyPropertyInterceptor;
import com.ngcin.ems.test.entity.Article;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.ArticleMapper;
import com.ngcin.ems.test.mapper.UserMapper;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for logical delete by entity conditions (delete method).
 */
public class DeleteTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;
    private ArticleMapper articleMapper;
    private UserMapper userMapper;

    @BeforeAll
    public static void setUpClass() throws Exception {
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
                        <property name="url" value="jdbc:h2:mem:testdb3;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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

        Configuration configuration = sqlSessionFactory.getConfiguration();
        configuration.addMapper(ArticleMapper.class);
        configuration.addMapper(UserMapper.class);
        configuration.addInterceptor(new KeyPropertyInterceptor());

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
        sqlSession = sqlSessionFactory.openSession(true);
        articleMapper = sqlSession.getMapper(ArticleMapper.class);
        userMapper = sqlSession.getMapper(UserMapper.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        sqlSession.getConnection().createStatement().execute("DELETE FROM t_article");
        sqlSession.getConnection().createStatement().execute("DELETE FROM t_user");
        sqlSession.close();
    }

    @Test
    public void testDelete_SingleCondition() {
        // Insert articles
        Article article1 = new Article();
        article1.setTitle("Java Tutorial");
        article1.setContent("Content 1");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Python Tutorial");
        article2.setContent("Content 2");
        articleMapper.insert(article2);

        // Delete by title condition
        Article query = new Article();
        query.setTitle("Java Tutorial");
        int result = articleMapper.delete(query);
        assertEquals(1, result);

        // Verify only matching article is deleted
        assertNull(articleMapper.getById(article1.getId()));
        assertNotNull(articleMapper.getById(article2.getId()));
    }

    @Test
    public void testDelete_MultipleConditions() {
        // Insert articles
        Article article1 = new Article();
        article1.setTitle("Java Tutorial");
        article1.setContent("Advanced");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Java Tutorial");
        article2.setContent("Beginner");
        articleMapper.insert(article2);

        // Delete by multiple conditions
        Article query = new Article();
        query.setTitle("Java Tutorial");
        query.setContent("Advanced");
        int result = articleMapper.delete(query);
        assertEquals(1, result);

        // Verify only matching article is deleted
        assertNull(articleMapper.getById(article1.getId()));
        assertNotNull(articleMapper.getById(article2.getId()));
    }

    @Test
    public void testDelete_NoMatchingRecords() {
        // Insert article
        Article article = new Article();
        article.setTitle("Java Tutorial");
        articleMapper.insert(article);

        // Delete with non-matching condition
        Article query = new Article();
        query.setTitle("Python Tutorial");
        int result = articleMapper.delete(query);
        assertEquals(0, result);

        // Verify article still exists
        assertNotNull(articleMapper.getById(article.getId()));
    }

    @Test
    public void testDelete_EntityWithoutSoftDelete_ThrowsException() {
        // Insert user
        User user = new User();
        user.setUsername("Test User");
        userMapper.insert(user);

        // Try to delete - should throw exception
        User query = new User();
        query.setUsername("Test User");
        Exception exception = assertThrows(Exception.class, () -> {
            userMapper.delete(query);
        });

        assertTrue(exception.getMessage().contains("does not support logical delete") ||
                   exception.getCause().getMessage().contains("does not support logical delete"));
    }

    @Test
    public void testDelete_MultipleMatchingRecords() {
        // Insert articles with same title
        Article article1 = new Article();
        article1.setTitle("Tutorial");
        article1.setContent("Content 1");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Tutorial");
        article2.setContent("Content 2");
        articleMapper.insert(article2);

        // Delete by title - should delete both
        Article query = new Article();
        query.setTitle("Tutorial");
        int result = articleMapper.delete(query);
        assertEquals(2, result);

        // Verify both are deleted
        assertNull(articleMapper.getById(article1.getId()));
        assertNull(articleMapper.getById(article2.getId()));
    }
}
