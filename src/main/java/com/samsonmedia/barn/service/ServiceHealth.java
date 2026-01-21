package com.samsonmedia.barn.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Health status of the barn service.
 *
 * @param running whether the service is running
 * @param activeJobs number of currently running jobs
 * @param queuedJobs number of jobs waiting to run
 * @param startedAt when the service was started
 * @param uptimeSeconds how long the service has been running
 */
public record ServiceHealth(
    boolean running,
    int activeJobs,
    int queuedJobs,
    Instant startedAt,
    long uptimeSeconds
) {

    /**
     * Creates a health record for a running service.
     *
     * @param activeJobs number of active jobs
     * @param queuedJobs number of queued jobs
     * @param startedAt when service started
     * @return the health record
     */
    public static ServiceHealth running(int activeJobs, int queuedJobs, Instant startedAt) {
        long uptime = Duration.between(startedAt, Instant.now()).getSeconds();
        return new ServiceHealth(true, activeJobs, queuedJobs, startedAt, uptime);
    }

    /**
     * Creates a health record for a stopped service.
     *
     * @return the health record
     */
    public static ServiceHealth stopped() {
        return new ServiceHealth(false, 0, 0, null, 0);
    }
}
