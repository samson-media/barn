package com.samsonmedia.barn.logging;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logger for Barn that can be globally disabled for CLI commands.
 *
 * <p>CLI commands should not produce any log output to avoid breaking
 * JSON/XML output. The service enables logging on startup.
 */
public final class BarnLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static volatile boolean enabled = false;
    private static volatile Level level = Level.INFO;
    private static volatile PrintStream output = System.err;

    /**
     * Log levels supported by BarnLogger.
     */
    public enum Level {
        DEBUG(0), INFO(1), WARN(2), ERROR(3), OFF(4);

        private final int value;

        Level(int value) {
            this.value = value;
        }

        boolean isEnabled(Level threshold) {
            return this.value >= threshold.value;
        }
    }

    private final String name;

    private BarnLogger(String name) {
        this.name = name;
    }

    public static BarnLogger getLogger(Class<?> clazz) {
        return new BarnLogger(clazz.getSimpleName());
    }

    public static BarnLogger getLogger(String name) {
        return new BarnLogger(name);
    }

    /**
     * Enables logging globally. Called by the service on startup.
     */
    public static void enable() {
        enabled = true;
    }

    /**
     * Disables logging globally. Default state for CLI commands.
     */
    public static void disable() {
        enabled = false;
    }

    /**
     * Sets the global log level.
     */
    public static void setLevel(Level newLevel) {
        level = newLevel;
    }

    /**
     * Gets the current global log level.
     *
     * @return the current log level
     */
    public static Level getLevel() {
        return level;
    }

    /**
     * Sets the output stream for logs.
     */
    public static void setOutput(PrintStream stream) {
        output = stream;
    }

    /**
     * Logs a trace-level message. Mapped to DEBUG level.
     */
    public void trace(String message, Object... args) {
        log(Level.DEBUG, message, args);
    }

    public void debug(String message, Object... args) {
        log(Level.DEBUG, message, args);
    }

    public void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }

    public void warn(String message, Object... args) {
        log(Level.WARN, message, args);
    }

    public void error(String message, Object... args) {
        log(Level.ERROR, message, args);
    }

    /**
     * Logs an error message with a throwable.
     *
     * @param message the message to log
     * @param t the throwable to log
     */
    public void error(String message, Throwable t) {
        if (!enabled || !Level.ERROR.isEnabled(level)) {
            return;
        }
        String timestamp = LocalDateTime.now().format(FORMATTER);
        output.println(String.format("%s [%s] ERROR %s - %s",
            timestamp, Thread.currentThread().getName(), name, message));
        if (t != null) {
            t.printStackTrace(output);
        }
    }

    public boolean isDebugEnabled() {
        return enabled && Level.DEBUG.isEnabled(level);
    }

    public boolean isInfoEnabled() {
        return enabled && Level.INFO.isEnabled(level);
    }

    private void log(Level msgLevel, String message, Object... args) {
        if (!enabled || !msgLevel.isEnabled(level)) {
            return;
        }
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String formattedMessage = formatMessage(message, args);
        output.println(String.format("%s [%s] %-5s %s - %s",
            timestamp, Thread.currentThread().getName(), msgLevel, name, formattedMessage));
    }

    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        // Simple {} placeholder replacement like SLF4J
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < message.length()) {
            if (i < message.length() - 1 && message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    sb.append(args[argIndex++]);
                } else {
                    sb.append("{}");
                }
                i += 2;
            } else {
                sb.append(message.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
