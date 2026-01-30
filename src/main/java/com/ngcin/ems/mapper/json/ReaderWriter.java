package com.ngcin.ems.mapper.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Internal utility class for JSON serialization/deserialization.
 * Uses shared ObjectReader/ObjectWriter instances for thread-safe, high-performance operations.
 */
final class ReaderWriter {

    private static final int MAX_STRING_LENGTH = 10 * 1024 * 1024; // 10MB
    private static final int MAX_NESTING_DEPTH = 500;

    private static final ObjectReader READER;
    private static final ObjectWriter WRITER;
    private static final ObjectMapper MAPPER;

    static {
        // Create JsonFactory with security constraints
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(MAX_STRING_LENGTH)
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .build())
                .build();

        MAPPER = JsonMapper.builder(factory)
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .build();

        READER = MAPPER.reader();
        WRITER = MAPPER.writer();
    }

    private ReaderWriter() {
    }

    static JsonNode readTree(String json) throws IOException {
        return READER.readTree(json);
    }

    static String write(TreeNode tree) throws JsonProcessingException {
        return WRITER.writeValueAsString(tree);
    }
}