package com.samsonmedia.barn.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.samsonmedia.barn.config.CleanupConfig;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.state.JobState;

/**
 * Scheduler for periodic cleanup of old completed jobs.
 */
public class CleanupScheduler {

    private static final BarnLogger LOG = BarnLogger.getLogger(CleanupScheduler.class);

    private final JobRepository repository;
    private final CleanupConfig config;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running;

    /**
     * Creates a cleanup scheduler.
     *
     * @param repository the job repository
     * @param config the cleanup configuration
     */
    public CleanupScheduler(JobRepository repository, CleanupConfig config) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cleanup-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }

    /**
     * Starts the cleanup scheduler.
     */
    public void start() {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Cleanup scheduler already running");
        }

        Duration interval = Duration.ofMinutes(config.cleanupIntervalMinutes());
        LOG.info("Starting cleanup scheduler with interval: {} minutes", config.cleanupIntervalMinutes());

        executor.scheduleAtFixedRate(
            this::runCleanup,
            interval.toMinutes(),
            interval.toMinutes(),
            TimeUnit.MINUTES
        );
    }

    /**
     * Stops the cleanup scheduler.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        LOG.info("Stopping cleanup scheduler");
        executor.shutdownNow();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Cleanup scheduler did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while stopping cleanup scheduler");
        }
    }

    /**
     * Checks if the scheduler is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Runs cleanup immediately (for testing).
     */
    public void runNow() {
        runCleanup();
    }

    private void runCleanup() {
        LOG.debug("Running cleanup");

        try {
            List<Job> jobs = repository.findAll();
            int cleaned = 0;

            for (Job job : jobs) {
                if (shouldClean(job)) {
                    repository.delete(job.id());
                    cleaned++;
                    LOG.debug("Cleaned job: {}", job.id());
                }
            }

            if (cleaned > 0) {
                LOG.info("Cleaned {} jobs", cleaned);
            }

        } catch (IOException e) {
            LOG.error("Cleanup failed: {}", e.getMessage(), e);
        }
    }

    private boolean shouldClean(Job job) {
        // Never clean active jobs
        if (job.state() == JobState.RUNNING || job.state() == JobState.QUEUED) {
            return false;
        }

        Instant finished = job.finishedAt();
        if (finished == null) {
            finished = job.createdAt();
        }

        Duration age = Duration.between(finished, Instant.now());
        Duration maxAge;

        if (job.state() == JobState.FAILED && config.keepFailedJobs()) {
            maxAge = Duration.ofHours(config.keepFailedJobsHours());
        } else {
            maxAge = Duration.ofHours(config.maxAgeHours());
        }

        return age.compareTo(maxAge) > 0;
    }
}
