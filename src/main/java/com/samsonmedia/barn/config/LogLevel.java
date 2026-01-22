package com.samsonmedia.barn.config;

import java.util.Locale;

/**
 * Log level for the Barn service.
 */
public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR;

    /**
     * Parses a log level from a string (case-insensitive).
     *
     * @param value the string value to parse
     * @return the parsed LogLevel
     * @throws IllegalArgumentException if the value is not a valid log level
     */
    public static LogLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Log level cannot be null or blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid log level: '" + value + "'. Valid values are: debug, info, warn, error");
        }
    }
}
