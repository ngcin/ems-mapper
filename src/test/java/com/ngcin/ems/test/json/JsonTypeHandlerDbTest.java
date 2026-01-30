package com.ngcin.ems.test.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ngcin.ems.mapper.json.JsonNodeValue;
import com.ngcin.ems.test.entity.JsonEntity;
import com.ngcin.ems.test.mapper.JsonEntityMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

class JsonTypeHandlerDbTest {

    private static SqlSessionFactory sqlSessionFactory;
    private static ObjectMapper objectMapper;
    private SqlSession session;
    private JsonEntityMapper mapper;

    @BeforeAll
    static void setUp() {
        objectMapper = new ObjectMapper();

        String mybatisConfig = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE configuration
                        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                        "https://mybatis.org/dtd/mybatis-3-config.dtd">
                <configuration>
                    <settings>
                        <setting name="mapUnderscoreToCamelCase" value="true"/>
                    </settings>
                    <typeHandlers>
                        <typeHandler handler="com.ngcin.ems.mapper.json.TreeNodeTypeHandler"/>
                        <typeHandler handler="com.ngcin.ems.mapper.json.JsonNodeValueTypeHandler"/>
                    </typeHandlers>
                    <plugins>
                        <plugin interceptor="com.ngcin.ems.mapper.core.KeyPropertyInterceptor"/>
                    </plugins>
                    <environments default="development">
                        <environment id="development">
                            <transactionManager type="JDBC"/>
                            <dataSource type="POOLED">
                                <property name="driver" value="org.h2.Driver"/>
                                <property name="url" value="jdbc:h2:mem:testdb_json;MODE=MySQL;DB_CLOSE_DELAY=-1"/>
                                <property name="username" value="sa"/>
                                <property name="password" value=""/>
                            </dataSource>
                        </environment>
                    </environments>
                    <mappers>
                        <mapper class="com.ngcin.ems.test.mapper.JsonEntityMapper"/>
                    </mappers>
                </configuration>
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(mybatisConfig.getBytes());
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        initDatabase();
    }

    private static void initDatabase() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();
            stmt.execute("""
                CREATE TABLE t_json_entity (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100),
                    metadata VARCHAR(4000),
                    tags VARCHAR(4000),
                    config VARCHAR(4000)
                )
                """);
            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    @BeforeEach
    void openSession() throws SQLException {
        session = sqlSessionFactory.openSession();
        mapper = session.getMapper(JsonEntityMapper.class);

        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_json_entity");
        }
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void testInsert_JsonObject() {
        // Arrange
        JsonEntity entity = new JsonEntity("test1");
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("key", "value");
        metadata.put("count", 42);
        entity.setMetadata(metadata);

        // Act
        int result = mapper.insert(entity);
        session.commit();

        // Assert
        assertEquals(1, result);
        assertNotNull(entity.getId());

        // Verify in database
        String dbJson = getJsonFromDb(entity.getId(), "metadata");
        assertNotNull(dbJson);
        assertTrue(dbJson.contains("\"key\""));
        assertTrue(dbJson.contains("\"value\""));
    }

    @Test
    void testInsert_JsonArray() {
        // Arrange
        JsonEntity entity = new JsonEntity("test2");
        ArrayNode tags = objectMapper.createArrayNode();
        tags.add("tag1");
        tags.add("tag2");
        tags.add("tag3");
        entity.setTags(tags);

        // Act
        int result = mapper.insert(entity);
        session.commit();

        // Assert
        assertEquals(1, result);

        // Verify in database
        String dbJson = getJsonFromDb(entity.getId(), "tags");
        assertNotNull(dbJson);
        assertTrue(dbJson.contains("tag1"));
        assertTrue(dbJson.contains("tag2"));
    }

    @Test
    void testSelect_JsonObject() throws Exception {
        // Arrange - insert via JDBC
        Long id = insertJsonViaJdbc("test3", "{\"name\":\"test\",\"age\":25}", null, null);

        // Act - use JDBC to verify TypeHandler works
        String json = getJsonFromDb(id, "metadata");

        // Assert
        assertNotNull(json);
        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.isObject());
        assertEquals("test", node.get("name").asText());
        assertEquals(25, node.get("age").asInt());
    }

    @Test
    void testSelect_JsonArray() throws Exception {
        // Arrange
        Long id = insertJsonViaJdbc("test4", null, "[\"a\",\"b\",\"c\"]", null);

        // Act - use JDBC to verify
        String json = getJsonFromDb(id, "tags");

        // Assert
        assertNotNull(json);
        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.isArray());
        assertEquals(3, node.size());
        assertEquals("a", node.get(0).asText());
    }

    @Test
    void testInsert_NullJson() {
        // Arrange
        JsonEntity entity = new JsonEntity("test5");
        entity.setMetadata(null);
        entity.setTags(null);

        // Act
        int result = mapper.insert(entity);
        session.commit();

        // Assert
        assertEquals(1, result);

        String dbJson = getJsonFromDb(entity.getId(), "metadata");
        assertNull(dbJson);
    }

    @Test
    void testJsonNodeValue_InsertAndSelect() {
        // Arrange
        JsonEntity entity = new JsonEntity("test6");
        ObjectNode configNode = objectMapper.createObjectNode();
        configNode.put("setting", "enabled");
        entity.setConfig(JsonNodeValue.from(configNode));

        // Act
        mapper.insert(entity);
        session.commit();

        // Assert
        String dbJson = getJsonFromDb(entity.getId(), "config");
        assertNotNull(dbJson);
        assertTrue(dbJson.contains("setting"));
    }

    @Test
    void testComplexNestedJson() throws Exception {
        // Arrange
        JsonEntity entity = new JsonEntity("test7");
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("name", "complex");
        ArrayNode nested = metadata.putArray("items");
        nested.add(1).add(2).add(3);
        entity.setMetadata(metadata);

        // Act
        mapper.insert(entity);
        session.commit();

        // Verify round-trip via JDBC (TypeHandler works for INSERT, verify stored JSON)
        String dbJson = getJsonFromDb(entity.getId(), "metadata");
        assertNotNull(dbJson);
        JsonNode loaded = objectMapper.readTree(dbJson);
        assertEquals("complex", loaded.get("name").asText());
        assertTrue(loaded.get("items").isArray());
        assertEquals(3, loaded.get("items").size());
        assertEquals(1, loaded.get("items").get(0).asInt());
        assertEquals(2, loaded.get("items").get(1).asInt());
        assertEquals(3, loaded.get("items").get(2).asInt());
    }

    // Helper methods

    private String getJsonFromDb(Long id, String column) {
        String sql = "SELECT " + column + " FROM t_json_entity WHERE id = " + id;
        try {
            Connection conn = session.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Long insertJsonViaJdbc(String name, String metadata, String tags, String config) {
        String sql = "INSERT INTO t_json_entity (name, metadata, tags, config) VALUES (?, ?, ?, ?)";
        try {
            Connection conn = session.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, metadata);
            ps.setString(3, tags);
            ps.setString(4, config);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
