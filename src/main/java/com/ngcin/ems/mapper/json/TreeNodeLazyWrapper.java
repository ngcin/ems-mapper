package com.ngcin.ems.mapper.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Objects;

/**
 * Lazy JSON node wrapper that defers parsing until first access.
 * Thread-safe implementation using double-checked locking with volatile.
 *
 * <p>Note: If the input JSON string is invalid, a RuntimeException
 * will be thrown on first method access.
 */
public class TreeNodeLazyWrapper implements TreeNode, Serializable {

    private static final long serialVersionUID = -5553988352322235606L;

    private final String json;

    // volatile is required for correct double-checked locking
    private transient volatile JsonNode node;

    TreeNodeLazyWrapper(String json) {
        this.json = Objects.requireNonNull(json, "JSON string cannot be null");
    }

    /**
     * Returns the source JSON string.
     */
    public String getJsonSource() {
        return this.json;
    }

    /**
     * Returns the underlying JsonNode, parsing lazily on first access.
     * Thread-safe using double-checked locking.
     */
    private JsonNode tree() {
        JsonNode result = node;
        if (result == null) {
            synchronized (this) {
                result = node;
                if (result == null) {
                    try {
                        result = ReaderWriter.readTree(json);
                        node = result;
                    } catch (IOException ex) {
                        throw new JsonParseException("Failed to parse JSON: " + ex.getMessage(), ex);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public JsonToken asToken() {
        return tree().asToken();
    }

    @Override
    public JsonParser.NumberType numberType() {
        return tree().numberType();
    }

    @Override
    public int size() {
        return tree().size();
    }

    @Override
    public boolean isValueNode() {
        return tree().isValueNode();
    }

    @Override
    public boolean isContainerNode() {
        return tree().isContainerNode();
    }

    @Override
    public boolean isMissingNode() {
        return tree().isMissingNode();
    }

    @Override
    public boolean isArray() {
        return tree().isArray();
    }

    @Override
    public boolean isObject() {
        return tree().isObject();
    }

    @Override
    public TreeNode get(String string) {
        return tree().get(string);
    }

    @Override
    public TreeNode get(int i) {
        return tree().get(i);
    }

    @Override
    public TreeNode path(String string) {
        return tree().path(string);
    }

    @Override
    public TreeNode path(int i) {
        return tree().path(i);
    }

    @Override
    public Iterator<String> fieldNames() {
        return tree().fieldNames();
    }

    @Override
    public TreeNode at(JsonPointer jp) {
        return tree().at(jp);
    }

    @Override
    public TreeNode at(String string) throws IllegalArgumentException {
        return tree().at(string);
    }

    @Override
    public JsonParser traverse() {
        return tree().traverse();
    }

    @Override
    public JsonParser traverse(ObjectCodec oc) {
        return tree().traverse(oc);
    }

    @Override
    public String toString() {
        return json;
    }

    @Override
    public int hashCode() {
        // Use json string hash to avoid unnecessary parsing
        return json.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;

        // Compare with another TreeNodeLazyWrapper by json string
        if (o instanceof TreeNodeLazyWrapper other) {
            return json.equals(other.json);
        }

        // Compare with JsonNode by parsing
        if (o instanceof JsonNode) {
            return tree().equals(o);
        }

        return false;
    }
}