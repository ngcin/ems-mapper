package com.ngcin.ems.mapper.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TreeNodeTypeHandler.
 */
class TreeNodeTypeHandlerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    void testJsonObjectSerialization() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", "test");
        node.put("age", 25);

        String json = ReaderWriter.write(node);

        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"test\""));
    }

    @Test
    void testJsonArraySerialization() throws Exception {
        ArrayNode array = mapper.createArrayNode();
        array.add("item1");
        array.add("item2");
        array.add(123);

        String json = ReaderWriter.write(array);

        assertNotNull(json);
        assertEquals("[\"item1\",\"item2\",123]", json);
    }

    @Test
    void testJsonObjectDeserialization() throws Exception {
        String json = "{\"name\":\"test\",\"age\":25}";

        JsonNode node = ReaderWriter.readTree(json);

        assertNotNull(node);
        assertTrue(node.isObject());
        assertEquals("test", node.get("name").asText());
        assertEquals(25, node.get("age").asInt());
    }

    @Test
    void testJsonArrayDeserialization() throws Exception {
        String json = "[{\"id\":1},{\"id\":2}]";

        JsonNode node = ReaderWriter.readTree(json);

        assertNotNull(node);
        assertTrue(node.isArray());
        assertEquals(2, node.size());
        assertEquals(1, node.get(0).get("id").asInt());
        assertEquals(2, node.get(1).get("id").asInt());
    }

    @Test
    void testNestedJsonDeserialization() throws Exception {
        String json = "{\"user\":{\"name\":\"test\"},\"tags\":[\"a\",\"b\"]}";

        JsonNode node = ReaderWriter.readTree(json);

        assertNotNull(node);
        assertEquals("test", node.get("user").get("name").asText());
        assertTrue(node.get("tags").isArray());
        assertEquals(2, node.get("tags").size());
    }

    @Test
    void testEmptyObjectDeserialization() throws Exception {
        String json = "{}";

        JsonNode node = ReaderWriter.readTree(json);

        assertNotNull(node);
        assertTrue(node.isObject());
        assertEquals(0, node.size());
    }

    @Test
    void testEmptyArrayDeserialization() throws Exception {
        String json = "[]";

        JsonNode node = ReaderWriter.readTree(json);

        assertNotNull(node);
        assertTrue(node.isArray());
        assertEquals(0, node.size());
    }
}
