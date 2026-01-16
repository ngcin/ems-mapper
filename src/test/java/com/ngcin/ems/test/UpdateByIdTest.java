package com.ngcin.ems.test;

import com.ngcin.ems.mapper.core.KeyPropertyInterceptor;
import com.ngcin.ems.test.entity.ProductV2;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.ProductV2Mapper;
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
 * Test cases for updateById method with version control support.
 */
public class UpdateByIdTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;
    private ProductV2Mapper productV2Mapper;
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
                        <property name="url" value="jdbc:h2:mem:testdb_updatebyid;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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
        configuration.addMapper(ProductV2Mapper.class);
        configuration.addMapper(UserMapper.class);
        configuration.addInterceptor(new KeyPropertyInterceptor());

        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getConnection().createStatement().execute(
                    "CREATE TABLE t_product_v2 (product_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                            "product_name VARCHAR(255), price DECIMAL(10,2), version INT DEFAULT 0)"
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
        productV2Mapper = sqlSession.getMapper(ProductV2Mapper.class);
        userMapper = sqlSession.getMapper(UserMapper.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        sqlSession.getConnection().createStatement().execute("DELETE FROM t_product_v2");
        sqlSession.getConnection().createStatement().execute("DELETE FROM t_user");
        sqlSession.close();
    }

    @Test
    public void testUpdateById_WithoutVersion_Success() {
        // Insert user (no version field)
        User user = new User();
        user.setUsername("Alice");
        user.setEmail("alice@example.com");
        user.setAge(25);
        userMapper.insert(user);

        Long id = user.getId();
        assertNotNull(id);

        // Update user
        user.setUsername("Alice Updated");
        user.setEmail("alice.updated@example.com");
        user.setAge(26);
        int result = userMapper.updateById(user);
        assertEquals(1, result);

        // Verify update
        User updated = userMapper.getById(id);
        assertEquals("Alice Updated", updated.getUsername());
        assertEquals("alice.updated@example.com", updated.getEmail());
        assertEquals(26, updated.getAge());
    }

    @Test
    public void testUpdateById_WithVersion_Success() {
        // Insert product with version
        ProductV2 product = new ProductV2();
        product.setProductName("Laptop");
        product.setPrice(999.99);
        productV2Mapper.insert(product);

        Long id = product.getProductId();
        assertNotNull(id);

        // Get product to ensure version is set
        ProductV2 fetched = productV2Mapper.getById(id);
        assertNotNull(fetched.getVersion());
        assertEquals(0, fetched.getVersion());

        // Update product
        fetched.setProductName("Laptop Pro");
        fetched.setPrice(1299.99);
        int result = productV2Mapper.updateById(fetched);
        assertEquals(1, result);

        // Verify update and version increment
        ProductV2 updated = productV2Mapper.getById(id);
        assertEquals("Laptop Pro", updated.getProductName());
        assertEquals(1299.99, updated.getPrice());
        assertEquals(1, updated.getVersion());
    }

    @Test
    public void testUpdateById_WithVersion_OptimisticLockConflict() {
        // Insert product
        ProductV2 product = new ProductV2();
        product.setProductName("Phone");
        product.setPrice(699.99);
        productV2Mapper.insert(product);

        Long id = product.getProductId();

        // Fetch product twice (simulate two concurrent users)
        ProductV2 user1Product = productV2Mapper.getById(id);
        ProductV2 user2Product = productV2Mapper.getById(id);

        // User 1 updates successfully
        user1Product.setPrice(649.99);
        int result1 = productV2Mapper.updateById(user1Product);
        assertEquals(1, result1);

        // User 2 tries to update with stale version - should fail
        user2Product.setPrice(599.99);
        int result2 = productV2Mapper.updateById(user2Product);
        assertEquals(0, result2); // No rows affected due to version mismatch

        // Verify only user1's update was applied
        ProductV2 final1 = productV2Mapper.getById(id);
        assertEquals(649.99, final1.getPrice());
        assertEquals(1, final1.getVersion());
    }

    @Test
    public void testUpdateById_WithVersion_MultipleUpdates() {
        // Insert product
        ProductV2 product = new ProductV2();
        product.setProductName("Tablet");
        product.setPrice(499.99);
        productV2Mapper.insert(product);

        Long id = product.getProductId();

        // Update multiple times
        for (int i = 1; i <= 5; i++) {
            ProductV2 fetched = productV2Mapper.getById(id);
            fetched.setPrice(499.99 + i * 10);
            int result = productV2Mapper.updateById(fetched);
            assertEquals(1, result);

            // Verify version incremented
            ProductV2 updated = productV2Mapper.getById(id);
            assertEquals(i, updated.getVersion());
        }

        // Final verification
        ProductV2 finalProduct = productV2Mapper.getById(id);
        assertEquals(549.99, finalProduct.getPrice());
        assertEquals(5, finalProduct.getVersion());
    }

    @Test
    public void testUpdateById_WithVersion_NullVersionThrowsException() {
        // Create product with null version
        ProductV2 product = new ProductV2();
        product.setProductId(999L);
        product.setProductName("Test");
        product.setPrice(100.0);
        product.setVersion(null);

        // Try to update - should throw exception
        Exception exception = assertThrows(Exception.class, () -> {
            productV2Mapper.updateById(product);
        });

        assertTrue(exception.getMessage().contains("Version field cannot be null") ||
                   exception.getCause().getMessage().contains("Version field cannot be null"));
    }

    @Test
    public void testUpdateById_NonExistentId() {
        // Try to update non-existent user
        User user = new User();
        user.setId(99999L);
        user.setUsername("Ghost");
        user.setEmail("ghost@example.com");

        int result = userMapper.updateById(user);
        assertEquals(0, result);
    }

    @Test
    public void testUpdateById_PartialFieldUpdate() {
        // Insert user
        User user = new User();
        user.setUsername("Bob");
        user.setEmail("bob@example.com");
        user.setAge(30);
        userMapper.insert(user);

        Long id = user.getId();

        // Update only some fields
        User updateUser = new User();
        updateUser.setId(id);
        updateUser.setUsername("Bob Updated");
        updateUser.setEmail("bob.updated@example.com");
        // age is null, should be updated to null

        int result = userMapper.updateById(updateUser);
        assertEquals(1, result);

        // Verify update
        User updated = userMapper.getById(id);
        assertEquals("Bob Updated", updated.getUsername());
        assertEquals("bob.updated@example.com", updated.getEmail());
        assertNull(updated.getAge()); // Should be null
    }

    @Test
    public void testUpdateById_SkipNullFields_Default() {
        // Insert user with all fields
        User user = new User();
        user.setUsername("Charlie");
        user.setEmail("charlie@example.com");
        user.setAge(35);
        userMapper.insert(user);

        Long id = user.getId();

        // Update only username, email and age are null
        User updateUser = new User();
        updateUser.setId(id);
        updateUser.setUsername("Charlie Updated");
        // email and age are null

        int result = userMapper.updateById(updateUser);
        assertEquals(1, result);

        // Verify: all fields updated including null
        User updated = userMapper.getById(id);
        assertEquals("Charlie Updated", updated.getUsername());
        assertNull(updated.getEmail()); // Should be null
        assertNull(updated.getAge()); // Should be null
    }

    @Test
    public void testUpdateById_SkipNullFields_Explicit() {
        // Insert user with all fields
        User user = new User();
        user.setUsername("David");
        user.setEmail("david@example.com");
        user.setAge(40);
        userMapper.insert(user);

        Long id = user.getId();

        // Update only username, email and age are null
        User updateUser = new User();
        updateUser.setId(id);
        updateUser.setUsername("David Updated");
        // email and age are null

        int result = userMapper.updateSelectiveById(updateUser);
        assertEquals(1, result);

        // Verify: only non-null fields updated
        User updated = userMapper.getById(id);
        assertEquals("David Updated", updated.getUsername());
        assertEquals("david@example.com", updated.getEmail()); // Should remain unchanged
        assertEquals(40, updated.getAge()); // Should remain unchanged
    }

    @Test
    public void testUpdateById_UpdateNullFields() {
        // Insert user with all fields
        User user = new User();
        user.setUsername("Eve");
        user.setEmail("eve@example.com");
        user.setAge(28);
        userMapper.insert(user);

        Long id = user.getId();

        // Update with null values - should set fields to null
        User updateUser = new User();
        updateUser.setId(id);
        updateUser.setUsername("Eve Updated");
        updateUser.setEmail(null);
        updateUser.setAge(null);

        int result = userMapper.updateById(updateUser);
        assertEquals(1, result);

        // Verify: all fields updated including nulls
        User updated = userMapper.getById(id);
        assertEquals("Eve Updated", updated.getUsername());
        assertNull(updated.getEmail()); // Should be null
        assertNull(updated.getAge()); // Should be null
    }

    @Test
    public void testUpdateById_WithVersion_SkipNullFields() {
        // Insert product with version
        ProductV2 product = new ProductV2();
        product.setProductName("Monitor");
        product.setPrice(299.99);
        productV2Mapper.insert(product);

        Long id = product.getProductId();
        ProductV2 fetched = productV2Mapper.getById(id);

        // Update only name, price is null should be skipped
        fetched.setProductName("Monitor Pro");
        fetched.setPrice(null);

        int result = productV2Mapper.updateSelectiveById(fetched);
        assertEquals(1, result);

        // Verify: name updated, price unchanged, version incremented
        ProductV2 updated = productV2Mapper.getById(id);
        assertEquals("Monitor Pro", updated.getProductName());
        assertEquals(299.99, updated.getPrice()); // Should remain unchanged
        assertEquals(1, updated.getVersion()); // Version incremented
    }

    @Test
    public void testUpdateById_WithVersion_UpdateNullFields() {
        // Insert product with version
        ProductV2 product = new ProductV2();
        product.setProductName("Keyboard");
        product.setPrice(99.99);
        productV2Mapper.insert(product);

        Long id = product.getProductId();
        ProductV2 fetched = productV2Mapper.getById(id);

        // Update price to null explicitly
        fetched.setProductName("Keyboard Pro");
        fetched.setPrice(null);

        int result = productV2Mapper.updateById(fetched);
        assertEquals(1, result);

        // Verify: all fields updated including null
        ProductV2 updated = productV2Mapper.getById(id);
        assertEquals("Keyboard Pro", updated.getProductName());
        assertNull(updated.getPrice()); // Should be null
        assertEquals(1, updated.getVersion()); // Version incremented
    }
}
