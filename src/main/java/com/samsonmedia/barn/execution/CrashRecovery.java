package com.samsonmedia.barn.execution;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samsonmedia.barn.config.Config;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.jobs.RetryCalculator;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;

/**
 * Recovers orphaned jobs on daemon startup.
 *
 * <p>A job is considered orphaned when:
 * <ul>
 *   <li>Its state is RUNNING</li>
 *   <li>Its heartbeat is stale (older than threshold)</li>
 *   <li>Its process is no longer alive (or PID is unknown)</li>
 * </ul>
 *
 * <p>Orphaned jobs are marked as KILLED and automatically scheduled for retry
 * if retries are configured and not exhausted.
 */
public class CrashRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(CrashRecovery.class);
    private static final String KILLED_ERROR = "Process killed - daemon restarted";

    private final JobRepository repository;
    private final HeartbeatChecker heartbeatChecker;
    private final BarnDirectories dirs;
    private final RetryCalculator retryCalculator;
    private final int maxRetries;

    /**
     * Creates a CrashRecovery instance.
     *
     * @param repository the job repository
     * @param heartbeatChecker the heartbeat checker
     * @param dirs the directory manager
     * @param config the configuration
     */
    public CrashRecovery(JobRepository repository, HeartbeatChecker heartbeatChecker, BarnDirectories dirs,
                         Config config) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.heartbeatChecker = Objects.requireNonNull(heartbeatChecker, "heartbeatChecker must not be null");
        this.dirs = Objects.requireNonNull(dirs, "dirs must not be null");
        Objects.requireNonNull(config, "config must not be null");
        this.retryCalculator = new RetryCalculator(config.jobs());
        this.maxRetries = config.jobs().maxRetries();
    }

    /**
     * Scans for and recovers orphaned jobs.
     *
     * <p>This method should be called on daemon startup, before the scheduler
     * begins accepting new jobs.
     *
     * @return the list of jobs that were recovered
     * @throws IOException if scanning or recovery fails
     */
    public List<Job> recoverOrphanedJobs() throws IOException {
        LOG.info("Scanning for orphaned jobs...");

        List<Job> orphaned = new ArrayList<>();
        List<Job> allJobs = repository.findAll();

        for (Job job : allJobs) {
            if (isOrphaned(job)) {
                try {
                    recoverJob(job);
                    orphaned.add(job);
                } catch (IOException e) {
                    LOG.error("Failed to recover orphaned job {}: {}", job.id(), e.getMessage(), e);
                }
            }
        }

        if (orphaned.isEmpty()) {
            LOG.info("No orphaned jobs found");
        } else {
            LOG.info("Recovered {} orphaned job(s)", orphaned.size());
        }

        return orphaned;
    }

    /**
     * Checks if a job is orphaned.
     *
     * <p>A job is orphaned if it is in RUNNING state but its process
     * is no longer alive or its heartbeat is stale.
     *
     * @param job the job to check
     * @return true if the job is orphaned
     */
    public boolean isOrphaned(Job job) {
        Objects.requireNonNull(job, "job must not be null");

        // Only running jobs can be orphaned
        if (job.state() != JobState.RUNNING) {
            return false;
        }

        // Check heartbeat first - if it's fresh, process might still be writing
        if (!heartbeatChecker.isStale(job)) {
            return false;
        }

        // Heartbeat is stale, verify process is actually dead
        if (job.pid() == null) {
            // No PID recorded = definitely orphaned
            LOG.debug("Job {} has no PID and stale heartbeat, marking as orphaned", job.id());
            return true;
        }

        // Check if process is still alive
        boolean alive = ProcessUtils.isAlive(job.pid());
        if (alive) {
            // Process is alive but heartbeat is stale - could be a bug, but not orphaned
            LOG.warn("Job {} has stale heartbeat but process {} is still alive", job.id(), job.pid());
            return false;
        }

        LOG.debug("Job {} has stale heartbeat and process {} is dead, marking as orphaned",
            job.id(), job.pid());
        return true;
    }

    private void recoverJob(Job job) throws IOException {
        LOG.info("Recovering orphaned job: {} (PID: {})", job.id(), job.pid());

        // Mark the job as killed
        repository.markKilled(job.id(), KILLED_ERROR);

        // Check if we should auto-retry
        if (maxRetries > 0 && job.retryCount() < maxRetries) {
            // Schedule retry with exponential backoff
            Instant retryAt = Instant.now().plus(retryCalculator.calculateDelay(job.retryCount()));
            repository.scheduleRetry(job.id(), retryAt, null, KILLED_ERROR);
            LOG.info("Job {} scheduled for auto-retry at {} (attempt {})",
                job.id(), retryAt, job.retryCount() + 1);
        } else {
            LOG.info("Job {} will not be retried (retries exhausted or disabled)", job.id());
        }
    }
}
