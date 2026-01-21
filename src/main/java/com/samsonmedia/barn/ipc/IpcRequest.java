package com.samsonmedia.barn.ipc;

import java.util.List;

/**
 * Sealed interface for IPC requests from CLI to service.
 */
public sealed interface IpcRequest {

    /**
     * Request to run a new job.
     *
     * @param command the command and arguments
     * @param tag optional user-defined tag
     * @param maxRetries maximum retry attempts
     * @param retryDelaySeconds initial retry delay
     */
    record RunJob(
        List<String> command,
        String tag,
        int maxRetries,
        int retryDelaySeconds
    ) implements IpcRequest { }

    /**
     * Request to get job status list.
     *
     * @param tag filter by tag (null for all)
     * @param state filter by state (null for all)
     * @param limit maximum number of results (null for all)
     */
    record GetStatus(
        String tag,
        String state,
        Integer limit
    ) implements IpcRequest { }

    /**
     * Request to get detailed job information.
     *
     * @param jobId the job ID
     * @param includeLogs whether to include log content
     * @param includeManifest whether to include manifest details
     */
    record GetJob(
        String jobId,
        boolean includeLogs,
        boolean includeManifest
    ) implements IpcRequest { }

    /**
     * Request to kill a running job.
     *
     * @param jobId the job ID
     * @param force whether to force kill (SIGKILL)
     */
    record KillJob(
        String jobId,
        boolean force
    ) implements IpcRequest { }

    /**
     * Request to clean completed jobs.
     *
     * @param all remove all completed jobs regardless of age
     * @param olderThan duration string (e.g., "24h")
     * @param includeFailed include failed jobs
     * @param jobId specific job to clean (null for all matching)
     * @param dryRun show what would be cleaned without removing
     */
    record CleanJobs(
        boolean all,
        String olderThan,
        boolean includeFailed,
        String jobId,
        boolean dryRun
    ) implements IpcRequest { }

    /**
     * Request to get service status.
     */
    record GetServiceStatus() implements IpcRequest { }

    /**
     * Request to shutdown the service.
     */
    record Shutdown() implements IpcRequest { }

    /**
     * Request to reload configuration.
     */
    record Reload() implements IpcRequest { }
}
