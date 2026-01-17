package com.ngcin.ems.test;

import com.ngcin.ems.test.entity.InheritedUser;
import com.ngcin.ems.test.mapper.InheritedUserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for entities with inherited fields.
 * Verifies that BaseMapper operations work correctly with inheritance.
 */
class InheritedEntityMapperTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private InheritedUserMapper mapper;

    @BeforeAll
    static void setUp() {
        // Create mybatis-config.xml content
        String mybatisConfig = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE configuration
                        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                        "https://mybatis.org/dtd/mybatis-3-config.dtd">
                <configuration>
                    <settings>
                        <setting name="mapUnderscoreToCamelCase" value="true"/>
                    </settings>
                    <environments default="development">
                        <environment id="development">
                            <transactionManager type="JDBC"/>
                            <dataSource type="POOLED">
                                <property name="driver" value="org.h2.Driver"/>
                                <property name="url" value="jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.InheritedUserMapper"/>
                    </mappers>
                </configuration>
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes());
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        // Initialize database schema
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE t_inherited_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50), " +
                    "email VARCHAR(100))");
            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    @BeforeEach
    void openSession() throws SQLException {
        session = sqlSessionFactory.openSession();
        mapper = session.getMapper(InheritedUserMapper.class);

        // Clean up table before each test
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_inherited_user");
        }
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void testInsert_InheritedEntity_Success() {
        // Arrange
        InheritedUser user = new InheritedUser("john_doe", "john@example.com");

        // Act
        int result = mapper.insert(user);
        session.commit();

        // Assert
        assertEquals(1, result, "Insert should affect 1 row");
        assertNotNull(user.getId(), "ID should be generated after insert (from inherited field)");

        // Verify the record in database
        try (Connection conn = sqlSessionFactory.openSession().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM t_inherited_user WHERE id = " + user.getId())) {

            assertTrue(rs.next(), "Should find the record");
            assertEquals("john_doe", rs.getString("username"));
            assertEquals("john@example.com", rs.getString("email"));
        } catch (SQLException e) {
            fail("Failed to verify database record: " + e.getMessage());
        }
    }

    @Test
    void testGetById_InheritedEntity_Success() {
        // Arrange - Insert a record first
        InheritedUser user = new InheritedUser("jane_doe", "jane@example.com");
        mapper.insert(user);
        session.commit();
        Long generatedId = user.getId();

        // Act
        InheritedUser retrieved = mapper.getById(generatedId);

        // Assert
        assertNotNull(retrieved, "Should retrieve the entity");
        assertEquals(generatedId, retrieved.getId(), "ID should match (from inherited field)");
        assertEquals("jane_doe", retrieved.getUsername(), "Username should match");
        assertEquals("jane@example.com", retrieved.getEmail(), "Email should match");
    }

    @Test
    void testUpdateById_InheritedEntity_Success() {
        // Arrange - Insert a record first
        InheritedUser user = new InheritedUser("bob_smith", "bob@example.com");
        mapper.insert(user);
        session.commit();

        // Modify the entity
        user.setUsername("bob_updated");
        user.setEmail("bob_new@example.com");

        // Act
        int result = mapper.updateById(user);
        session.commit();

        // Assert
        assertEquals(1, result, "Update should affect 1 row");

        // Verify changes persisted
        InheritedUser updated = mapper.getById(user.getId());
        assertNotNull(updated, "Should retrieve updated entity");
        assertEquals("bob_updated", updated.getUsername(), "Username should be updated");
        assertEquals("bob_new@example.com", updated.getEmail(), "Email should be updated");
    }
}
