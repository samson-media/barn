package com.samsonmedia.barn.config;

import java.util.Objects;

import com.samsonmedia.barn.jobs.LoadLevel;

/**
 * Configuration for per-load-level job limits.
 *
 * <p>This configuration controls how many jobs of each load level can run
 * concurrently. This allows the system to run many lightweight tasks (like
 * file downloads) while limiting resource-intensive tasks (like transcoding).
 *
 * @param maxHighJobs maximum concurrent high load jobs
 * @param maxMediumJobs maximum concurrent medium load jobs
 * @param maxLowJobs maximum concurrent low load jobs
 */
public record LoadLevelConfig(
    int maxHighJobs,
    int maxMediumJobs,
    int maxLowJobs
) {

    /** Default maximum high load jobs. */
    public static final int DEFAULT_MAX_HIGH_JOBS = LoadLevel.HIGH.getDefaultMaxJobs();

    /** Default maximum medium load jobs. */
    public static final int DEFAULT_MAX_MEDIUM_JOBS = LoadLevel.MEDIUM.getDefaultMaxJobs();

    /** Default maximum low load jobs. */
    public static final int DEFAULT_MAX_LOW_JOBS = LoadLevel.LOW.getDefaultMaxJobs();

    /**
     * Creates a LoadLevelConfig with validation.
     */
    public LoadLevelConfig {
        if (maxHighJobs < 1) {
            throw new IllegalArgumentException("maxHighJobs must be at least 1");
        }
        if (maxMediumJobs < 1) {
            throw new IllegalArgumentException("maxMediumJobs must be at least 1");
        }
        if (maxLowJobs < 1) {
            throw new IllegalArgumentException("maxLowJobs must be at least 1");
        }
    }

    /**
     * Creates a LoadLevelConfig with default values.
     *
     * @return a LoadLevelConfig with all defaults
     */
    public static LoadLevelConfig withDefaults() {
        return new LoadLevelConfig(
            DEFAULT_MAX_HIGH_JOBS,
            DEFAULT_MAX_MEDIUM_JOBS,
            DEFAULT_MAX_LOW_JOBS
        );
    }

    /**
     * Creates a LoadLevelConfig from a total limit (for backward compatibility).
     *
     * <p>Distributes the total limit proportionally based on default ratios:
     * HIGH gets ~5%, MEDIUM gets ~19%, LOW gets ~76% (matching default 2:8:32 ratio).
     *
     * @param totalLimit the total maximum concurrent jobs
     * @return a LoadLevelConfig with proportional limits
     * @deprecated Use explicit per-level limits instead
     */
    @Deprecated
    public static LoadLevelConfig fromTotalLimit(int totalLimit) {
        if (totalLimit < 3) {
            // Minimum: 1 for each level
            return new LoadLevelConfig(1, 1, 1);
        }

        // Use default ratio of 2:8:32 = 1:4:16
        int totalRatio = 1 + 4 + 16; // 21
        int high = Math.max(1, (totalLimit * 1) / totalRatio);
        int medium = Math.max(1, (totalLimit * 4) / totalRatio);
        int low = Math.max(1, totalLimit - high - medium);

        return new LoadLevelConfig(high, medium, low);
    }

    /**
     * Returns the maximum concurrent jobs for the specified load level.
     *
     * @param level the load level
     * @return the maximum concurrent jobs for that level
     */
    public int getMaxJobsFor(LoadLevel level) {
        Objects.requireNonNull(level, "level must not be null");
        return switch (level) {
            case HIGH -> maxHighJobs;
            case MEDIUM -> maxMediumJobs;
            case LOW -> maxLowJobs;
        };
    }

    /**
     * Returns the total maximum concurrent jobs across all levels.
     *
     * @return the sum of all level limits
     */
    public int getTotalMaxJobs() {
        return maxHighJobs + maxMediumJobs + maxLowJobs;
    }
}
