package com.samsonmedia.barn.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the Barn service daemon.
 *
 * @param logLevel the logging verbosity level
 * @param maxConcurrentJobs maximum number of jobs running simultaneously
 * @param heartbeatIntervalSeconds how often running jobs update their heartbeat
 * @param ipcSocket path to the IPC socket for CLI communication
 * @param staleHeartbeatThresholdSeconds heartbeat age before a job is considered orphaned
 */
public record ServiceConfig(
    LogLevel logLevel,
    int maxConcurrentJobs,
    int heartbeatIntervalSeconds,
    Path ipcSocket,
    int staleHeartbeatThresholdSeconds
) {

    /** Default log level. */
    public static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.INFO;

    /** Default maximum concurrent jobs. */
    public static final int DEFAULT_MAX_CONCURRENT_JOBS = 4;

    /** Default heartbeat interval in seconds. */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 5;

    /** Default stale heartbeat threshold in seconds. */
    public static final int DEFAULT_STALE_HEARTBEAT_THRESHOLD_SECONDS = 30;

    /**
     * Creates a ServiceConfig with validation.
     */
    public ServiceConfig {
        Objects.requireNonNull(logLevel, "logLevel must not be null");
        Objects.requireNonNull(ipcSocket, "ipcSocket must not be null");
        if (maxConcurrentJobs < 1) {
            throw new IllegalArgumentException("maxConcurrentJobs must be at least 1");
        }
        if (heartbeatIntervalSeconds < 1) {
            throw new IllegalArgumentException("heartbeatIntervalSeconds must be at least 1");
        }
        if (staleHeartbeatThresholdSeconds < 1) {
            throw new IllegalArgumentException("staleHeartbeatThresholdSeconds must be at least 1");
        }
    }

    /**
     * Creates a ServiceConfig with default values.
     *
     * @return a ServiceConfig with all defaults
     */
    public static ServiceConfig withDefaults() {
        return new ServiceConfig(
            DEFAULT_LOG_LEVEL,
            DEFAULT_MAX_CONCURRENT_JOBS,
            DEFAULT_HEARTBEAT_INTERVAL_SECONDS,
            ConfigDefaults.getDefaultIpcSocket(),
            DEFAULT_STALE_HEARTBEAT_THRESHOLD_SECONDS
        );
    }
}
