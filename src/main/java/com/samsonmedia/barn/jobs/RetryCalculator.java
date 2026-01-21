package com.samsonmedia.barn.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import com.samsonmedia.barn.config.JobsConfig;

/**
 * Calculator for job retry logic with exponential backoff.
 *
 * <p>This class determines whether a failed job should be retried and calculates
 * the delay before the next retry attempt using exponential backoff.
 *
 * <p>The delay formula is: {@code delay = base_delay * (multiplier ^ retry_count)}
 *
 * <p>With default settings (30s base, 2.0x multiplier):
 * <ul>
 *   <li>Retry 1: 30 * 2^0 = 30 seconds</li>
 *   <li>Retry 2: 30 * 2^1 = 60 seconds</li>
 *   <li>Retry 3: 30 * 2^2 = 120 seconds</li>
 * </ul>
 *
 * <p>A small amount of jitter (±20%) is added to prevent thundering herd issues.
 */
public class RetryCalculator {

    private static final double JITTER_MIN = 0.8;
    private static final double JITTER_MAX = 1.2;
    private static final long MAX_DELAY_SECONDS = 3600; // 1 hour max delay

    private final JobsConfig config;

    /**
     * Creates a RetryCalculator with the specified configuration.
     *
     * @param config the jobs configuration
     */
    public RetryCalculator(JobsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Determines if a job should be retried based on its current state and configuration.
     *
     * @param job the job to check
     * @return true if the job should be retried
     */
    public boolean shouldRetry(Job job) {
        Objects.requireNonNull(job, "job must not be null");

        // Check if retries are enabled (maxRetries > 0)
        if (config.maxRetries() == 0) {
            return false;
        }

        // Check if we've exhausted all retries
        if (job.retryCount() >= config.maxRetries()) {
            return false;
        }

        // Check if the exit code is retryable
        if (job.exitCode() == null) {
            return false; // No exit code means job didn't complete normally
        }

        if (!isRetryableExitCode(job.exitCode())) {
            return false;
        }

        return true;
    }

    /**
     * Determines if a job should be retried using per-job override settings.
     *
     * @param job the job to check
     * @param maxRetries the per-job max retries override
     * @param retryExitCodes the per-job retry exit codes override (null = use config)
     * @return true if the job should be retried
     */
    public boolean shouldRetry(Job job, int maxRetries, List<Integer> retryExitCodes) {
        Objects.requireNonNull(job, "job must not be null");

        // Check if retries are enabled
        if (maxRetries == 0) {
            return false;
        }

        // Check if we've exhausted all retries
        if (job.retryCount() >= maxRetries) {
            return false;
        }

        // Check if the exit code is retryable
        if (job.exitCode() == null) {
            return false;
        }

        List<Integer> exitCodes = retryExitCodes != null ? retryExitCodes : config.retryOnExitCodes();
        if (!isRetryableExitCode(job.exitCode(), exitCodes)) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the delay duration before the next retry attempt.
     *
     * <p>Uses exponential backoff with optional jitter.
     *
     * @param retryCount the current retry count (0-based)
     * @return the delay duration
     */
    public Duration calculateDelay(int retryCount) {
        return calculateDelay(retryCount, config.retryDelaySeconds(), config.retryBackoffMultiplier());
    }

    /**
     * Calculates the delay duration with custom settings.
     *
     * @param retryCount the current retry count (0-based)
     * @param baseDelaySeconds the base delay in seconds
     * @param multiplier the backoff multiplier
     * @return the delay duration
     */
    public Duration calculateDelay(int retryCount, int baseDelaySeconds, double multiplier) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be non-negative");
        }
        if (baseDelaySeconds < 0) {
            throw new IllegalArgumentException("baseDelaySeconds must be non-negative");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be at least 1.0");
        }

        // Calculate base delay: delay = base_delay * (multiplier ^ retry_count)
        double delaySeconds = baseDelaySeconds * Math.pow(multiplier, retryCount);

        // Apply jitter to prevent thundering herd
        double jitter = ThreadLocalRandom.current().nextDouble(JITTER_MIN, JITTER_MAX);
        delaySeconds = delaySeconds * jitter;

        // Cap at maximum delay
        delaySeconds = Math.min(delaySeconds, MAX_DELAY_SECONDS);

        return Duration.ofSeconds((long) delaySeconds);
    }

    /**
     * Calculates the delay duration without jitter (for testing).
     *
     * @param retryCount the current retry count (0-based)
     * @return the delay duration without jitter
     */
    Duration calculateDelayWithoutJitter(int retryCount) {
        return calculateDelayWithoutJitter(retryCount, config.retryDelaySeconds(), config.retryBackoffMultiplier());
    }

    /**
     * Calculates the delay duration without jitter (for testing).
     *
     * @param retryCount the current retry count (0-based)
     * @param baseDelaySeconds the base delay in seconds
     * @param multiplier the backoff multiplier
     * @return the delay duration without jitter
     */
    Duration calculateDelayWithoutJitter(int retryCount, int baseDelaySeconds, double multiplier) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be non-negative");
        }

        double delaySeconds = baseDelaySeconds * Math.pow(multiplier, retryCount);
        delaySeconds = Math.min(delaySeconds, MAX_DELAY_SECONDS);
        return Duration.ofSeconds((long) delaySeconds);
    }

    /**
     * Calculates the instant when a job should be retried.
     *
     * @param job the job to schedule for retry
     * @return the retry timestamp
     */
    public Instant calculateRetryAt(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        Duration delay = calculateDelay(job.retryCount());
        return Instant.now().plus(delay);
    }

    /**
     * Calculates the instant when a job should be retried with custom settings.
     *
     * @param job the job to schedule for retry
     * @param baseDelaySeconds the base delay in seconds
     * @param multiplier the backoff multiplier
     * @return the retry timestamp
     */
    public Instant calculateRetryAt(Job job, int baseDelaySeconds, double multiplier) {
        Objects.requireNonNull(job, "job must not be null");
        Duration delay = calculateDelay(job.retryCount(), baseDelaySeconds, multiplier);
        return Instant.now().plus(delay);
    }

    /**
     * Gets the maximum number of retries from configuration.
     *
     * @return the maximum retries
     */
    public int getMaxRetries() {
        return config.maxRetries();
    }

    private boolean isRetryableExitCode(int exitCode) {
        return isRetryableExitCode(exitCode, config.retryOnExitCodes());
    }

    private boolean isRetryableExitCode(int exitCode, List<Integer> retryExitCodes) {
        // Exit code 0 is success, never retry
        if (exitCode == 0) {
            return false;
        }

        // If no exit codes specified, retry all non-zero exit codes
        if (retryExitCodes == null || retryExitCodes.isEmpty()) {
            return true;
        }

        // Check if exit code is in the retry list
        return retryExitCodes.contains(exitCode);
    }
}
