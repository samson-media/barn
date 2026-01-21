package com.samsonmedia.barn.jobs;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.samsonmedia.barn.state.JobState;

/**
 * Represents a job in the Barn system.
 *
 * <p>A job is an external command to be executed by the daemon. This record
 * captures the current state and metadata of a job.
 *
 * @param id unique identifier (e.g., "job-9f83c")
 * @param state current lifecycle state
 * @param command command and arguments to execute
 * @param tag optional user-defined tag
 * @param createdAt when the job was created
 * @param startedAt when execution started (null if not started)
 * @param finishedAt when execution finished (null if not finished)
 * @param exitCode process exit code (null if not finished)
 * @param error error message (null if no error)
 * @param pid OS process ID (null if not started)
 * @param heartbeat last liveness check timestamp
 * @param retryCount current retry attempt (0-based)
 * @param retryAt when to retry (null if not scheduled for retry)
 */
public record Job(
    String id,
    JobState state,
    List<String> command,
    String tag,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    Integer exitCode,
    String error,
    Long pid,
    Instant heartbeat,
    int retryCount,
    Instant retryAt
) {

    /**
     * Creates a Job with validation.
     */
    public Job {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be non-negative");
        }
        command = List.copyOf(command);
    }

    /**
     * Creates a new queued job with minimal required fields.
     *
     * @param id the job ID
     * @param command the command to execute
     * @param tag optional tag (may be null)
     * @return a new Job in QUEUED state
     */
    public static Job createQueued(String id, List<String> command, String tag) {
        return new Job(
            id,
            JobState.QUEUED,
            command,
            tag,
            Instant.now(),
            null,  // startedAt
            null,  // finishedAt
            null,  // exitCode
            null,  // error
            null,  // pid
            null,  // heartbeat
            0,     // retryCount
            null   // retryAt
        );
    }

    /**
     * Creates a copy of this job with a new state.
     *
     * @param newState the new state
     * @return a new Job with the updated state
     */
    public Job withState(JobState newState) {
        return new Job(id, newState, command, tag, createdAt, startedAt, finishedAt,
            exitCode, error, pid, heartbeat, retryCount, retryAt);
    }

    /**
     * Creates a copy of this job marked as started.
     *
     * @param processId the OS process ID
     * @return a new Job in RUNNING state
     */
    public Job withStarted(long processId) {
        Instant now = Instant.now();
        return new Job(id, JobState.RUNNING, command, tag, createdAt, now, finishedAt,
            exitCode, error, processId, now, retryCount, retryAt);
    }

    /**
     * Creates a copy of this job marked as completed.
     *
     * @param code the exit code
     * @param errorMessage optional error message (may be null)
     * @return a new Job in terminal state
     */
    public Job withCompleted(int code, String errorMessage) {
        JobState newState = (code == 0) ? JobState.SUCCEEDED : JobState.FAILED;
        return new Job(id, newState, command, tag, createdAt, startedAt, Instant.now(),
            code, errorMessage, pid, heartbeat, retryCount, retryAt);
    }

    /**
     * Creates a copy of this job with updated heartbeat.
     *
     * @param timestamp the heartbeat timestamp
     * @return a new Job with updated heartbeat
     */
    public Job withHeartbeat(Instant timestamp) {
        return new Job(id, state, command, tag, createdAt, startedAt, finishedAt,
            exitCode, error, pid, timestamp, retryCount, retryAt);
    }

    /**
     * Creates a copy of this job scheduled for retry.
     *
     * @param retryTimestamp when to retry
     * @return a new Job in QUEUED state with incremented retry count
     */
    public Job withRetry(Instant retryTimestamp) {
        return new Job(id, JobState.QUEUED, command, tag, createdAt, null, null,
            null, null, null, null, retryCount + 1, retryTimestamp);
    }

    /**
     * Creates a copy of this job marked as canceled.
     *
     * @return a new Job in CANCELED state
     */
    public Job withCanceled() {
        return new Job(id, JobState.CANCELED, command, tag, createdAt, startedAt, Instant.now(),
            exitCode, "Job was canceled", pid, heartbeat, retryCount, retryAt);
    }

    /**
     * Checks if this job is in a terminal state.
     *
     * @return true if the job is finished
     */
    public boolean isTerminal() {
        return state.isTerminal();
    }

    /**
     * Checks if this job is actively running.
     *
     * @return true if the job is running
     */
    public boolean isRunning() {
        return state == JobState.RUNNING;
    }

    /**
     * Checks if this job is waiting to be executed.
     *
     * @return true if the job is queued
     */
    public boolean isQueued() {
        return state == JobState.QUEUED;
    }
}
