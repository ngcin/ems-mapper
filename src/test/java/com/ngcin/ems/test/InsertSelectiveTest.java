package com.ngcin.ems.test;

import com.ngcin.ems.mapper.core.KeyPropertyInterceptor;
import com.ngcin.ems.test.entity.Product;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.ProductMapper;
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
 * Test cases for insertSelective method.
 */
public class InsertSelectiveTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;
    private UserMapper userMapper;
    private ProductMapper productMapper;

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
                        <property name="url" value="jdbc:h2:mem:testdb_insertselective;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(ProductMapper.class);
        configuration.addInterceptor(new KeyPropertyInterceptor());

        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getConnection().createStatement().execute(
                    "CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                            "username VARCHAR(255), email VARCHAR(255), age INT, create_time TIMESTAMP)"
            );
            session.getConnection().createStatement().execute(
                    "CREATE TABLE t_product (product_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                            "product_name VARCHAR(255), price DECIMAL(10,2))"
            );
        }
    }

    @BeforeEach
    public void setUp() {
        sqlSession = sqlSessionFactory.openSession(true);
        userMapper = sqlSession.getMapper(UserMapper.class);
        productMapper = sqlSession.getMapper(ProductMapper.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        sqlSession.getConnection().createStatement().execute("DELETE FROM t_user");
        sqlSession.getConnection().createStatement().execute("DELETE FROM t_product");
        sqlSession.close();
    }

    @Test
    public void testInsertSelective_SkipNullFields() {
        // Create user with only some fields set
        User user = new User();
        user.setUsername("Alice");
        user.setEmail("alice@example.com");
        // age is null - should not be inserted

        int result = userMapper.insertSelective(user);
        assertEquals(1, result);

        // Verify ID was generated
        assertNotNull(user.getId());

        // Verify only non-null fields were inserted
        User inserted = userMapper.getById(user.getId());
        assertEquals("Alice", inserted.getUsername());
        assertEquals("alice@example.com", inserted.getEmail());
        assertNull(inserted.getAge()); // Should be null (not inserted)
    }

    @Test
    public void testInsertSelective_WithAllFields() {
        // Create user with all fields set
        User user = new User();
        user.setUsername("Bob");
        user.setEmail("bob@example.com");
        user.setAge(30);

        int result = userMapper.insertSelective(user);
        assertEquals(1, result);

        // Verify all fields were inserted
        User inserted = userMapper.getById(user.getId());
        assertEquals("Bob", inserted.getUsername());
        assertEquals("bob@example.com", inserted.getEmail());
        assertEquals(30, inserted.getAge());
    }

    @Test
    public void testInsertSelective_OnlyRequiredFields() {
        // Create user with only username (minimum required)
        User user = new User();
        user.setUsername("Charlie");
        // email and age are null

        int result = userMapper.insertSelective(user);
        assertEquals(1, result);

        // Verify only username was inserted
        User inserted = userMapper.getById(user.getId());
        assertEquals("Charlie", inserted.getUsername());
        assertNull(inserted.getEmail());
        assertNull(inserted.getAge());
    }
}
