package com.samsonmedia.barn.execution;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.samsonmedia.barn.config.Config;
import com.samsonmedia.barn.config.LoadLevelConfig;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.jobs.LoadLevel;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;

/**
 * Scheduler for managing concurrent job execution.
 *
 * <p>The scheduler polls for queued jobs and executes them respecting
 * the configured maximum concurrent jobs limit.
 */
public class JobScheduler {

    private static final BarnLogger LOG = BarnLogger.getLogger(JobScheduler.class);
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofMinutes(5);

    private final JobRepository repository;
    private final JobRunner runner;
    private final BarnDirectories dirs;
    private final LoadLevelConfig loadLevelConfig;
    private final Duration pollInterval;
    private final Duration shutdownTimeout;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<LoadLevel, AtomicInteger> runningJobCounts;

    private ExecutorService jobExecutor;
    private ScheduledExecutorService schedulerThread;
    private SchedulerLock lock;

    /**
     * Creates a JobScheduler with the specified configuration.
     *
     * @param repository the job repository
     * @param runner the job runner
     * @param dirs the directory manager
     * @param config the configuration
     */
    public JobScheduler(
            JobRepository repository,
            JobRunner runner,
            BarnDirectories dirs,
            Config config) {
        this(repository, runner, dirs,
            config.loadLevels(),
            DEFAULT_POLL_INTERVAL,
            DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Creates a JobScheduler with custom settings using legacy single-limit mode.
     *
     * <p>In legacy mode, all jobs are treated as MEDIUM load level, and the
     * maxConcurrentJobs limit applies only to MEDIUM jobs. HIGH and LOW levels
     * use their default limits.
     *
     * @param repository the job repository
     * @param runner the job runner
     * @param dirs the directory manager
     * @param maxConcurrentJobs the maximum number of concurrent jobs
     * @param pollInterval the interval between queue checks
     * @param shutdownTimeout the maximum time to wait for shutdown
     * @deprecated Use the constructor with LoadLevelConfig instead
     */
    @Deprecated
    public JobScheduler(
            JobRepository repository,
            JobRunner runner,
            BarnDirectories dirs,
            int maxConcurrentJobs,
            Duration pollInterval,
            Duration shutdownTimeout) {
        this(repository, runner, dirs,
            createLegacyConfig(maxConcurrentJobs),
            pollInterval,
            shutdownTimeout);
    }

    private static LoadLevelConfig createLegacyConfig(int maxConcurrentJobs) {
        if (maxConcurrentJobs <= 0) {
            throw new IllegalArgumentException("maxConcurrentJobs must be positive: " + maxConcurrentJobs);
        }
        // In legacy mode, apply limit to MEDIUM only, use minimum for others
        return new LoadLevelConfig(1, maxConcurrentJobs, 1);
    }

    /**
     * Creates a JobScheduler with per-level limits.
     *
     * @param repository the job repository
     * @param runner the job runner
     * @param dirs the directory manager
     * @param loadLevelConfig the per-level job limits
     * @param pollInterval the interval between queue checks
     * @param shutdownTimeout the maximum time to wait for shutdown
     */
    public JobScheduler(
            JobRepository repository,
            JobRunner runner,
            BarnDirectories dirs,
            LoadLevelConfig loadLevelConfig,
            Duration pollInterval,
            Duration shutdownTimeout) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.dirs = Objects.requireNonNull(dirs, "dirs must not be null");
        this.loadLevelConfig = Objects.requireNonNull(loadLevelConfig, "loadLevelConfig must not be null");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout must not be null");

        // Initialize per-level running job counts
        this.runningJobCounts = new EnumMap<>(LoadLevel.class);
        for (LoadLevel level : LoadLevel.values()) {
            runningJobCounts.put(level, new AtomicInteger(0));
        }
    }

    /**
     * Starts the scheduler.
     *
     * <p>Acquires the scheduler lock and begins processing queued jobs.
     *
     * @throws IllegalStateException if already running or lock cannot be acquired
     * @throws IOException if lock acquisition fails
     */
    public void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Scheduler is already running");
        }

        // Acquire lock
        Optional<SchedulerLock> acquired = SchedulerLock.tryAcquire(dirs.getSchedulerLockFile());
        if (acquired.isEmpty()) {
            throw new IllegalStateException("Another scheduler is already running");
        }
        lock = acquired.get();

        int totalMaxJobs = loadLevelConfig.getTotalMaxJobs();

        // Create executors
        jobExecutor = Executors.newFixedThreadPool(totalMaxJobs, r -> {
            Thread t = new Thread(r, "job-executor");
            t.setDaemon(true);
            return t;
        });

        schedulerThread = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-scheduler");
            t.setDaemon(true);
            return t;
        });

        running.set(true);

        // Start the scheduling loop
        schedulerThread.scheduleAtFixedRate(
            this::scheduleNextJob,
            0,
            pollInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );

        LOG.info("Scheduler started with per-level limits: HIGH={}, MEDIUM={}, LOW={} (total={})",
            loadLevelConfig.maxHighJobs(),
            loadLevelConfig.maxMediumJobs(),
            loadLevelConfig.maxLowJobs(),
            totalMaxJobs);
    }

    /**
     * Stops the scheduler gracefully.
     *
     * <p>Waits for running jobs to complete up to the shutdown timeout.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        LOG.info("Stopping scheduler gracefully...");

        // Stop scheduling new jobs
        if (schedulerThread != null) {
            schedulerThread.shutdown();
        }

        // Wait for running jobs
        if (jobExecutor != null) {
            jobExecutor.shutdown();
            try {
                if (!jobExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    LOG.warn("Timeout waiting for jobs, forcing shutdown");
                    jobExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                jobExecutor.shutdownNow();
            }
        }

        // Release lock
        if (lock != null) {
            lock.close();
            lock = null;
        }

        LOG.info("Scheduler stopped");
    }

    /**
     * Stops the scheduler immediately.
     *
     * <p>Kills any running jobs without waiting.
     */
    public void stopNow() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        LOG.info("Stopping scheduler immediately...");

        // Kill everything
        if (schedulerThread != null) {
            schedulerThread.shutdownNow();
        }

        if (jobExecutor != null) {
            jobExecutor.shutdownNow();
        }

        // Release lock
        if (lock != null) {
            lock.close();
            lock = null;
        }

        LOG.info("Scheduler stopped");
    }

    /**
     * Submits a job for execution.
     *
     * <p>The job will be picked up by the scheduler in FIFO order.
     *
     * @param job the job to submit
     */
    public void submit(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        // Jobs are already in the repository with QUEUED state
        // The scheduler will pick them up in the next poll
        LOG.debug("Job submitted for scheduling: {}", job.id());
    }

    /**
     * Gets the current scheduler status.
     *
     * @return the scheduler status
     */
    public SchedulerStatus getStatus() {
        int queuedCount = 0;
        try {
            queuedCount = repository.findByState(JobState.QUEUED).size();
        } catch (IOException e) {
            LOG.warn("Failed to count queued jobs: {}", e.getMessage());
        }

        int totalRunning = runningJobCounts.values().stream()
            .mapToInt(AtomicInteger::get)
            .sum();

        return new SchedulerStatus(
            totalRunning,
            queuedCount,
            loadLevelConfig.getTotalMaxJobs(),
            running.get(),
            runningJobCounts.get(LoadLevel.HIGH).get(),
            runningJobCounts.get(LoadLevel.MEDIUM).get(),
            runningJobCounts.get(LoadLevel.LOW).get(),
            loadLevelConfig.maxHighJobs(),
            loadLevelConfig.maxMediumJobs(),
            loadLevelConfig.maxLowJobs()
        );
    }

    /**
     * Checks if the scheduler is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    private void scheduleNextJob() {
        if (!running.get()) {
            return;
        }

        try {
            // Find next queued job that has capacity in its load level
            Optional<Job> nextJob = findNextQueuedWithCapacity();
            if (nextJob.isEmpty()) {
                return;
            }

            Job job = nextJob.get();
            LoadLevel level = job.loadLevel();

            // Check if job is ready (not scheduled for later retry)
            if (job.retryAt() != null && job.retryAt().isAfter(Instant.now())) {
                return;
            }

            // Increment count for this level and submit
            runningJobCounts.get(level).incrementAndGet();

            jobExecutor.submit(() -> {
                try {
                    runner.run(job);
                } finally {
                    runningJobCounts.get(level).decrementAndGet();
                }
            });

            LOG.debug("Scheduled job: {} (level={})", job.id(), level);

        } catch (IOException e) {
            LOG.error("Error in scheduler: {}", e.getMessage(), e);
        }
    }

    private Optional<Job> findNextQueuedWithCapacity() throws IOException {
        List<Job> queuedJobs = repository.findByState(JobState.QUEUED);

        // Sort by creation time (FIFO) and filter by retry time and capacity
        return queuedJobs.stream()
            .filter(job -> job.retryAt() == null || !job.retryAt().isAfter(Instant.now()))
            .filter(this::hasCapacityForLevel)
            .min(Comparator.comparing(Job::createdAt));
    }

    private boolean hasCapacityForLevel(Job job) {
        LoadLevel level = job.loadLevel();
        int currentRunning = runningJobCounts.get(level).get();
        int maxForLevel = loadLevelConfig.getMaxJobsFor(level);
        return currentRunning < maxForLevel;
    }

    /**
     * Status of the job scheduler.
     *
     * @param runningJobs the total number of currently running jobs
     * @param queuedJobs the number of queued jobs
     * @param maxConcurrentJobs the total maximum concurrent job limit
     * @param isRunning whether the scheduler is running
     * @param runningHighJobs running HIGH load level jobs
     * @param runningMediumJobs running MEDIUM load level jobs
     * @param runningLowJobs running LOW load level jobs
     * @param maxHighJobs maximum HIGH load level jobs
     * @param maxMediumJobs maximum MEDIUM load level jobs
     * @param maxLowJobs maximum LOW load level jobs
     */
    public record SchedulerStatus(
        int runningJobs,
        int queuedJobs,
        int maxConcurrentJobs,
        boolean isRunning,
        int runningHighJobs,
        int runningMediumJobs,
        int runningLowJobs,
        int maxHighJobs,
        int maxMediumJobs,
        int maxLowJobs
    ) {
        /**
         * Creates a SchedulerStatus with only legacy fields (for backward compatibility).
         *
         * @param runningJobs the total number of currently running jobs
         * @param queuedJobs the number of queued jobs
         * @param maxConcurrentJobs the total maximum concurrent job limit
         * @param isRunning whether the scheduler is running
         */
        public SchedulerStatus(int runningJobs, int queuedJobs, int maxConcurrentJobs, boolean isRunning) {
            this(runningJobs, queuedJobs, maxConcurrentJobs, isRunning, 0, 0, 0, 0, 0, 0);
        }
    }
}
