package com.samsonmedia.barn.jobs;

import java.util.Locale;

/**
 * Load level classification for jobs.
 *
 * <p>Jobs are classified into load levels based on their resource intensity:
 * <ul>
 *   <li>{@link #HIGH} - CPU/GPU intensive tasks like transcoding (default: 2 concurrent)</li>
 *   <li>{@link #MEDIUM} - General purpose tasks (default: 8 concurrent)</li>
 *   <li>{@link #LOW} - Network/IO intensive tasks like downloads (default: 32 concurrent)</li>
 * </ul>
 */
public enum LoadLevel {

    /**
     * High load level for CPU/GPU intensive tasks (e.g., ffmpeg, transcoding).
     */
    HIGH(2),

    /**
     * Medium load level for general purpose tasks.
     */
    MEDIUM(8),

    /**
     * Low load level for network/IO intensive tasks (e.g., curl, rclone).
     */
    LOW(32);

    private final int defaultMaxJobs;

    LoadLevel(int defaultMaxJobs) {
        this.defaultMaxJobs = defaultMaxJobs;
    }

    /**
     * Returns the default maximum concurrent jobs for this load level.
     *
     * @return the default max jobs
     */
    public int getDefaultMaxJobs() {
        return defaultMaxJobs;
    }

    /**
     * Returns the lowercase string representation of this load level.
     *
     * @return the lowercase string
     */
    public String toLowercase() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the default load level (MEDIUM).
     *
     * @return the default load level
     */
    public static LoadLevel getDefault() {
        return MEDIUM;
    }

    /**
     * Parses a load level from a string (case-insensitive).
     *
     * @param value the string value to parse
     * @return the parsed LoadLevel
     * @throws IllegalArgumentException if the value is not a valid load level
     */
    public static LoadLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Load level cannot be null or blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid load level: '" + value + "'. Valid values are: high, medium, low");
        }
    }
}
