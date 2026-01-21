package com.samsonmedia.barn.logging;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samsonmedia.barn.jobs.Job;

/**
 * Structured log events for job lifecycle.
 *
 * <p>Provides consistent, structured logging for all job-related events.
 */
public final class JobLogEvents {

    private static final Logger LOG = LoggerFactory.getLogger(JobLogEvents.class);

    private JobLogEvents() {
        // Utility class
    }

    /**
     * Logs a job creation event.
     *
     * @param job the created job
     * @throws NullPointerException if job is null
     */
    public static void jobCreated(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        LOG.info("Job created: id={} command={} tag={}",
            job.id(), formatCommand(job), job.tag());
    }

    /**
     * Logs a job started event.
     *
     * @param job the started job
     * @throws NullPointerException if job is null
     */
    public static void jobStarted(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        LOG.info("Job started: id={} pid={}",
            job.id(), job.pid());
    }

    /**
     * Logs a job completion event (success or failure).
     *
     * @param job the completed job
     * @param duration the job duration
     * @throws NullPointerException if job or duration is null
     */
    public static void jobCompleted(Job job, Duration duration) {
        Objects.requireNonNull(job, "job must not be null");
        Objects.requireNonNull(duration, "duration must not be null");

        if (job.exitCode() != null && job.exitCode() == 0) {
            LOG.info("Job succeeded: id={} duration={}ms",
                job.id(), duration.toMillis());
        } else {
            LOG.warn("Job failed: id={} exitCode={} error={}",
                job.id(), job.exitCode(), job.error());
        }
    }

    /**
     * Logs a job retry scheduled event.
     *
     * @param job the job being retried
     * @param retryAt when the retry will occur
     * @throws NullPointerException if job or retryAt is null
     */
    public static void jobRetryScheduled(Job job, Instant retryAt) {
        Objects.requireNonNull(job, "job must not be null");
        Objects.requireNonNull(retryAt, "retryAt must not be null");
        LOG.info("Job retry scheduled: id={} attempt={} retryAt={}",
            job.id(), job.retryCount() + 1, retryAt);
    }

    /**
     * Logs a job cancellation event.
     *
     * @param job the cancelled job
     * @throws NullPointerException if job is null
     */
    public static void jobCancelled(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        LOG.info("Job cancelled: id={}", job.id());
    }

    /**
     * Logs a job timeout event.
     *
     * @param job the timed-out job
     * @throws NullPointerException if job is null
     */
    public static void jobTimedOut(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        LOG.warn("Job timed out: id={}", job.id());
    }

    /**
     * Logs a job cleanup event.
     *
     * @param jobId the cleaned-up job ID
     * @throws NullPointerException if jobId is null
     */
    public static void jobCleaned(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        LOG.debug("Job cleaned: id={}", jobId);
    }

    private static String formatCommand(Job job) {
        if (job.command() == null || job.command().isEmpty()) {
            return "[]";
        }
        if (job.command().size() == 1) {
            return job.command().get(0);
        }
        return String.join(" ", job.command());
    }
}
