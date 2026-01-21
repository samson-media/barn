package com.samsonmedia.barn.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple TOML parser supporting the subset of TOML used by Barn configuration.
 *
 * <p>Supports:
 * <ul>
 *   <li>Sections: [section_name]</li>
 *   <li>String values: key = "value"</li>
 *   <li>Integer values: key = 123</li>
 *   <li>Float values: key = 1.5</li>
 *   <li>Boolean values: key = true/false</li>
 *   <li>Array values: key = [1, 2, 3]</li>
 *   <li>Comments: # comment</li>
 * </ul>
 */
public final class TomlParser {

    private static final Pattern SECTION_PATTERN = Pattern.compile("^\\s*\\[([a-zA-Z_][a-zA-Z0-9_]*)\\]\\s*$");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
        "^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.+)$");
    private static final Pattern STRING_PATTERN = Pattern.compile("^\"(.*)\"$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern FLOAT_PATTERN = Pattern.compile("^-?\\d+\\.\\d+$");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("^(true|false)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARRAY_PATTERN = Pattern.compile("^\\[(.*)\\]$");

    private TomlParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses TOML content from a string.
     *
     * @param content the TOML content
     * @return a map of section names to key-value maps
     * @throws TomlParseException if the content cannot be parsed
     */
    public static Map<String, Map<String, Object>> parse(String content) {
        Objects.requireNonNull(content, "content must not be null");
        try {
            return parse(new StringReader(content));
        } catch (IOException e) {
            throw new TomlParseException("Failed to parse TOML content", e);
        }
    }

    /**
     * Parses TOML content from a reader.
     *
     * @param reader the reader to read from
     * @return a map of section names to key-value maps
     * @throws IOException if reading fails
     * @throws TomlParseException if the content cannot be parsed
     */
    public static Map<String, Map<String, Object>> parse(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        Map<String, Map<String, Object>> result = new HashMap<>();
        String currentSection = null;
        int lineNumber = 0;

        try (BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                line = stripComment(line).trim();

                if (line.isEmpty()) {
                    continue;
                }

                Matcher sectionMatcher = SECTION_PATTERN.matcher(line);
                if (sectionMatcher.matches()) {
                    currentSection = sectionMatcher.group(1);
                    result.putIfAbsent(currentSection, new HashMap<>());
                    continue;
                }

                Matcher keyValueMatcher = KEY_VALUE_PATTERN.matcher(line);
                if (keyValueMatcher.matches()) {
                    if (currentSection == null) {
                        throw new TomlParseException(
                            "Key-value pair outside of section at line " + lineNumber);
                    }
                    String key = keyValueMatcher.group(1);
                    String valueStr = keyValueMatcher.group(2).trim();
                    Object value = parseValue(valueStr, lineNumber);
                    result.get(currentSection).put(key, value);
                    continue;
                }

                throw new TomlParseException("Invalid syntax at line " + lineNumber + ": " + line);
            }
        }

        return result;
    }

    private static String stripComment(String line) {
        int commentIndex = -1;
        boolean inString = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (c == '#' && !inString) {
                commentIndex = i;
                break;
            }
        }

        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    private static Object parseValue(String valueStr, int lineNumber) {
        Matcher stringMatcher = STRING_PATTERN.matcher(valueStr);
        if (stringMatcher.matches()) {
            return unescapeString(stringMatcher.group(1));
        }

        if (BOOLEAN_PATTERN.matcher(valueStr).matches()) {
            return Boolean.parseBoolean(valueStr);
        }

        if (INTEGER_PATTERN.matcher(valueStr).matches()) {
            try {
                return Integer.parseInt(valueStr);
            } catch (NumberFormatException e) {
                try {
                    return Long.parseLong(valueStr);
                } catch (NumberFormatException e2) {
                    throw new TomlParseException(
                        "Integer value too large at line " + lineNumber + ": " + valueStr);
                }
            }
        }

        if (FLOAT_PATTERN.matcher(valueStr).matches()) {
            return Double.parseDouble(valueStr);
        }

        Matcher arrayMatcher = ARRAY_PATTERN.matcher(valueStr);
        if (arrayMatcher.matches()) {
            return parseArray(arrayMatcher.group(1), lineNumber);
        }

        throw new TomlParseException("Cannot parse value at line " + lineNumber + ": " + valueStr);
    }

    private static List<Object> parseArray(String content, int lineNumber) {
        List<Object> result = new ArrayList<>();
        if (content.trim().isEmpty()) {
            return result;
        }

        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                inString = !inString;
                current.append(c);
            } else if (c == '[' && !inString) {
                depth++;
                current.append(c);
            } else if (c == ']' && !inString) {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0 && !inString) {
                String value = current.toString().trim();
                if (!value.isEmpty()) {
                    result.add(parseValue(value, lineNumber));
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            result.add(parseValue(remaining, lineNumber));
        }

        return result;
    }

    private static String unescapeString(String str) {
        return str
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r");
    }

    /**
     * Exception thrown when TOML parsing fails.
     */
    public static class TomlParseException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new TomlParseException.
         *
         * @param message the error message
         */
        public TomlParseException(String message) {
            super(message);
        }

        /**
         * Creates a new TomlParseException with a cause.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public TomlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
