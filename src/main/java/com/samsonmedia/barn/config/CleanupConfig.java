package com.samsonmedia.barn.config;

/**
 * Configuration for automatic job cleanup.
 *
 * @param enabled whether automatic cleanup is enabled
 * @param maxAgeHours remove jobs older than this
 * @param cleanupIntervalMinutes how often the cleanup task runs
 * @param keepFailedJobs preserve failed jobs for debugging
 * @param keepFailedJobsHours how long to keep failed jobs
 */
public record CleanupConfig(
    boolean enabled,
    int maxAgeHours,
    int cleanupIntervalMinutes,
    boolean keepFailedJobs,
    int keepFailedJobsHours
) {

    /** Default enabled state. */
    public static final boolean DEFAULT_ENABLED = true;

    /** Default max age in hours (3 days). */
    public static final int DEFAULT_MAX_AGE_HOURS = 72;

    /** Default cleanup interval in minutes. */
    public static final int DEFAULT_CLEANUP_INTERVAL_MINUTES = 60;

    /** Default keep failed jobs setting. */
    public static final boolean DEFAULT_KEEP_FAILED_JOBS = true;

    /** Default keep failed jobs hours (1 week). */
    public static final int DEFAULT_KEEP_FAILED_JOBS_HOURS = 168;

    /**
     * Creates a CleanupConfig with validation.
     */
    public CleanupConfig {
        if (maxAgeHours < 1) {
            throw new IllegalArgumentException("maxAgeHours must be at least 1");
        }
        if (cleanupIntervalMinutes < 1) {
            throw new IllegalArgumentException("cleanupIntervalMinutes must be at least 1");
        }
        if (keepFailedJobsHours < 1) {
            throw new IllegalArgumentException("keepFailedJobsHours must be at least 1");
        }
    }

    /**
     * Creates a CleanupConfig with default values.
     *
     * @return a CleanupConfig with all defaults
     */
    public static CleanupConfig withDefaults() {
        return new CleanupConfig(
            DEFAULT_ENABLED,
            DEFAULT_MAX_AGE_HOURS,
            DEFAULT_CLEANUP_INTERVAL_MINUTES,
            DEFAULT_KEEP_FAILED_JOBS,
            DEFAULT_KEEP_FAILED_JOBS_HOURS
        );
    }
}
