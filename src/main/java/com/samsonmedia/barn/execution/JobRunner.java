package com.samsonmedia.barn.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.jobs.RetryCalculator;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.monitoring.UsageMonitor;
import com.samsonmedia.barn.state.BarnDirectories;

/**
 * Runs jobs by executing their commands and managing their lifecycle.
 *
 * <p>The JobRunner handles:
 * <ul>
 *   <li>Starting the process</li>
 *   <li>Updating state to RUNNING</li>
 *   <li>Writing PID and heartbeat</li>
 *   <li>Monitoring the process</li>
 *   <li>Capturing exit code and errors</li>
 *   <li>Updating final state (SUCCEEDED/FAILED)</li>
 * </ul>
 */
public class JobRunner {

    private static final BarnLogger LOG = BarnLogger.getLogger(JobRunner.class);
    private static final String STDOUT_LOG = "stdout.log";
    private static final String STDERR_LOG = "stderr.log";

    private final ProcessExecutor executor;
    private final ProcessMonitor monitor;
    private final JobRepository repository;
    private final BarnDirectories dirs;
    private final RetryCalculator retryCalculator;

    /**
     * Creates a JobRunner.
     *
     * @param executor the process executor
     * @param monitor the process monitor
     * @param repository the job repository
     * @param dirs the directory manager
     * @param retryCalculator the retry calculator
     */
    public JobRunner(
            ProcessExecutor executor,
            ProcessMonitor monitor,
            JobRepository repository,
            BarnDirectories dirs,
            RetryCalculator retryCalculator) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.monitor = Objects.requireNonNull(monitor, "monitor must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.dirs = Objects.requireNonNull(dirs, "dirs must not be null");
        this.retryCalculator = retryCalculator; // May be null to disable retries
    }

    /**
     * Creates a JobRunner with default executor and monitor.
     *
     * @param repository the job repository
     * @param dirs the directory manager
     */
    public JobRunner(JobRepository repository, BarnDirectories dirs) {
        this(new ProcessExecutor(), new ProcessMonitor(), repository, dirs, null);
    }

    /**
     * Creates a JobRunner with default executor, monitor, and retry support.
     *
     * @param repository the job repository
     * @param dirs the directory manager
     * @param jobsConfig the jobs configuration for retry settings
     */
    public JobRunner(JobRepository repository, BarnDirectories dirs, JobsConfig jobsConfig) {
        this(new ProcessExecutor(), new ProcessMonitor(), repository, dirs,
            jobsConfig != null ? new RetryCalculator(jobsConfig) : null);
    }

    /**
     * Runs a job synchronously.
     *
     * <p>This method blocks until the job completes.
     *
     * @param job the job to run
     * @return the result of running the job
     */
    public JobResult run(Job job) {
        Objects.requireNonNull(job, "job must not be null");

        LOG.info("Starting job: {}", job.id());
        Instant startTime = Instant.now();
        AtomicBoolean completed = new AtomicBoolean(false);

        try {
            // Get paths
            var logsDir = dirs.getJobLogsDir(job.id());
            var workDir = dirs.getJobWorkDir(job.id());
            var stdoutFile = logsDir.resolve(STDOUT_LOG);
            var stderrFile = logsDir.resolve(STDERR_LOG);

            // Start process
            Process process = executor.execute(
                job.command(),
                workDir,
                stdoutFile,
                stderrFile
            );

            long pid = process.pid();

            // Update state to RUNNING
            repository.markStarted(job.id(), pid);

            // Start usage monitoring
            UsageMonitor usageMonitor = new UsageMonitor(pid, workDir, logsDir);
            usageMonitor.start();

            try {
                // Monitor the process with heartbeat updates
                ProcessMonitor.ProcessEvent.Completed completedEvent = monitor.monitorBlocking(
                    process,
                    event -> handleEvent(job.id(), event)
                );

                completed.set(true);

                // Update final state
                int exitCode = completedEvent.exitCode();
                String error = exitCode == 0 ? null : "Process exited with code " + exitCode;

                // Check if we should retry
                if (exitCode != 0 && shouldRetry(job, exitCode)) {
                    handleRetry(job, exitCode, error);
                    Duration duration = Duration.between(startTime, Instant.now());
                    LOG.info("Job {} failed with exit code {} and scheduled for retry in {}",
                        job.id(), exitCode, formatDuration(duration));
                    return new JobResult(exitCode, duration, error, true);
                }

                repository.markCompleted(job.id(), exitCode, error);

                Duration duration = Duration.between(startTime, Instant.now());
                LOG.info("Job {} completed with exit code {} in {}", job.id(), exitCode, formatDuration(duration));

                return new JobResult(exitCode, duration, error, false);
            } finally {
                // Always stop and close usage monitoring
                try {
                    usageMonitor.close();
                } catch (IOException e) {
                    LOG.warn("Failed to close usage monitor for job {}: {}", job.id(), e.getMessage());
                }
            }

        } catch (IOException e) {
            LOG.error("Failed to start job {}: {}", job.id(), e.getMessage(), e);

            // Write error to stderr.log so it appears in --logs output
            try {
                var stderrFile = dirs.getJobLogsDir(job.id()).resolve(STDERR_LOG);
                Files.createDirectories(stderrFile.getParent());
                Files.writeString(stderrFile, "Failed to start process: " + e.getMessage() + "\n");
            } catch (IOException writeError) {
                LOG.warn("Failed to write error to stderr.log for job {}: {}", job.id(), writeError.getMessage());
            }

            if (!completed.get()) {
                try {
                    repository.markFailed(job.id(), "start_failed", e.getMessage());
                } catch (IOException updateError) {
                    LOG.error("Failed to mark job {} as failed: {}", job.id(), updateError.getMessage());
                }
            }

            Duration duration = Duration.between(startTime, Instant.now());
            return new JobResult(-1, duration, e.getMessage(), false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Job {} was interrupted", job.id());

            if (!completed.get()) {
                try {
                    repository.markFailed(job.id(), "interrupted", "Job was interrupted");
                } catch (IOException updateError) {
                    LOG.error("Failed to mark job {} as failed: {}", job.id(), updateError.getMessage());
                }
            }

            Duration duration = Duration.between(startTime, Instant.now());
            return new JobResult(-1, duration, "Job was interrupted", false);
        }
    }

    private boolean shouldRetry(Job job, int exitCode) {
        if (retryCalculator == null) {
            return false;
        }
        // Create a temporary job with the exit code to check retry eligibility
        Job jobWithExitCode = new Job(
            job.id(), job.state(), job.command(), job.tag(),
            job.createdAt(), job.startedAt(), job.finishedAt(),
            exitCode, job.error(), job.pid(), job.heartbeat(),
            job.retryCount(), job.retryAt()
        );
        return retryCalculator.shouldRetry(jobWithExitCode);
    }

    private void handleRetry(Job job, int exitCode, String error) throws IOException {
        Instant retryAt = retryCalculator.calculateRetryAt(job);
        repository.scheduleRetry(job.id(), retryAt, exitCode, error);
        LOG.info("Job {} retry {} of {} scheduled for {}",
            job.id(), job.retryCount() + 1, retryCalculator.getMaxRetries(), retryAt);
    }

    private void handleEvent(String jobId, ProcessMonitor.ProcessEvent event) {
        try {
            switch (event) {
                case ProcessMonitor.ProcessEvent.Started started -> {
                    LOG.debug("Job {} started with PID {}", jobId, started.pid());
                }
                case ProcessMonitor.ProcessEvent.Heartbeat heartbeat -> {
                    LOG.trace("Job {} heartbeat at {}", jobId, heartbeat.timestamp());
                    repository.updateHeartbeat(jobId, heartbeat.timestamp());
                }
                case ProcessMonitor.ProcessEvent.Completed completed -> {
                    LOG.debug("Job {} completed with exit code {}", jobId, completed.exitCode());
                }
                case ProcessMonitor.ProcessEvent.Failed failed -> {
                    LOG.error("Job {} failed: {}", jobId, failed.error().getMessage());
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to handle event for job {}: {}", jobId, e.getMessage(), e);
        }
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        } else {
            return String.format("%dh %dm %ds", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }
    }

    /**
     * Result of running a job.
     *
     * @param exitCode the process exit code (-1 if process failed to start)
     * @param duration the total runtime
     * @param error the error message, if any
     * @param retryScheduled true if a retry was scheduled
     */
    public record JobResult(int exitCode, Duration duration, String error, boolean retryScheduled) {

        /**
         * Creates a JobResult without retry information (defaults to false).
         *
         * @param exitCode the process exit code
         * @param duration the total runtime
         * @param error the error message, if any
         */
        public JobResult(int exitCode, Duration duration, String error) {
            this(exitCode, duration, error, false);
        }

        /**
         * Returns true if the job completed successfully.
         *
         * @return true if exit code is 0
         */
        public boolean isSuccess() {
            return exitCode == 0;
        }

        /**
         * Returns true if the job failed but a retry was scheduled.
         *
         * @return true if a retry is pending
         */
        public boolean isRetryPending() {
            return !isSuccess() && retryScheduled;
        }
    }
}
