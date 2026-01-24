package com.samsonmedia.barn.config;

import java.util.Objects;

/**
 * Top-level configuration for the Barn application.
 *
 * @param service service daemon configuration
 * @param jobs job execution configuration
 * @param cleanup automatic cleanup configuration
 * @param storage storage and filesystem configuration
 * @param loadLevels per-load-level job limits configuration
 */
public record Config(
    ServiceConfig service,
    JobsConfig jobs,
    CleanupConfig cleanup,
    StorageConfig storage,
    LoadLevelConfig loadLevels
) {

    /**
     * Creates a Config with validation.
     */
    public Config {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(jobs, "jobs must not be null");
        Objects.requireNonNull(cleanup, "cleanup must not be null");
        Objects.requireNonNull(storage, "storage must not be null");
        Objects.requireNonNull(loadLevels, "loadLevels must not be null");
    }

    /**
     * Creates a Config with all default values.
     *
     * @return a Config with all defaults
     */
    public static Config withDefaults() {
        return new Config(
            ServiceConfig.withDefaults(),
            JobsConfig.withDefaults(),
            CleanupConfig.withDefaults(),
            StorageConfig.withDefaults(),
            LoadLevelConfig.withDefaults()
        );
    }
}
