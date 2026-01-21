package com.samsonmedia.barn.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.LoggerFactory;

import com.samsonmedia.barn.config.LogLevel;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

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
     * Sets the root log level at runtime.
     *
     * @param level the log level to set
     * @throws NullPointerException if level is null
     */
    public static void setLogLevel(LogLevel level) {
        Objects.requireNonNull(level, "level must not be null");

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(toLogbackLevel(level));
    }

    /**
     * Gets the current root log level.
     *
     * @return the current log level
     */
    public static LogLevel getLogLevel() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        return fromLogbackLevel(rootLogger.getLevel());
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

    private static Level toLogbackLevel(LogLevel level) {
        return switch (level) {
            case DEBUG -> Level.DEBUG;
            case INFO -> Level.INFO;
            case WARN -> Level.WARN;
            case ERROR -> Level.ERROR;
        };
    }

    private static LogLevel fromLogbackLevel(Level level) {
        if (level == null || level.equals(Level.INFO)) {
            return LogLevel.INFO;
        }
        if (level.equals(Level.DEBUG) || level.equals(Level.TRACE)) {
            return LogLevel.DEBUG;
        }
        if (level.equals(Level.WARN)) {
            return LogLevel.WARN;
        }
        if (level.equals(Level.ERROR) || level.equals(Level.OFF)) {
            return LogLevel.ERROR;
        }
        return LogLevel.INFO;
    }
}
