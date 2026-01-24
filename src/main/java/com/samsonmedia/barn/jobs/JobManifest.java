package com.samsonmedia.barn.jobs;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.samsonmedia.barn.config.JobsConfig;

/**
 * Immutable job definition stored as manifest.json.
 *
 * <p>The manifest is written once when a job is created and never modified.
 * It contains the job's command and retry configuration.
 *
 * @param id unique job identifier
 * @param command command and arguments to execute
 * @param tag optional user-defined tag
 * @param createdAt when the job was created
 * @param maxRetries maximum number of retry attempts
 * @param retryDelaySeconds initial delay before first retry
 * @param retryBackoffMultiplier multiplier for exponential backoff
 * @param loadLevel the load level classification for this job
 */
public record JobManifest(
    String id,
    List<String> command,
    String tag,
    Instant createdAt,
    int maxRetries,
    int retryDelaySeconds,
    double retryBackoffMultiplier,
    LoadLevel loadLevel
) {

    /**
     * Creates a JobManifest with validation.
     */
    public JobManifest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(loadLevel, "loadLevel must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
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
        command = List.copyOf(command);
    }

    /**
     * Creates a JobManifest with default retry configuration from JobsConfig.
     *
     * @param id the job ID
     * @param command the command to execute
     * @param tag optional tag (may be null)
     * @param config the jobs configuration for defaults
     * @return a new JobManifest with MEDIUM load level
     */
    public static JobManifest create(String id, List<String> command, String tag, JobsConfig config) {
        return create(id, command, tag, config, LoadLevel.getDefault());
    }

    /**
     * Creates a JobManifest with default retry configuration from JobsConfig.
     *
     * @param id the job ID
     * @param command the command to execute
     * @param tag optional tag (may be null)
     * @param config the jobs configuration for defaults
     * @param loadLevel the load level for this job
     * @return a new JobManifest
     */
    public static JobManifest create(String id, List<String> command, String tag,
                                     JobsConfig config, LoadLevel loadLevel) {
        Objects.requireNonNull(config, "config must not be null");
        return new JobManifest(
            id,
            command,
            tag,
            Instant.now(),
            config.maxRetries(),
            config.retryDelaySeconds(),
            config.retryBackoffMultiplier(),
            loadLevel
        );
    }

    /**
     * Creates a JobManifest with custom retry configuration.
     *
     * @param id the job ID
     * @param command the command to execute
     * @param tag optional tag (may be null)
     * @param maxRetries maximum retry attempts
     * @param retryDelaySeconds initial retry delay
     * @param retryBackoffMultiplier backoff multiplier
     * @return a new JobManifest with MEDIUM load level
     */
    public static JobManifest create(String id, List<String> command, String tag,
                                     int maxRetries, int retryDelaySeconds, double retryBackoffMultiplier) {
        return create(id, command, tag, maxRetries, retryDelaySeconds, retryBackoffMultiplier,
            LoadLevel.getDefault());
    }

    /**
     * Creates a JobManifest with custom retry configuration and load level.
     *
     * @param id the job ID
     * @param command the command to execute
     * @param tag optional tag (may be null)
     * @param maxRetries maximum retry attempts
     * @param retryDelaySeconds initial retry delay
     * @param retryBackoffMultiplier backoff multiplier
     * @param loadLevel the load level for this job
     * @return a new JobManifest
     */
    public static JobManifest create(String id, List<String> command, String tag,
                                     int maxRetries, int retryDelaySeconds, double retryBackoffMultiplier,
                                     LoadLevel loadLevel) {
        return new JobManifest(
            id,
            command,
            tag,
            Instant.now(),
            maxRetries,
            retryDelaySeconds,
            retryBackoffMultiplier,
            loadLevel
        );
    }

    /**
     * Calculates the delay for a specific retry attempt using exponential backoff.
     *
     * @param retryCount the current retry count (0-based)
     * @return the delay in seconds
     */
    public int calculateRetryDelay(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be non-negative");
        }
        return (int) (retryDelaySeconds * Math.pow(retryBackoffMultiplier, retryCount));
    }

    /**
     * Checks if another retry is allowed.
     *
     * @param currentRetryCount the current retry count
     * @return true if retries are available
     */
    public boolean canRetry(int currentRetryCount) {
        return currentRetryCount < maxRetries;
    }
}
