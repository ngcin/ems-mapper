package com.ngcin.ems.test;

import com.ngcin.ems.mapper.MapperException;
import com.ngcin.ems.mapper.core.KeyPropertyInterceptor;
import com.ngcin.ems.test.entity.Article;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.ArticleMapper;
import com.ngcin.ems.test.mapper.UserMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for logical delete by ID (deleteById method).
 */
public class DeleteByIdTest {

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
                        <property name="url" value="jdbc:h2:mem:testdb_deletebyid;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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
    public void testDeleteById_Success() {
        // Insert article
        Article article = new Article();
        article.setTitle("Test Article");
        article.setContent("Test Content");
        articleMapper.insert(article);

        Long id = article.getId();
        assertNotNull(id);

        // Verify article exists and not deleted
        Article found = articleMapper.getById(id);
        assertNotNull(found);
        assertEquals(0, found.getDeleted());

        // Delete by ID
        int result = articleMapper.deleteById(id);
        assertEquals(1, result);

        // Verify article is logically deleted (not returned by getById)
        Article deleted = articleMapper.getById(id);
        assertNull(deleted);

        // Verify article still exists in database but deleted=1
        Article directQuery = sqlSession.selectOne(
                "com.ngcin.ems.test.mapper.ArticleMapper.getById",
                id
        );
        assertNull(directQuery); // Should be null because getById filters deleted records
    }

    @Test
    public void testDeleteById_MultipleRecords() {
        // Insert multiple articles
        Article article1 = new Article();
        article1.setTitle("Article 1");
        article1.setContent("Content 1");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Article 2");
        article2.setContent("Content 2");
        articleMapper.insert(article2);

        // Delete first article
        int result = articleMapper.deleteById(article1.getId());
        assertEquals(1, result);

        // Verify first article is deleted
        assertNull(articleMapper.getById(article1.getId()));

        // Verify second article still exists
        Article found = articleMapper.getById(article2.getId());
        assertNotNull(found);
        assertEquals("Article 2", found.getTitle());
    }

    @Test
    public void testDeleteById_NonExistentId() {
        // Try to delete non-existent ID
        int result = articleMapper.deleteById(99999L);
        assertEquals(0, result);
    }

    @Test
    public void testDeleteById_AlreadyDeleted() {
        // Insert and delete article
        Article article = new Article();
        article.setTitle("Test Article");
        article.setContent("Test Content");
        articleMapper.insert(article);

        Long id = article.getId();
        articleMapper.deleteById(id);

        // Try to delete again
        int result = articleMapper.deleteById(id);
        assertEquals(0, result); // Should return 0 because already deleted
    }

    @Test
    public void testDeleteById_EntityWithoutSoftDelete_ThrowsException() {
        // Insert user (User entity doesn't have @Deleted field)
        User user = new User();
        user.setUsername("Test User");
        user.setEmail("test@example.com");
        userMapper.insert(user);

        Long id = user.getId();

        // Try to delete by ID - should throw exception
        Exception exception = assertThrows(Exception.class, () -> {
            userMapper.deleteById(id);
        });

        assertTrue(exception.getMessage().contains("does not support logical delete") ||
                   exception.getCause().getMessage().contains("does not support logical delete"));
        assertTrue(exception.getMessage().contains("Use hardDelete instead") ||
                   exception.getCause().getMessage().contains("Use hardDeleteById instead"));
    }

    @Test
    public void testDeleteById_SelectAllExcludesDeleted() {
        // Insert multiple articles
        Article article1 = new Article();
        article1.setTitle("Article 1");
        articleMapper.insert(article1);

        Article article2 = new Article();
        article2.setTitle("Article 2");
        articleMapper.insert(article2);

        Article article3 = new Article();
        article3.setTitle("Article 3");
        articleMapper.insert(article3);

        // Delete one article
        articleMapper.deleteById(article2.getId());

        // selectAll should only return non-deleted articles
        var articles = articleMapper.selectAll();
        assertEquals(2, articles.size());
        assertTrue(articles.stream().noneMatch(a -> a.getId().equals(article2.getId())));
    }
}
