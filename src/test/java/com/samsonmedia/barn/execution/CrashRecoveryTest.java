package com.samsonmedia.barn.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.Config;
import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;
import com.samsonmedia.barn.state.StateFiles;

/**
 * Tests for CrashRecovery.
 */
class CrashRecoveryTest {

    @TempDir
    private Path tempDir;

    private BarnDirectories dirs;
    private JobRepository repository;
    private HeartbeatChecker heartbeatChecker;
    private CrashRecovery crashRecovery;
    private JobsConfig jobsConfig;
    private Config config;

    @BeforeEach
    void setUp() throws IOException {
        dirs = new BarnDirectories(tempDir);
        dirs.initialize();
        repository = new JobRepository(dirs);
        heartbeatChecker = new HeartbeatChecker(Duration.ofSeconds(30));
        config = Config.withDefaults();
        crashRecovery = new CrashRecovery(repository, heartbeatChecker, dirs, config);
        jobsConfig = JobsConfig.withDefaults();
    }

    @Nested
    class Constructor {

        @Test
        void constructor_withNullRepository_shouldThrowException() {
            assertThatThrownBy(() -> new CrashRecovery(null, heartbeatChecker, dirs, config))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withNullHeartbeatChecker_shouldThrowException() {
            assertThatThrownBy(() -> new CrashRecovery(repository, null, dirs, config))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withNullDirs_shouldThrowException() {
            assertThatThrownBy(() -> new CrashRecovery(repository, heartbeatChecker, null, config))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withNullConfig_shouldThrowException() {
            assertThatThrownBy(() -> new CrashRecovery(repository, heartbeatChecker, dirs, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class RecoverOrphanedJobs {

        @Test
        void recoverOrphanedJobs_withNoJobs_shouldReturnEmpty() throws IOException {
            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            assertThat(recovered).isEmpty();
        }

        @Test
        void recoverOrphanedJobs_withQueuedJobs_shouldNotRecover() throws IOException {
            repository.create(List.of("echo", "test"), "test", jobsConfig);

            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            assertThat(recovered).isEmpty();
        }

        @Test
        void recoverOrphanedJobs_withCompletedJobs_shouldNotRecover() throws IOException {
            Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
            repository.markStarted(job.id(), 12345);
            repository.markCompleted(job.id(), 0, null);

            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            assertThat(recovered).isEmpty();
        }

        @Test
        void recoverOrphanedJobs_withRunningJobFreshHeartbeat_shouldNotRecover() throws IOException {
            Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
            repository.markStarted(job.id(), ProcessUtils.getCurrentPid());  // Use current PID (alive)
            repository.updateHeartbeat(job.id(), Instant.now());  // Fresh heartbeat

            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            assertThat(recovered).isEmpty();
        }

        @Test
        void recoverOrphanedJobs_withRunningJobStaleHeartbeatDeadProcess_shouldRecover() throws IOException {
            Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
            // Use a PID that's unlikely to exist (very high number)
            repository.markStarted(job.id(), 999999999L);
            // Set a stale heartbeat
            setStaleHeartbeat(job.id(), Duration.ofMinutes(5));

            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            assertThat(recovered).hasSize(1);
            assertThat(recovered.get(0).id()).isEqualTo(job.id());

            // Verify job was killed and auto-retried (state is QUEUED with retry count incremented)
            Job updatedJob = repository.findById(job.id()).orElseThrow();
            assertThat(updatedJob.state()).isEqualTo(JobState.QUEUED);
            assertThat(updatedJob.retryCount()).isEqualTo(1);
        }

        @Test
        void recoverOrphanedJobs_withRunningJobNoHeartbeatDeadProcess_shouldRecover() throws IOException {
            Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
            repository.markStarted(job.id(), 999999999L);
            // Clear heartbeat to simulate crash before first heartbeat
            clearHeartbeat(job.id());

            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            assertThat(recovered).hasSize(1);
            assertThat(recovered.get(0).id()).isEqualTo(job.id());
        }

        @Test
        void recoverOrphanedJobs_withRunningJobStaleHeartbeatAliveProcess_shouldNotRecover() throws IOException {
            Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
            // Use current process PID which is definitely alive
            repository.markStarted(job.id(), ProcessUtils.getCurrentPid());
            // Set a stale heartbeat
            setStaleHeartbeat(job.id(), Duration.ofMinutes(5));

            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            // Should not recover because process is still alive
            assertThat(recovered).isEmpty();
        }

        @Test
        void recoverOrphanedJobs_withRunningJobNoPid_shouldRecover() throws IOException {
            Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
            // Manually set running state without PID (simulates partial write)
            StateFiles stateFiles = new StateFiles(dirs.getJobDir(job.id()));
            stateFiles.writeState(JobState.RUNNING);
            stateFiles.writeStartedAt(Instant.now().minus(Duration.ofMinutes(10)));

            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            assertThat(recovered).hasSize(1);
        }

        @Test
        void recoverOrphanedJobs_withMultipleOrphanedJobs_shouldRecoverAll() throws IOException {
            // Create 3 orphaned jobs
            for (int i = 0; i < 3; i++) {
                Job job = repository.create(List.of("echo", "test" + i), "test", jobsConfig);
                repository.markStarted(job.id(), 999999999L + i);
                setStaleHeartbeat(job.id(), Duration.ofMinutes(5));
            }

            List<Job> recovered = crashRecovery.recoverOrphanedJobs();

            assertThat(recovered).hasSize(3);
        }

        @Test
        void recoverOrphanedJobs_shouldSetCorrectExitCode() throws IOException {
            Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
            repository.markStarted(job.id(), 999999999L);
            setStaleHeartbeat(job.id(), Duration.ofMinutes(5));

            crashRecovery.recoverOrphanedJobs();

            Job updatedJob = repository.findById(job.id()).orElseThrow();
            // Verify the job was killed and auto-retried (state is QUEUED with retry count incremented)
            assertThat(updatedJob.state()).isEqualTo(JobState.QUEUED);
            assertThat(updatedJob.retryCount()).isEqualTo(1);
            assertThat(updatedJob.retryAt()).isNotNull();
        }

        @Test
        void recoverOrphanedJobs_withRetriesExhausted_shouldStayKilled() throws IOException {
            // Create a config with maxRetries = 0 so no auto-retry
            Config noRetryConfig = new Config(
                config.service(),
                new JobsConfig(config.jobs().defaultTimeoutSeconds(), 0, 1, 1.0, List.of()),
                config.cleanup(),
                config.storage()
            );
            CrashRecovery noRetryCrashRecovery = new CrashRecovery(
                repository, heartbeatChecker, dirs, noRetryConfig);

            Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
            repository.markStarted(job.id(), 999999999L);
            setStaleHeartbeat(job.id(), Duration.ofMinutes(5));

            noRetryCrashRecovery.recoverOrphanedJobs();

            Job updatedJob = repository.findById(job.id()).orElseThrow();
            // With retries disabled, job should stay in KILLED state
            assertThat(updatedJob.state()).isEqualTo(JobState.KILLED);
            assertThat(updatedJob.error()).contains("killed");
        }
    }

    @Nested
    class IsOrphaned {

        @Test
        void isOrphaned_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> crashRecovery.isOrphaned(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void isOrphaned_withQueuedJob_shouldReturnFalse() {
            Job job = createJob(JobState.QUEUED, null, null);

            assertThat(crashRecovery.isOrphaned(job)).isFalse();
        }

        @Test
        void isOrphaned_withRunningJobAliveProcess_shouldReturnFalse() {
            Job job = createJob(JobState.RUNNING, ProcessUtils.getCurrentPid(), Instant.now());

            assertThat(crashRecovery.isOrphaned(job)).isFalse();
        }

        @Test
        void isOrphaned_withRunningJobDeadProcessStaleHeartbeat_shouldReturnTrue() {
            Instant stale = Instant.now().minus(Duration.ofMinutes(5));
            Job job = createJob(JobState.RUNNING, 999999999L, stale);

            assertThat(crashRecovery.isOrphaned(job)).isTrue();
        }

        @Test
        void isOrphaned_withRunningJobDeadProcessFreshHeartbeat_shouldReturnFalse() {
            // Fresh heartbeat + dead process = weird state, but fresh heartbeat wins
            Job job = createJob(JobState.RUNNING, 999999999L, Instant.now());

            assertThat(crashRecovery.isOrphaned(job)).isFalse();
        }
    }

    // Helper methods

    private void setStaleHeartbeat(String jobId, Duration age) throws IOException {
        StateFiles stateFiles = new StateFiles(dirs.getJobDir(jobId));
        stateFiles.writeHeartbeat(Instant.now().minus(age));
    }

    private void clearHeartbeat(String jobId) throws IOException {
        StateFiles stateFiles = new StateFiles(dirs.getJobDir(jobId));
        stateFiles.writeHeartbeat(null);
    }

    private Job createJob(JobState state, Long pid, Instant heartbeat) {
        return new Job(
            "job-12345678",
            state,
            List.of("echo", "test"),
            null,  // tag
            Instant.now(),  // createdAt
            state == JobState.RUNNING ? Instant.now().minus(Duration.ofMinutes(1)) : null,  // startedAt
            null,  // finishedAt
            null,  // exitCode
            null,  // error
            pid,
            heartbeat,
            0,  // retryCount
            null  // retryAt
        );
    }
}
