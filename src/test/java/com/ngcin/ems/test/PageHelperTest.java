package com.ngcin.ems.test;

import com.ngcin.ems.mapper.IPage;
import com.ngcin.ems.mapper.MapperException;
import com.ngcin.ems.mapper.core.Page;
import com.ngcin.ems.mapper.PageHelper;
import com.ngcin.ems.test.entity.User;
import com.ngcin.ems.test.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PageHelper ThreadLocal pagination.
 */
class PageHelperTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private UserMapper userMapper;

    @BeforeAll
    static void setUp() {
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
                                <property name="url" value="jdbc:h2:mem:testdb_page_helper;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
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

        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();
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

    @Test
    void testPageHelper_BasicPagination() {
        for (int i = 1; i <= 25; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        IPage<User> result = PageHelper.page(1, 10, () -> userMapper.selectList(null));

        assertNotNull(result, "Result should not be null");
        assertEquals(10, result.getRecords().size(), "Should return 10 records");
        assertEquals(25, result.getTotal(), "Total should be 25");
        assertEquals(3, result.getPages(), "Should have 3 pages");
        assertEquals(1, result.getCurrent(), "Current page should be 1");
    }

    @Test
    void testPageHelper_WithQueryConditions() {
        for (int i = 1; i <= 30; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + (i % 3));
            userMapper.insert(user);
        }
        session.commit();

        User queryUser = new User();
        queryUser.setAge(21);
        IPage<User> result = PageHelper.page(1, 5, () -> userMapper.selectList(queryUser));

        assertEquals(5, result.getRecords().size(), "Should return 5 records");
        assertEquals(10, result.getTotal(), "Total should be 10");
        for (User user : result.getRecords()) {
            assertEquals(21, user.getAge(), "All users should have age 21");
        }
    }

    @Test
    void testPageHelper_SecondPage() {
        for (int i = 1; i <= 25; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        IPage<User> result = PageHelper.page(2, 10, () -> userMapper.selectList(null));

        assertEquals(10, result.getRecords().size(), "Should return 10 records");
        assertEquals(25, result.getTotal(), "Total should be 25");
        assertEquals(2, result.getCurrent(), "Current page should be 2");
    }

    @Test
    void testPageHelper_EmptyResults() {
        IPage<User> result = PageHelper.page(1, 10, () -> userMapper.selectList(null));

        assertNotNull(result, "Result should not be null");
        assertEquals(0, result.getRecords().size(), "Should return 0 records");
        assertEquals(0, result.getTotal(), "Total should be 0");
    }

    @Test
    void testPageHelper_LastPagePartial() {
        for (int i = 1; i <= 23; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        IPage<User> result = PageHelper.page(3, 10, () -> userMapper.selectList(null));

        assertEquals(3, result.getRecords().size(), "Should return 3 records on last page");
        assertEquals(23, result.getTotal(), "Total should be 23");
    }

    @Test
    void testPageHelper_ThreadLocalCleanup() {
        for (int i = 1; i <= 10; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        PageHelper.page(1, 5, () -> userMapper.selectList(null));

        assertNull(PageHelper.getLocalPage(), "ThreadLocal should be cleaned after page() call");
    }

    @Test
    void testPageHelper_ThreadLocalCleanupOnException() {
        assertThrows(MapperException.class, () -> {
            PageHelper.page(1, 10, () -> {
                throw new RuntimeException("Simulated error");
            });
        });

        assertNull(PageHelper.getLocalPage(), "ThreadLocal should be cleaned even after exception");
    }

    @Test
    void testPageHelper_ThreadSafety() throws Exception {
        for (int i = 1; i <= 100; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<IPage<User>> results = new ConcurrentLinkedQueue<>();

        for (int t = 0; t < threadCount; t++) {
            final int pageNum = t + 1;
            executor.submit(() -> {
                try (SqlSession threadSession = sqlSessionFactory.openSession()) {
                    UserMapper threadMapper = threadSession.getMapper(UserMapper.class);
                    IPage<User> result = PageHelper.page(pageNum, 10, () -> threadMapper.selectList(null));
                    results.add(result);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, results.size(), "All threads should complete");
        for (IPage<User> result : results) {
            assertEquals(100, result.getTotal(), "Each thread should see total 100");
        }
    }

    @Test
    void testPageHelper_MethodParameterTakesPrecedence() {
        for (int i = 1; i <= 50; i++) {
            User user = new User("user" + i, "user" + i + "@test.com", 20 + i);
            userMapper.insert(user);
        }
        session.commit();

        // 使用 page() 方法，它会自动设置 records
        Page<User> methodPage = new Page<>(2, 10);
        PageHelper.page(1, 5, () -> {
            userMapper.page(methodPage, null);
            return methodPage.getRecords();
        });

        // 方法参数应该优先于 ThreadLocal
        assertEquals(2, methodPage.getCurrent(), "Method parameter should take precedence");
        assertEquals(10, methodPage.getRecords().size(), "Should use method parameter page size");
        assertEquals(50, methodPage.getTotal(), "Total should be 50");
    }
}
