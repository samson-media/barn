package com.samsonmedia.barn.logging;

import java.util.Objects;

import org.slf4j.MDC;

import com.samsonmedia.barn.jobs.Job;

/**
 * Mapped Diagnostic Context (MDC) wrapper for job-scoped logging.
 *
 * <p>Provides automatic context management for job-related log messages.
 * Use with try-with-resources to ensure proper cleanup:
 *
 * <pre>{@code
 * try (var ctx = JobContext.forJob(job)) {
 *     LOG.info("Processing started");  // Automatically includes jobId
 * }
 * }</pre>
 */
public final class JobContext implements AutoCloseable {

    /** MDC key for job ID. */
    public static final String KEY_JOB_ID = "jobId";

    /** MDC key for job tag. */
    public static final String KEY_JOB_TAG = "jobTag";

    private JobContext() {
        // Private constructor - use factory method
    }

    /**
     * Creates a job context and sets MDC values.
     *
     * @param job the job to create context for
     * @return a new job context (must be closed)
     * @throws NullPointerException if job is null
     */
    public static JobContext forJob(Job job) {
        Objects.requireNonNull(job, "job must not be null");

        MDC.put(KEY_JOB_ID, job.id());
        if (job.tag() != null) {
            MDC.put(KEY_JOB_TAG, job.tag());
        }

        return new JobContext();
    }

    /**
     * Creates a job context with just the job ID.
     *
     * @param jobId the job ID
     * @return a new job context (must be closed)
     * @throws NullPointerException if jobId is null
     */
    public static JobContext forJobId(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");

        MDC.put(KEY_JOB_ID, jobId);
        return new JobContext();
    }

    /**
     * Gets the current job ID from MDC.
     *
     * @return the job ID, or null if not set
     */
    public static String getCurrentJobId() {
        return MDC.get(KEY_JOB_ID);
    }

    /**
     * Gets the current job tag from MDC.
     *
     * @return the job tag, or null if not set
     */
    public static String getCurrentJobTag() {
        return MDC.get(KEY_JOB_TAG);
    }

    @Override
    public void close() {
        MDC.remove(KEY_JOB_ID);
        MDC.remove(KEY_JOB_TAG);
    }
}
