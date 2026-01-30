package com.ngcin.ems.mapper.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Value container that transfers JSON from/into DB.
 * Main feature is lazy initialization - JsonNode is built only on first access.
 * Thread-safe implementation using double-checked locking.
 */
public class JsonNodeValue implements Serializable {

    private static final long serialVersionUID = 745861884668365334L;

    /**
     * Immutable empty value container.
     */
    public static final JsonNodeValue EMPTY = new JsonNodeValue();

    private String source;

    private boolean dbSource;

    // volatile is required for correct double-checked locking
    private transient volatile JsonNode value;

    private JsonNodeValue() {
        this.source = null;
        this.value = null;
    }

    private JsonNodeValue(String source) {
        if (source != null) {
            source = source.trim();
            this.source = source.isEmpty() ? null : source;
        } else {
            this.source = null;
        }
        this.value = null;
    }

    private JsonNodeValue(JsonNode value) {
        this.value = value;
        this.source = null;
    }

    /**
     * Build value container from JsonNode object.
     * In this case {@link JsonNodeValue#get()} will never throw any exception.
     *
     * @param node JSON node or null
     */
    public static JsonNodeValue from(JsonNode node) {
        return node == null ? EMPTY : new JsonNodeValue(node);
    }

    /**
     * Build value container from JSON string.
     * NOTE if input is not valid JSON than exception in {@link JsonNodeValue#get()} will be thrown.
     *
     * @param json JSON string or null
     */
    public static JsonNodeValue from(String json) {
        if (json == null || json.isEmpty()) {
            return EMPTY;
        }
        json = json.trim();
        return json.isEmpty() ? EMPTY : new JsonNodeValue(json);
    }

    static JsonNodeValue fromDb(String json) {
        JsonNodeValue v = from(json);
        if (v.isPresent()) {
            v.dbSource = true;
        }
        return v;
    }

    /**
     * Test input value and return not null - value from input or empty object.
     */
    public static JsonNodeValue orEmpty(JsonNodeValue node) {
        return node == null || node.isNotPresent() ? EMPTY : node;
    }

    /**
     * Check if nested value is present (not null or empty JSON string).
     */
    public boolean isPresent() {
        return value != null || (source != null && !source.isEmpty());
    }

    /**
     * Opposite to {@link JsonNodeValue#isPresent()}.
     */
    public boolean isNotPresent() {
        return !isPresent();
    }

    /**
     * Return true if value is not present or if underlying JSON is empty object, array or null.
     * WARNING this method can throw same exceptions as {@link JsonNodeValue#get()} in a case if
     * source is invalid JSON string.
     */
    public boolean isEmpty() {
        if (!isPresent()) {
            return true;
        }

        JsonNode n = get();

        if ((n.isObject() || n.isArray()) && n.size() == 0) {
            return true;
        }

        if (n.isNull()) {
            return true;
        }

        return false;
    }

    /**
     * Opposite to {@link JsonNodeValue#isEmpty()}.
     */
    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * Return COPY of JSON node value (will parse node from string at first call).
     *
     * @return Copy of valid JsonNode or MissingNode if no data.
     * @throws JsonParseException On JSON parsing errors.
     */
    public JsonNode get() {
        if (!isPresent()) {
            return MissingNode.getInstance();
        }

        JsonNode result = value;
        if (result == null) {
            synchronized (this) {
                result = value;
                if (result == null) {
                    try {
                        result = ReaderWriter.readTree(source);
                        value = result;
                    } catch (Exception ex) {
                        throw new JsonParseException("Failed to parse JSON: " + ex.getMessage(), ex);
                    }
                }
            }
        }
        return result.deepCopy();
    }

    /**
     * Same as {@link JsonNodeValue#get()}.
     * Created for compatibility with frameworks that works with object properties,
     * thus require get* methods.
     */
    public JsonNode getValue() {
        return get();
    }

    boolean hasDbSource() {
        return this.dbSource && this.source != null;
    }

    String getSource() {
        return this.source;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if (this.source == null && this.value != null) {
            this.source = ReaderWriter.write(this.value);
        }
        oos.defaultWriteObject();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonNodeValue that = (JsonNodeValue) o;

        // Both empty
        if (!this.isPresent() && !that.isPresent()) return true;

        // Compare by source string if both have source
        if (this.source != null && that.source != null) {
            return this.source.equals(that.source);
        }

        // Fall back to comparing parsed values
        return this.get().equals(that.get());
    }

    @Override
    public int hashCode() {
        if (source != null) {
            return source.hashCode();
        }
        return isPresent() ? get().hashCode() : 0;
    }

    @Override
    public String toString() {
        if (source != null) {
            return source;
        }
        return isPresent() ? get().toString() : "EMPTY";
    }
}