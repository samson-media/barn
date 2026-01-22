package com.samsonmedia.barn.jobs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.state.AtomicFiles;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;
import com.samsonmedia.barn.state.StateFiles;

/**
 * Repository for persisting jobs to the filesystem.
 *
 * <p>Jobs are stored in directories under the jobs directory, with state
 * files and a manifest.json containing the immutable job definition.
 */
public class JobRepository {

    private static final BarnLogger LOG = BarnLogger.getLogger(JobRepository.class);
    private static final String MANIFEST_FILE = "manifest.json";

    private final BarnDirectories dirs;
    private final ObjectMapper objectMapper;

    /**
     * Creates a JobRepository with the specified directories.
     *
     * @param dirs the Barn directories manager
     */
    public JobRepository(BarnDirectories dirs) {
        this.dirs = Objects.requireNonNull(dirs, "dirs must not be null");
        this.objectMapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Creates a new job with the given command and configuration.
     *
     * @param command the command to execute
     * @param tag optional user tag (may be null)
     * @param config the jobs configuration for defaults
     * @return the created Job
     * @throws IOException if creation fails
     */
    public Job create(List<String> command, String tag, JobsConfig config) throws IOException {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(config, "config must not be null");

        String id = JobIdGenerator.generate();
        JobManifest manifest = JobManifest.create(id, command, tag, config);

        // Create directories
        dirs.createJobDirs(id);

        // Write manifest
        Path manifestPath = dirs.getJobDir(id).resolve(MANIFEST_FILE);
        String manifestJson = objectMapper.writeValueAsString(manifest);
        AtomicFiles.writeAtomically(manifestPath, manifestJson);

        // Write initial state files
        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));
        stateFiles.writeState(JobState.QUEUED);
        stateFiles.writeCreatedAt(manifest.createdAt());
        if (tag != null) {
            stateFiles.writeTag(tag);
        }
        stateFiles.writeRetryCount(0);

        LOG.info("Created job: {}", id);
        return Job.createQueued(id, command, tag);
    }

    /**
     * Finds a job by its ID.
     *
     * @param id the job ID
     * @return the job if found, or empty
     * @throws IOException if reading fails
     */
    public Optional<Job> findById(String id) throws IOException {
        Objects.requireNonNull(id, "id must not be null");

        if (!dirs.jobExists(id)) {
            return Optional.empty();
        }

        return Optional.of(loadJob(id));
    }

    /**
     * Lists all jobs.
     *
     * @return list of all jobs
     * @throws IOException if reading fails
     */
    public List<Job> findAll() throws IOException {
        Path jobsDir = dirs.getJobsDir();
        if (!Files.exists(jobsDir)) {
            return List.of();
        }

        List<Job> jobs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(jobsDir)) {
            for (Path jobPath : stream.toList()) {
                if (Files.isDirectory(jobPath)) {
                    String id = jobPath.getFileName().toString();
                    try {
                        jobs.add(loadJob(id));
                    } catch (IOException e) {
                        LOG.warn("Failed to load job {}: {}", id, e.getMessage());
                    }
                }
            }
        }
        return jobs;
    }

    /**
     * Finds all jobs in a specific state.
     *
     * @param state the state to filter by
     * @return list of jobs in the specified state
     * @throws IOException if reading fails
     */
    public List<Job> findByState(JobState state) throws IOException {
        Objects.requireNonNull(state, "state must not be null");
        return findAll().stream()
            .filter(job -> job.state() == state)
            .toList();
    }

    /**
     * Updates the state of a job.
     *
     * @param id the job ID
     * @param newState the new state
     * @throws IOException if update fails
     * @throws IllegalStateException if the transition is invalid
     */
    public void updateState(String id, JobState newState) throws IOException {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(newState, "newState must not be null");

        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));
        Optional<JobState> currentState = stateFiles.readState();

        if (currentState.isPresent()) {
            JobStateMachine.validateTransition(currentState.get(), newState);
        }

        stateFiles.writeState(newState);
        LOG.debug("Updated job {} state to {}", id, newState);
    }

    /**
     * Marks a job as started.
     *
     * @param id the job ID
     * @param pid the OS process ID
     * @throws IOException if update fails
     */
    public void markStarted(String id, long pid) throws IOException {
        Objects.requireNonNull(id, "id must not be null");

        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));
        Instant now = Instant.now();

        updateState(id, JobState.RUNNING);
        stateFiles.writeStartedAt(now);
        stateFiles.writePid(pid);
        stateFiles.writeHeartbeat(now);

        LOG.info("Job {} started with PID {}", id, pid);
    }

    /**
     * Updates the heartbeat for a running job.
     *
     * @param id the job ID
     * @param timestamp the heartbeat timestamp
     * @throws IOException if update fails
     */
    public void updateHeartbeat(String id, Instant timestamp) throws IOException {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));
        stateFiles.writeHeartbeat(timestamp);
    }

    /**
     * Marks a job as completed.
     *
     * @param id the job ID
     * @param exitCode the process exit code
     * @param error optional error message (may be null)
     * @throws IOException if update fails
     */
    public void markCompleted(String id, int exitCode, String error) throws IOException {
        Objects.requireNonNull(id, "id must not be null");

        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));
        JobState newState = (exitCode == 0) ? JobState.SUCCEEDED : JobState.FAILED;

        updateState(id, newState);
        stateFiles.writeFinishedAt(Instant.now());
        stateFiles.writeExitCode(exitCode);
        if (error != null) {
            stateFiles.writeError(error);
        }

        LOG.info("Job {} completed with exit code {}", id, exitCode);
    }

    /**
     * Marks a job as failed with a symbolic exit code.
     *
     * @param id the job ID
     * @param symbolicCode the symbolic exit code (e.g., "orphaned_process")
     * @param error the error message
     * @throws IOException if update fails
     */
    public void markFailed(String id, String symbolicCode, String error) throws IOException {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(symbolicCode, "symbolicCode must not be null");

        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));

        updateState(id, JobState.FAILED);
        stateFiles.writeFinishedAt(Instant.now());
        stateFiles.writeExitCode(symbolicCode);
        if (error != null) {
            stateFiles.writeError(error);
        }

        LOG.info("Job {} failed: {}", id, symbolicCode);
    }

    /**
     * Marks a job as killed (orphaned when service was terminated).
     *
     * @param id the job ID
     * @param error the error message
     * @throws IOException if update fails
     */
    public void markKilled(String id, String error) throws IOException {
        Objects.requireNonNull(id, "id must not be null");

        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));

        updateState(id, JobState.KILLED);
        stateFiles.writeFinishedAt(Instant.now());
        if (error != null) {
            stateFiles.writeError(error);
        }

        LOG.info("Job {} killed: {}", id, error);
    }

    /**
     * Schedules a job for retry.
     *
     * @param id the job ID
     * @param retryAt when to retry
     * @throws IOException if update fails
     */
    public void scheduleRetry(String id, Instant retryAt) throws IOException {
        scheduleRetry(id, retryAt, null, null);
    }

    /**
     * Schedules a job for retry with history recording.
     *
     * @param id the job ID
     * @param retryAt when to retry
     * @param exitCode the exit code from the failed attempt (may be null)
     * @param error the error message from the failed attempt (may be null)
     * @throws IOException if update fails
     */
    public void scheduleRetry(String id, Instant retryAt, Integer exitCode, String error) throws IOException {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(retryAt, "retryAt must not be null");

        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));

        // Record retry history entry before incrementing count
        int currentCount = stateFiles.readRetryCount().orElse(0);
        String historyEntry = formatRetryHistoryEntry(currentCount, exitCode, error);
        stateFiles.appendRetryHistory(historyEntry);

        // Increment retry count
        stateFiles.writeRetryCount(currentCount + 1);
        stateFiles.writeRetryAt(retryAt);

        // Transition back to queued
        updateState(id, JobState.QUEUED);

        // Clear runtime state
        stateFiles.writeStartedAt(null);
        stateFiles.writeHeartbeat(null);
        stateFiles.writeFinishedAt(null);

        LOG.info("Job {} scheduled for retry at {} (attempt {})", id, retryAt, currentCount + 1);
    }

    private String formatRetryHistoryEntry(int attempt, Integer exitCode, String error) {
        StringBuilder entry = new StringBuilder();
        entry.append(Instant.now().toString());
        entry.append("|attempt=").append(attempt + 1);
        if (exitCode != null) {
            entry.append("|exit_code=").append(exitCode);
        }
        if (error != null) {
            // Escape newlines and pipes in error message
            String safeError = error.replace("\n", " ").replace("|", ";");
            entry.append("|error=").append(safeError);
        }
        return entry.toString();
    }

    /**
     * Marks a job as canceled.
     *
     * @param id the job ID
     * @throws IOException if update fails
     */
    public void markCanceled(String id) throws IOException {
        Objects.requireNonNull(id, "id must not be null");

        StateFiles stateFiles = new StateFiles(dirs.getJobDir(id));

        updateState(id, JobState.CANCELED);
        stateFiles.writeFinishedAt(Instant.now());
        stateFiles.writeError("Job was canceled");

        LOG.info("Job {} canceled", id);
    }

    /**
     * Deletes a job and its directory.
     *
     * @param id the job ID
     * @throws IOException if deletion fails
     */
    public void delete(String id) throws IOException {
        Objects.requireNonNull(id, "id must not be null");

        dirs.deleteJobDir(id);
        LOG.info("Deleted job: {}", id);
    }

    /**
     * Loads the manifest for a job.
     *
     * @param id the job ID
     * @return the job manifest
     * @throws IOException if loading fails
     */
    public JobManifest loadManifest(String id) throws IOException {
        Objects.requireNonNull(id, "id must not be null");

        Path manifestPath = dirs.getJobDir(id).resolve(MANIFEST_FILE);
        String json = Files.readString(manifestPath);
        return objectMapper.readValue(json, JobManifest.class);
    }

    private Job loadJob(String id) throws IOException {
        Path jobDir = dirs.getJobDir(id);
        StateFiles stateFiles = new StateFiles(jobDir);
        JobManifest manifest = loadManifest(id);

        return new Job(
            id,
            stateFiles.readState().orElse(JobState.QUEUED),
            manifest.command(),
            stateFiles.readTag().orElse(null),
            stateFiles.readCreatedAt().orElse(manifest.createdAt()),
            stateFiles.readStartedAt().orElse(null),
            stateFiles.readFinishedAt().orElse(null),
            stateFiles.readExitCode().isPresent() ? stateFiles.readExitCode().getAsInt() : null,
            stateFiles.readError().orElse(null),
            stateFiles.readPid().isPresent() ? stateFiles.readPid().getAsLong() : null,
            stateFiles.readHeartbeat().orElse(null),
            stateFiles.readRetryCount().orElse(0),
            stateFiles.readRetryAt().orElse(null)
        );
    }
}
