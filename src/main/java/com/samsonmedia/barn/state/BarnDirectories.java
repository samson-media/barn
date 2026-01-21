package com.samsonmedia.barn.state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Manages the Barn directory structure.
 *
 * <p>The directory layout is:
 * <pre>
 * {baseDir}/
 *   jobs/
 *     {jobId}/
 *       work/
 *         input/
 *         output/
 *       logs/
 *   locks/
 *   logs/
 * </pre>
 */
public final class BarnDirectories {

    /** Subdirectory for job data. */
    public static final String JOBS_DIR = "jobs";

    /** Subdirectory for lock files. */
    public static final String LOCKS_DIR = "locks";

    /** Subdirectory for daemon logs. */
    public static final String LOGS_DIR = "logs";

    /** Subdirectory for job working files. */
    public static final String WORK_DIR = "work";

    /** Subdirectory for input files within work. */
    public static final String INPUT_DIR = "input";

    /** Subdirectory for output files within work. */
    public static final String OUTPUT_DIR = "output";

    private final Path baseDir;

    /**
     * Creates a BarnDirectories instance with the specified base directory.
     *
     * @param baseDir the base directory for all Barn data
     */
    public BarnDirectories(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null");
    }

    /**
     * Returns the base directory.
     *
     * @return the base directory path
     */
    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Returns the jobs directory.
     *
     * @return the jobs directory path
     */
    public Path getJobsDir() {
        return baseDir.resolve(JOBS_DIR);
    }

    /**
     * Returns the locks directory.
     *
     * @return the locks directory path
     */
    public Path getLocksDir() {
        return baseDir.resolve(LOCKS_DIR);
    }

    /**
     * Returns the daemon logs directory.
     *
     * @return the logs directory path
     */
    public Path getLogsDir() {
        return baseDir.resolve(LOGS_DIR);
    }

    /**
     * Returns the directory for a specific job.
     *
     * @param jobId the job identifier
     * @return the job directory path
     */
    public Path getJobDir(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        return getJobsDir().resolve(jobId);
    }

    /**
     * Returns the working directory for a specific job.
     *
     * @param jobId the job identifier
     * @return the job work directory path
     */
    public Path getJobWorkDir(String jobId) {
        return getJobDir(jobId).resolve(WORK_DIR);
    }

    /**
     * Returns the input directory for a specific job.
     *
     * @param jobId the job identifier
     * @return the job input directory path
     */
    public Path getJobInputDir(String jobId) {
        return getJobWorkDir(jobId).resolve(INPUT_DIR);
    }

    /**
     * Returns the output directory for a specific job.
     *
     * @param jobId the job identifier
     * @return the job output directory path
     */
    public Path getJobOutputDir(String jobId) {
        return getJobWorkDir(jobId).resolve(OUTPUT_DIR);
    }

    /**
     * Returns the logs directory for a specific job.
     *
     * @param jobId the job identifier
     * @return the job logs directory path
     */
    public Path getJobLogsDir(String jobId) {
        return getJobDir(jobId).resolve(LOGS_DIR);
    }

    /**
     * Returns the lock file path for a specific job.
     *
     * @param jobId the job identifier
     * @return the job lock file path
     */
    public Path getJobLockFile(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        return getLocksDir().resolve("job-" + jobId + ".lock");
    }

    /**
     * Returns the scheduler lock file path.
     *
     * @return the scheduler lock file path
     */
    public Path getSchedulerLockFile() {
        return getLocksDir().resolve("scheduler.lock");
    }

    /**
     * Returns the daemon log file path.
     *
     * @return the daemon log file path
     */
    public Path getDaemonLogFile() {
        return getLogsDir().resolve("barn.log");
    }

    /**
     * Returns the IPC socket file path.
     *
     * @return the socket file path
     */
    public Path getSocketPath() {
        return baseDir.resolve("barn.sock");
    }

    /**
     * Initializes the top-level directory structure.
     *
     * <p>Creates the base directory and all subdirectories if they don't exist.
     *
     * @throws IOException if directory creation fails
     */
    public void initialize() throws IOException {
        Files.createDirectories(getJobsDir());
        Files.createDirectories(getLocksDir());
        Files.createDirectories(getLogsDir());
    }

    /**
     * Creates the directory structure for a specific job.
     *
     * @param jobId the job identifier
     * @throws IOException if directory creation fails
     */
    public void createJobDirs(String jobId) throws IOException {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Files.createDirectories(getJobDir(jobId));
        Files.createDirectories(getJobInputDir(jobId));
        Files.createDirectories(getJobOutputDir(jobId));
        Files.createDirectories(getJobLogsDir(jobId));
    }

    /**
     * Checks if a job directory exists.
     *
     * @param jobId the job identifier
     * @return true if the job directory exists
     */
    public boolean jobExists(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        return Files.exists(getJobDir(jobId));
    }

    /**
     * Deletes the directory for a specific job.
     *
     * @param jobId the job identifier
     * @throws IOException if deletion fails
     */
    public void deleteJobDir(String jobId) throws IOException {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Path jobDir = getJobDir(jobId);
        if (Files.exists(jobDir)) {
            deleteRecursively(jobDir);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }
}
