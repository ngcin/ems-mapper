package com.ngcin.ems.test;

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
 * Test cases for selectCount method.
 */
public class SelectCountTest {

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
                        <property name="url" value="jdbc:h2:mem:testdb_selectcount;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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
    public void testSelectCount_EmptyTable() {
        // Count with empty table
        Article query = new Article();
        long count = articleMapper.selectCount(query);
        assertEquals(0, count);
    }

    @Test
    public void testSelectCount_AllRecords() {
        // Insert multiple articles
        for (int i = 1; i <= 5; i++) {
            Article article = new Article();
            article.setTitle("Article " + i);
            article.setContent("Content " + i);
            articleMapper.insert(article);
        }

        // Count all records
        Article query = new Article();
        long count = articleMapper.selectCount(query);
        assertEquals(5, count);
    }

    @Test
    public void testSelectCount_WithSingleCondition() {
        // Insert articles with different titles
        Article article1 = new Article();
        article1.setTitle("Java Tutorial");
        article1.setContent("Content 1");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Python Tutorial");
        article2.setContent("Content 2");
        articleMapper.insert(article2);

        Article article3 = new Article();
        article3.setTitle("Java Tutorial");
        article3.setContent("Content 3");
        articleMapper.insert(article3);

        // Count articles with title "Java Tutorial"
        Article query = new Article();
        query.setTitle("Java Tutorial");
        long count = articleMapper.selectCount(query);
        assertEquals(2, count);
    }

    @Test
    public void testSelectCount_WithMultipleConditions() {
        // Insert articles
        Article article1 = new Article();
        article1.setTitle("Java Tutorial");
        article1.setContent("Advanced");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Java Tutorial");
        article2.setContent("Beginner");
        articleMapper.insert(article2);

        Article article3 = new Article();
        article3.setTitle("Python Tutorial");
        article3.setContent("Advanced");
        articleMapper.insert(article3);

        // Count with multiple conditions
        Article query = new Article();
        query.setTitle("Java Tutorial");
        query.setContent("Advanced");
        long count = articleMapper.selectCount(query);
        assertEquals(1, count);
    }

    @Test
    public void testSelectCount_NoMatchingRecords() {
        // Insert articles
        Article article1 = new Article();
        article1.setTitle("Java Tutorial");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Python Tutorial");
        articleMapper.insert(article2);

        // Count with non-matching condition
        Article query = new Article();
        query.setTitle("Ruby Tutorial");
        long count = articleMapper.selectCount(query);
        assertEquals(0, count);
    }

    @Test
    public void testSelectCount_ExcludesDeletedRecords() {
        // Insert articles
        Article article1 = new Article();
        article1.setTitle("Java Tutorial");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Python Tutorial");
        articleMapper.insert(article2);

        Article article3 = new Article();
        article3.setTitle("Ruby Tutorial");
        articleMapper.insert(article3);

        // Delete one article
        articleMapper.deleteById(article2.getId());

        // Count all - should exclude deleted
        Article query = new Article();
        long count = articleMapper.selectCount(query);
        assertEquals(2, count);
    }

    @Test
    public void testSelectCount_WithConditionExcludesDeleted() {
        // Insert articles with same title
        Article article1 = new Article();
        article1.setTitle("Java Tutorial");
        article1.setContent("Content 1");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Java Tutorial");
        article2.setContent("Content 2");
        articleMapper.insert(article2);

        Article article3 = new Article();
        article3.setTitle("Java Tutorial");
        article3.setContent("Content 3");
        articleMapper.insert(article3);

        // Delete one article
        articleMapper.deleteById(article2.getId());

        // Count with title condition - should exclude deleted
        Article query = new Article();
        query.setTitle("Java Tutorial");
        long count = articleMapper.selectCount(query);
        assertEquals(2, count);
    }

    @Test
    public void testSelectCount_EntityWithoutSoftDelete() {
        // Insert users (User entity doesn't have @Deleted field)
        User user1 = new User();
        user1.setUsername("Alice");
        user1.setEmail("alice@example.com");
        userMapper.insert(user1);

        User user2 = new User();
        user2.setUsername("Bob");
        user2.setEmail("bob@example.com");
        userMapper.insert(user2);

        // Count all users
        User query = new User();
        long count = userMapper.selectCount(query);
        assertEquals(2, count);
    }

    @Test
    public void testSelectCount_LargeDataset() {
        // Insert 100 articles
        for (int i = 1; i <= 100; i++) {
            Article article = new Article();
            article.setTitle("Article " + i);
            article.setContent("Content " + i);
            articleMapper.insert(article);
        }

        // Count all
        Article query = new Article();
        long count = articleMapper.selectCount(query);
        assertEquals(100, count);
    }
}
