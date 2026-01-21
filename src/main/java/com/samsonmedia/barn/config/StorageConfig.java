package com.samsonmedia.barn.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for storage and filesystem settings.
 *
 * @param baseDir root directory for all Barn data
 * @param maxDiskUsageGb maximum disk space Barn can use
 * @param preserveWorkDir keep work/input and work/output after job completion
 */
public record StorageConfig(
    Path baseDir,
    int maxDiskUsageGb,
    boolean preserveWorkDir
) {

    /** Default max disk usage in GB. */
    public static final int DEFAULT_MAX_DISK_USAGE_GB = 50;

    /** Default preserve work dir setting. */
    public static final boolean DEFAULT_PRESERVE_WORK_DIR = false;

    /**
     * Creates a StorageConfig with validation.
     */
    public StorageConfig {
        Objects.requireNonNull(baseDir, "baseDir must not be null");
        if (maxDiskUsageGb < 1) {
            throw new IllegalArgumentException("maxDiskUsageGb must be at least 1");
        }
    }

    /**
     * Creates a StorageConfig with default values.
     *
     * @return a StorageConfig with all defaults
     */
    public static StorageConfig withDefaults() {
        return new StorageConfig(
            ConfigDefaults.getDefaultBaseDir(),
            DEFAULT_MAX_DISK_USAGE_GB,
            DEFAULT_PRESERVE_WORK_DIR
        );
    }
}
