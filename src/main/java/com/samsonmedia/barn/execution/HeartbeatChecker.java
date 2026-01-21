package com.samsonmedia.barn.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.state.JobState;

/**
 * Checks if a job's heartbeat is stale.
 *
 * <p>A job is considered to have a stale heartbeat if:
 * <ul>
 *   <li>Its state is RUNNING</li>
 *   <li>Its heartbeat is null or older than the threshold</li>
 * </ul>
 */
public class HeartbeatChecker {

    private final Duration threshold;

    /**
     * Creates a HeartbeatChecker with the specified threshold.
     *
     * @param threshold the duration after which a heartbeat is considered stale
     * @throws NullPointerException if threshold is null
     * @throws IllegalArgumentException if threshold is zero or negative
     */
    public HeartbeatChecker(Duration threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        if (threshold.isZero() || threshold.isNegative()) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.threshold = threshold;
    }

    /**
     * Checks if a job's heartbeat is stale.
     *
     * <p>Only running jobs can have stale heartbeats. Jobs in other states
     * (queued, succeeded, failed, canceled) are never considered stale.
     *
     * @param job the job to check
     * @return true if the job is running and its heartbeat is stale
     * @throws NullPointerException if job is null
     */
    public boolean isStale(Job job) {
        Objects.requireNonNull(job, "job must not be null");

        // Only running jobs can be stale
        if (job.state() != JobState.RUNNING) {
            return false;
        }

        // No heartbeat means definitely stale
        if (job.heartbeat() == null) {
            return true;
        }

        // Check if heartbeat is older than threshold
        Instant cutoff = Instant.now().minus(threshold);
        return job.heartbeat().isBefore(cutoff);
    }

    /**
     * Returns the stale heartbeat threshold.
     *
     * @return the threshold duration
     */
    public Duration getThreshold() {
        return threshold;
    }
}
