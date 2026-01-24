package com.samsonmedia.barn.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samsonmedia.barn.config.Config;
import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.execution.CrashRecovery;
import com.samsonmedia.barn.execution.HeartbeatChecker;
import com.samsonmedia.barn.execution.JobRunner;
import com.samsonmedia.barn.execution.JobScheduler;
import com.samsonmedia.barn.ipc.IpcException;
import com.samsonmedia.barn.ipc.IpcServer;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.jobs.LoadLevel;
import com.samsonmedia.barn.jobs.LoadLevelClassifier;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.logging.LoggingConfig;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;

/**
 * Main service daemon that orchestrates all barn components.
 *
 * <p>The service coordinates:
 * <ul>
 *   <li>Job scheduling and execution</li>
 *   <li>IPC server for CLI communication</li>
 *   <li>Periodic cleanup of old jobs</li>
 *   <li>Crash recovery on startup</li>
 * </ul>
 */
public class BarnService {

    private static final BarnLogger LOG = BarnLogger.getLogger(BarnService.class);

    private final Config config;
    private final BarnDirectories dirs;
    private final JobRepository repository;
    private final JobRunner runner;
    private final JobScheduler scheduler;
    private final CleanupScheduler cleanupScheduler;
    private final CrashRecovery crashRecovery;
    private final LoadLevelClassifier loadLevelClassifier;
    private final CountDownLatch shutdownLatch;
    private final AtomicBoolean running;
    private final ObjectMapper objectMapper;
    private IpcServer ipcServer;
    private Instant startedAt;

    /**
     * Creates a new barn service.
     *
     * @param config the service configuration
     */
    public BarnService(Config config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.dirs = new BarnDirectories(config.storage().baseDir());
        this.repository = new JobRepository(dirs);
        this.runner = new JobRunner(repository, dirs);
        this.scheduler = new JobScheduler(repository, runner, dirs, config);
        this.cleanupScheduler = new CleanupScheduler(repository, config.cleanup());

        Duration heartbeatThreshold = Duration.ofSeconds(config.service().staleHeartbeatThresholdSeconds());
        HeartbeatChecker heartbeatChecker = new HeartbeatChecker(heartbeatThreshold);
        this.crashRecovery = new CrashRecovery(repository, heartbeatChecker, dirs, config);

        // Load classifier from config directory (if .load files exist)
        this.loadLevelClassifier = LoadLevelClassifier.loadFromConfigDir();

        this.shutdownLatch = new CountDownLatch(1);
        this.running = new AtomicBoolean(false);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Starts the service.
     *
     * @throws IOException if startup fails
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Service already running");
        }

        // Enable logging for the service (CLI commands have logging OFF by default)
        LoggingConfig.setLogLevel(config.service().logLevel());

        LOG.info("Starting Barn service");

        // 1. Initialize directories
        dirs.initialize();
        LOG.debug("Initialized directories");

        // 2. Run crash recovery
        List<Job> recovered = crashRecovery.recoverOrphanedJobs();
        if (!recovered.isEmpty()) {
            LOG.info("Recovered {} orphaned jobs", recovered.size());
        }

        // 3. Start scheduler
        scheduler.start();
        LOG.debug("Started job scheduler");

        // 4. Start IPC server
        this.ipcServer = new IpcServer(config.service().ipcSocket(), this::handleIpcRequest);
        ipcServer.start();
        LOG.debug("Started IPC server on {}", config.service().ipcSocket());

        // 5. Start cleanup scheduler (if enabled)
        if (config.cleanup().enabled()) {
            cleanupScheduler.start();
            LOG.debug("Started cleanup scheduler");
        }

        startedAt = Instant.now();
        LOG.info("Barn service started");
    }

    /**
     * Blocks until the service is stopped.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void awaitTermination() throws InterruptedException {
        shutdownLatch.await();
    }

    /**
     * Stops the service gracefully.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        LOG.info("Stopping Barn service");

        // 1. Stop IPC server (reject new connections)
        if (ipcServer != null) {
            ipcServer.stop();
            LOG.debug("Stopped IPC server");
        }

        // 2. Stop cleanup scheduler
        if (config.cleanup().enabled()) {
            cleanupScheduler.stop();
            LOG.debug("Stopped cleanup scheduler");
        }

        // 3. Graceful scheduler stop (wait for running jobs)
        scheduler.stop();
        LOG.debug("Stopped job scheduler");

        // 4. Signal shutdown complete
        shutdownLatch.countDown();

        LOG.info("Barn service stopped");
    }

    /**
     * Reloads the service configuration.
     *
     * <p>Note: Some settings require a restart to take effect.
     */
    public void reload() {
        LOG.info("Reloading configuration");
        // Currently, most config changes require restart
        // In the future, some settings could be applied dynamically
        LOG.info("Configuration reload complete (some changes require restart)");
    }

    /**
     * Checks if the service is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the service health status.
     *
     * @return the health status
     */
    public ServiceHealth getHealth() {
        if (!running.get()) {
            return ServiceHealth.stopped();
        }

        try {
            int active = repository.findByState(JobState.RUNNING).size();
            int queued = repository.findByState(JobState.QUEUED).size();
            return ServiceHealth.running(active, queued, startedAt);
        } catch (IOException e) {
            LOG.error("Failed to get health status: {}", e.getMessage());
            return ServiceHealth.running(0, 0, startedAt);
        }
    }

    /**
     * Gets the job repository.
     *
     * @return the repository
     */
    public JobRepository getRepository() {
        return repository;
    }

    /**
     * Gets the job scheduler.
     *
     * @return the scheduler
     */
    public JobScheduler getScheduler() {
        return scheduler;
    }

    /**
     * Gets the service directories.
     *
     * @return the directories
     */
    public BarnDirectories getDirectories() {
        return dirs;
    }

    private Object handleIpcRequest(String type, Object payload) throws IpcException {
        LOG.debug("Handling IPC request: {}", type);

        try {
            return switch (type) {
                case "get_service_status" -> getHealth();
                case "shutdown" -> {
                    // Schedule shutdown in background
                    new Thread(this::stop, "shutdown").start();
                    yield "Shutdown initiated";
                }
                case "reload" -> {
                    reload();
                    yield "Configuration reloaded";
                }
                case "get_status" -> handleGetStatus(payload);
                case "get_job" -> handleGetJob(payload);
                case "run_job" -> handleRunJob(payload);
                case "kill_job" -> handleKillJob(payload);
                case "clean_jobs" -> handleCleanJobs(payload);
                default -> throw new IpcException("INVALID_REQUEST", "Unknown request type: " + type);
            };
        } catch (IOException e) {
            throw new IpcException("INTERNAL_ERROR", "Failed to process request: " + e.getMessage());
        }
    }

    private Object handleGetStatus(Object payload) throws IOException {
        String tag = null;
        JobState state = null;
        Integer limit = null;

        if (payload instanceof java.util.Map<?, ?> map) {
            tag = (String) map.get("tag");
            String stateStr = (String) map.get("state");
            if (stateStr != null) {
                state = JobState.valueOf(stateStr.toUpperCase(Locale.ROOT));
            }
            limit = (Integer) map.get("limit");
        }

        List<Job> jobs = repository.findAll();
        LOG.info("IPC get_status: found {} jobs", jobs.size());

        // Apply filters
        final String filterTag = tag;
        final JobState filterState = state;
        jobs = jobs.stream()
            .filter(job -> filterTag == null || filterTag.equals(job.tag()))
            .filter(job -> filterState == null || filterState == job.state())
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .toList();

        if (limit != null && limit > 0 && jobs.size() > limit) {
            jobs = jobs.subList(0, limit);
        }

        return jobs;
    }

    private Object handleGetJob(Object payload) throws IOException, IpcException {
        final String jobId;

        if (payload instanceof java.util.Map<?, ?> map) {
            jobId = (String) map.get("jobId");
        } else if (payload instanceof String) {
            jobId = (String) payload;
        } else {
            jobId = null;
        }

        if (jobId == null) {
            throw new IpcException("INVALID_REQUEST", "Job ID is required");
        }

        return repository.findById(jobId)
            .orElseThrow(() -> new IpcException("NOT_FOUND", "Job not found: " + jobId));
    }

    private Object handleRunJob(Object payload) throws IOException, IpcException {
        if (!(payload instanceof java.util.Map<?, ?> map)) {
            throw new IpcException("INVALID_REQUEST", "Invalid payload");
        }

        @SuppressWarnings("unchecked")
        List<String> command = (List<String>) map.get("command");
        final String tag = (String) map.get("tag");
        final String loadLevelStr = (String) map.get("loadLevel");
        final Integer maxRetries = (Integer) map.get("maxRetries");
        final Integer retryDelaySeconds = (Integer) map.get("retryDelaySeconds");

        if (command == null || command.isEmpty()) {
            throw new IpcException("INVALID_REQUEST", "Command is required");
        }

        JobsConfig jobsConfig = config.jobs();
        if (maxRetries != null || retryDelaySeconds != null) {
            jobsConfig = new JobsConfig(
                jobsConfig.defaultTimeoutSeconds(),
                maxRetries != null ? maxRetries : jobsConfig.maxRetries(),
                retryDelaySeconds != null ? retryDelaySeconds : jobsConfig.retryDelaySeconds(),
                jobsConfig.retryBackoffMultiplier(),
                jobsConfig.retryOnExitCodes()
            );
        }

        // Determine load level: use explicit override, or auto-classify
        LoadLevel loadLevel;
        if (loadLevelStr != null) {
            loadLevel = LoadLevel.fromString(loadLevelStr);
            LOG.debug("Using explicit load level: {}", loadLevel);
        } else {
            loadLevel = loadLevelClassifier.classify(command);
            LOG.debug("Auto-classified command as load level: {}", loadLevel);
        }

        Job job = repository.create(command, tag, jobsConfig, loadLevel);
        LOG.info("Created job via IPC: {} (level={})", job.id(), loadLevel);
        return job;
    }

    private Object handleKillJob(Object payload) throws IOException, IpcException {
        final String jobId;
        final boolean forceKill;

        if (payload instanceof java.util.Map<?, ?> map) {
            jobId = (String) map.get("jobId");
            forceKill = Boolean.TRUE.equals(map.get("force"));
        } else if (payload instanceof String) {
            jobId = (String) payload;
            forceKill = false;
        } else {
            jobId = null;
            forceKill = false;
        }

        if (jobId == null) {
            throw new IpcException("INVALID_REQUEST", "Job ID is required");
        }

        Job job = repository.findById(jobId)
            .orElseThrow(() -> new IpcException("NOT_FOUND", "Job not found: " + jobId));

        if (job.state() != JobState.RUNNING) {
            throw new IpcException("INVALID_STATE", "Job is not running: " + job.state());
        }

        // Kill the process
        if (job.pid() != null) {
            ProcessHandle.of(job.pid()).ifPresent(ph -> {
                if (forceKill) {
                    ph.destroyForcibly();
                } else {
                    ph.destroy();
                }
            });
        }

        // Update job state
        repository.updateState(jobId, JobState.CANCELED);
        LOG.info("Killed job via IPC: {} (force={})", jobId, forceKill);

        return repository.findById(jobId).orElse(job);
    }

    private Object handleCleanJobs(Object payload) throws IOException {
        final boolean includeFailed;
        final boolean dryRun;
        final String jobId;

        if (payload instanceof java.util.Map<?, ?> map) {
            includeFailed = Boolean.TRUE.equals(map.get("includeFailed"));
            dryRun = Boolean.TRUE.equals(map.get("dryRun"));
            jobId = (String) map.get("jobId");
        } else {
            includeFailed = false;
            dryRun = false;
            jobId = null;
        }

        List<Job> toClean;

        if (jobId != null) {
            toClean = repository.findById(jobId)
                .filter(j -> j.state() == JobState.SUCCEEDED
                    || includeFailed && j.state() == JobState.FAILED
                    || j.state() == JobState.CANCELED)
                .map(List::of)
                .orElse(List.of());
        } else {
            toClean = repository.findAll().stream()
                .filter(j -> j.state() == JobState.SUCCEEDED
                    || includeFailed && j.state() == JobState.FAILED
                    || j.state() == JobState.CANCELED)
                .toList();
        }

        if (!dryRun) {
            for (Job job : toClean) {
                repository.delete(job.id());
                LOG.info("Cleaned job via IPC: {}", job.id());
            }
        }

        return java.util.Map.of(
            "cleaned", toClean.size(),
            "dryRun", dryRun,
            "jobs", toClean
        );
    }
}
