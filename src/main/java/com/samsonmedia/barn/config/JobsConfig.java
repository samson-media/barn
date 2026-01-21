package com.samsonmedia.barn.config;

import java.util.List;
import java.util.Objects;

/**
 * Configuration for job execution behavior.
 *
 * @param defaultTimeoutSeconds maximum time a job can run before being killed
 * @param maxRetries number of retry attempts for failed jobs
 * @param retryDelaySeconds initial delay before first retry
 * @param retryBackoffMultiplier multiplier applied to delay after each retry
 * @param retryOnExitCodes only retry on these exit codes (empty = retry all failures)
 */
public record JobsConfig(
    int defaultTimeoutSeconds,
    int maxRetries,
    int retryDelaySeconds,
    double retryBackoffMultiplier,
    List<Integer> retryOnExitCodes
) {

    /** Default job timeout in seconds (1 hour). */
    public static final int DEFAULT_TIMEOUT_SECONDS = 3600;

    /** Default maximum number of retries. */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Default retry delay in seconds. */
    public static final int DEFAULT_RETRY_DELAY_SECONDS = 30;

    /** Default retry backoff multiplier. */
    public static final double DEFAULT_RETRY_BACKOFF_MULTIPLIER = 2.0;

    /**
     * Creates a JobsConfig with validation.
     */
    public JobsConfig {
        Objects.requireNonNull(retryOnExitCodes, "retryOnExitCodes must not be null");
        if (defaultTimeoutSeconds < 1) {
            throw new IllegalArgumentException("defaultTimeoutSeconds must be at least 1");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }
        if (retryDelaySeconds < 0) {
            throw new IllegalArgumentException("retryDelaySeconds must be non-negative");
        }
        if (retryBackoffMultiplier < 1.0) {
            throw new IllegalArgumentException("retryBackoffMultiplier must be at least 1.0");
        }
        retryOnExitCodes = List.copyOf(retryOnExitCodes);
    }

    /**
     * Creates a JobsConfig with default values.
     *
     * @return a JobsConfig with all defaults
     */
    public static JobsConfig withDefaults() {
        return new JobsConfig(
            DEFAULT_TIMEOUT_SECONDS,
            DEFAULT_MAX_RETRIES,
            DEFAULT_RETRY_DELAY_SECONDS,
            DEFAULT_RETRY_BACKOFF_MULTIPLIER,
            List.of()
        );
    }
}
