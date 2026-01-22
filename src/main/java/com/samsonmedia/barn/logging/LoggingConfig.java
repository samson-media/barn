package com.samsonmedia.barn.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.samsonmedia.barn.config.LogLevel;

/**
 * Configuration utilities for the logging system.
 *
 * <p>Provides runtime control over log levels and log directory setup.
 */
public final class LoggingConfig {

    private LoggingConfig() {
        // Utility class
    }

    /**
     * Sets the root log level at runtime and enables logging.
     *
     * @param level the log level to set
     * @throws NullPointerException if level is null
     */
    public static void setLogLevel(LogLevel level) {
        Objects.requireNonNull(level, "level must not be null");
        BarnLogger.enable();
        BarnLogger.setLevel(toBarnLoggerLevel(level));
    }

    /**
     * Gets the current root log level.
     *
     * @return the current log level
     */
    public static LogLevel getLogLevel() {
        return fromBarnLoggerLevel(BarnLogger.getLevel());
    }

    /**
     * Ensures the log directory exists.
     *
     * @param logDir the log directory path
     * @throws IOException if the directory cannot be created
     */
    public static void ensureLogDirectory(Path logDir) throws IOException {
        Objects.requireNonNull(logDir, "logDir must not be null");
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }
    }

    /**
     * Gets the default log directory.
     *
     * @return the default log directory path
     */
    public static Path getDefaultLogDirectory() {
        String barnLogDir = System.getProperty("BARN_LOG_DIR");
        if (barnLogDir != null && !barnLogDir.isEmpty()) {
            return Path.of(barnLogDir);
        }
        String envLogDir = System.getenv("BARN_LOG_DIR");
        if (envLogDir != null && !envLogDir.isEmpty()) {
            return Path.of(envLogDir);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "barn", "logs");
    }

    private static BarnLogger.Level toBarnLoggerLevel(LogLevel level) {
        return switch (level) {
            case DEBUG -> BarnLogger.Level.DEBUG;
            case INFO -> BarnLogger.Level.INFO;
            case WARN -> BarnLogger.Level.WARN;
            case ERROR -> BarnLogger.Level.ERROR;
        };
    }

    private static LogLevel fromBarnLoggerLevel(BarnLogger.Level level) {
        return switch (level) {
            case DEBUG -> LogLevel.DEBUG;
            case INFO -> LogLevel.INFO;
            case WARN -> LogLevel.WARN;
            case ERROR, OFF -> LogLevel.ERROR;
        };
    }
}
