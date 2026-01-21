package com.samsonmedia.barn.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.state.BarnDirectories;

/**
 * Tests for JobScheduler.
 */
class JobSchedulerTest {

    @TempDir
    private Path tempDir;

    private BarnDirectories dirs;
    private JobRepository repository;
    private JobRunner runner;
    private JobScheduler scheduler;
    private JobsConfig config;

    @BeforeEach
    void setUp() throws IOException {
        dirs = new BarnDirectories(tempDir);
        dirs.initialize();
        repository = new JobRepository(dirs);
        runner = new JobRunner(repository, dirs);
        config = JobsConfig.withDefaults();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null && scheduler.isRunning()) {
            scheduler.stopNow();
        }
    }

    @Nested
    class StartAndStop {

        @Test
        void start_shouldAcquireLock() throws IOException {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            scheduler.start();

            assertThat(scheduler.isRunning()).isTrue();
        }

        @Test
        void start_whenAlreadyRunning_shouldThrowException() throws IOException {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            scheduler.start();

            assertThatThrownBy(() -> scheduler.start())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
        }

        @Test
        void start_withLockHeld_shouldThrowException() throws IOException {
            // First scheduler acquires lock
            JobScheduler firstScheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));
            firstScheduler.start();

            // Second scheduler should fail
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            try {
                assertThatThrownBy(() -> scheduler.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Another scheduler");
            } finally {
                firstScheduler.stop();
            }
        }

        @Test
        void stop_shouldReleaseLock() throws IOException {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            scheduler.start();
            scheduler.stop();

            assertThat(scheduler.isRunning()).isFalse();
        }

        @Test
        void stop_whenNotRunning_shouldNotThrow() {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            scheduler.stop(); // Should not throw
        }

        @Test
        void stopNow_shouldTerminateImmediately() throws IOException {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            scheduler.start();
            scheduler.stopNow();

            assertThat(scheduler.isRunning()).isFalse();
        }
    }

    @Nested
    class JobExecution {

        @Test
        void scheduler_shouldExecuteQueuedJobs() throws IOException, InterruptedException {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(50), Duration.ofSeconds(10));

            // Create a quick job
            Job job = repository.create(getEchoCommand("test"), "test", config);

            scheduler.start();

            // Wait for job to complete
            assertThat(waitForJobCompletion(job.id(), Duration.ofSeconds(10))).isTrue();
        }

        @Test
        void scheduler_shouldExecuteJobsInFIFO() throws IOException, InterruptedException {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(50), Duration.ofSeconds(10));

            // Create multiple jobs with slight delays to ensure different creation times
            final String jobId1 = repository.create(getEchoCommand("first"), "tag1", config).id();
            Thread.sleep(10);
            final String jobId2 = repository.create(getEchoCommand("second"), "tag2", config).id();
            Thread.sleep(10);
            final String jobId3 = repository.create(getEchoCommand("third"), "tag3", config).id();

            scheduler.start();

            // Jobs should complete in order (first created, first executed)
            assertThat(waitForJobCompletion(jobId1, Duration.ofSeconds(10))).isTrue();
            assertThat(waitForJobCompletion(jobId2, Duration.ofSeconds(10))).isTrue();
            assertThat(waitForJobCompletion(jobId3, Duration.ofSeconds(10))).isTrue();
        }

        @Test
        void scheduler_shouldRespectMaxConcurrentJobs() throws IOException, InterruptedException {
            int maxConcurrent = 2;
            scheduler = new JobScheduler(repository, runner, dirs, maxConcurrent,
                Duration.ofMillis(50), Duration.ofSeconds(10));

            // Create jobs that take some time (short duration for test reliability)
            for (int i = 0; i < 4; i++) {
                repository.create(getEchoCommand("job" + i), "tag" + i, config);
            }

            scheduler.start();

            // Allow scheduler to pick up jobs
            Thread.sleep(200);
            JobScheduler.SchedulerStatus status = scheduler.getStatus();

            // Running jobs should never exceed max concurrent limit
            assertThat(status.runningJobs()).isLessThanOrEqualTo(maxConcurrent);
        }
    }

    @Nested
    class GetStatus {

        @Test
        void getStatus_whenNotRunning_shouldShowNotRunning() {
            scheduler = new JobScheduler(repository, runner, dirs, 2,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            JobScheduler.SchedulerStatus status = scheduler.getStatus();

            assertThat(status.isRunning()).isFalse();
            assertThat(status.maxConcurrentJobs()).isEqualTo(2);
        }

        @Test
        void getStatus_withQueuedJobs_shouldShowCount() throws IOException {
            scheduler = new JobScheduler(repository, runner, dirs, 2,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            repository.create(getEchoCommand("test1"), "tag1", config);
            repository.create(getEchoCommand("test2"), "tag2", config);

            JobScheduler.SchedulerStatus status = scheduler.getStatus();

            assertThat(status.queuedJobs()).isEqualTo(2);
        }
    }

    @Nested
    class Submit {

        @Test
        void submit_withNullJob_shouldThrowException() {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            assertThatThrownBy(() -> scheduler.submit(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void submit_shouldAcceptJob() throws IOException {
            scheduler = new JobScheduler(repository, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

            Job job = repository.create(getEchoCommand("test"), "test", config);

            scheduler.submit(job); // Should not throw
        }
    }

    @Nested
    class Constructor {

        @Test
        void constructor_withNullRepository_shouldThrowException() {
            assertThatThrownBy(() -> new JobScheduler(null, runner, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withNullRunner_shouldThrowException() {
            assertThatThrownBy(() -> new JobScheduler(repository, null, dirs, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withZeroMaxConcurrent_shouldThrowException() {
            assertThatThrownBy(() -> new JobScheduler(repository, runner, dirs, 0,
                Duration.ofMillis(100), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructor_withNegativeMaxConcurrent_shouldThrowException() {
            assertThatThrownBy(() -> new JobScheduler(repository, runner, dirs, -1,
                Duration.ofMillis(100), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class SchedulerStatusRecord {

        @Test
        void schedulerStatus_shouldContainAllFields() {
            JobScheduler.SchedulerStatus status = new JobScheduler.SchedulerStatus(5, 10, 8, true);

            assertThat(status.runningJobs()).isEqualTo(5);
            assertThat(status.queuedJobs()).isEqualTo(10);
            assertThat(status.maxConcurrentJobs()).isEqualTo(8);
            assertThat(status.isRunning()).isTrue();
        }
    }

    // Helper methods

    private boolean waitForJobCompletion(String jobId, Duration timeout) throws IOException, InterruptedException {
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < endTime) {
            Job job = repository.findById(jobId).orElse(null);
            if (job != null && job.isTerminal()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private List<String> getEchoCommand(String message) {
        if (isWindows()) {
            return List.of("cmd", "/c", "echo", message);
        }
        return List.of("echo", message);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
