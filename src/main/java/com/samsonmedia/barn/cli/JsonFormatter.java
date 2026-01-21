package com.samsonmedia.barn.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Formats output as JSON.
 *
 * <p>Produces machine-readable JSON with proper ISO-8601 date formatting.
 */
public class JsonFormatter implements OutputFormatter {

    private final ObjectMapper mapper;

    /**
     * Creates a JsonFormatter with default settings.
     */
    public JsonFormatter() {
        this.mapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.enable(SerializationFeature.INDENT_OUTPUT);
        return om;
    }

    @Override
    public String format(Object value) {
        if (value == null) {
            return "null";
        }

        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return formatError("Failed to serialize object", e);
        }
    }

    @Override
    public String formatList(List<?> values) {
        if (values == null) {
            return "[]";
        }

        try {
            return mapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return formatError("Failed to serialize list", e);
        }
    }

    @Override
    public String formatError(String message, Throwable cause) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", true);
        error.put("message", message);

        if (cause != null) {
            error.put("cause", cause.getMessage());
            error.put("type", cause.getClass().getSimpleName());
        }

        try {
            return mapper.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            // Fallback to plain JSON if serialization fails
            return "{\"error\":true,\"message\":\"" + escapeJson(message) + "\"}";
        }
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
