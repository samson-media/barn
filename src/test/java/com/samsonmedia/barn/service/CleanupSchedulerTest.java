package com.samsonmedia.barn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.CleanupConfig;
import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.state.BarnDirectories;

/**
 * Tests for {@link CleanupScheduler}.
 */
class CleanupSchedulerTest {

    @TempDir
    private Path tempDir;

    private JobRepository repository;
    private BarnDirectories dirs;
    private CleanupConfig config;
    private JobsConfig jobsConfig;

    @BeforeEach
    void setUp() throws IOException {
        dirs = new BarnDirectories(tempDir);
        dirs.initialize();
        repository = new JobRepository(dirs);
        // maxAgeHours=24, cleanupIntervalMinutes=60, keepFailedJobsHours=168
        config = new CleanupConfig(true, 24, 60, true, 168);
        jobsConfig = JobsConfig.withDefaults();
    }

    @Test
    void constructor_withNullRepository_shouldThrowException() {
        assertThatThrownBy(() -> new CleanupScheduler(null, config))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("repository");
    }

    @Test
    void constructor_withNullConfig_shouldThrowException() {
        assertThatThrownBy(() -> new CleanupScheduler(repository, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("config");
    }

    @Test
    void start_whenNotRunning_shouldStartSuccessfully() {
        // Arrange
        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act
        scheduler.start();

        // Assert
        assertThat(scheduler.isRunning()).isTrue();

        // Cleanup
        scheduler.stop();
    }

    @Test
    void start_whenAlreadyRunning_shouldThrowException() {
        // Arrange
        CleanupScheduler scheduler = new CleanupScheduler(repository, config);
        scheduler.start();

        try {
            // Act & Assert
            assertThatThrownBy(() -> scheduler.start())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void stop_whenRunning_shouldStopSuccessfully() {
        // Arrange
        CleanupScheduler scheduler = new CleanupScheduler(repository, config);
        scheduler.start();

        // Act
        scheduler.stop();

        // Assert
        assertThat(scheduler.isRunning()).isFalse();
    }

    @Test
    void stop_whenNotRunning_shouldBeNoOp() {
        // Arrange
        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act - should not throw
        scheduler.stop();

        // Assert
        assertThat(scheduler.isRunning()).isFalse();
    }

    @Test
    void isRunning_whenNotStarted_shouldReturnFalse() {
        // Arrange
        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Assert
        assertThat(scheduler.isRunning()).isFalse();
    }

    @Test
    void runNow_withNoJobs_shouldNotFail() throws IOException {
        // Arrange
        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act - should not throw
        scheduler.runNow();

        // Assert
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void runNow_withRunningJob_shouldNotDeleteIt() throws IOException {
        // Arrange
        Job job = repository.create(List.of("sleep", "100"), "test", jobsConfig);
        repository.markStarted(job.id(), 12345L);
        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act
        scheduler.runNow();

        // Assert - job should still exist
        assertThat(repository.findById(job.id())).isPresent();
    }

    @Test
    void runNow_withQueuedJob_shouldNotDeleteIt() throws IOException {
        // Arrange
        Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
        // Job is QUEUED by default after creation
        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act
        scheduler.runNow();

        // Assert - job should still exist
        assertThat(repository.findById(job.id())).isPresent();
    }

    @Test
    void runNow_withOldSucceededJob_shouldDeleteIt() throws IOException {
        // Arrange
        Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
        repository.markStarted(job.id(), 12345L);
        repository.markCompleted(job.id(), 0, null);

        // Manually make the job old by modifying its state file
        makeJobOld(job.id(), 30); // 30 hours old, config maxAgeHours is 24

        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act
        scheduler.runNow();

        // Assert - job should be deleted
        assertThat(repository.findById(job.id())).isEmpty();
    }

    @Test
    void runNow_withRecentSucceededJob_shouldNotDeleteIt() throws IOException {
        // Arrange
        Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
        repository.markStarted(job.id(), 12345L);
        repository.markCompleted(job.id(), 0, null);
        // Job just finished, so it's recent

        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act
        scheduler.runNow();

        // Assert - job should still exist
        assertThat(repository.findById(job.id())).isPresent();
    }

    @Test
    void runNow_withOldFailedJob_shouldRespectKeepFailedJobsHours() throws IOException {
        // Arrange
        Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
        repository.markStarted(job.id(), 12345L);
        repository.markFailed(job.id(), "error", "test error");

        // Make job older than keepFailedJobsHours (168 hours = 7 days)
        makeJobOld(job.id(), 200); // 200 hours old

        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act
        scheduler.runNow();

        // Assert - job should be deleted
        assertThat(repository.findById(job.id())).isEmpty();
    }

    @Test
    void runNow_withRecentFailedJob_shouldNotDeleteIt() throws IOException {
        // Arrange
        Job job = repository.create(List.of("echo", "test"), "test", jobsConfig);
        repository.markStarted(job.id(), 12345L);
        repository.markFailed(job.id(), "error", "test error");

        // Make job 48 hours old - still within keepFailedJobsHours (168)
        makeJobOld(job.id(), 48);

        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act
        scheduler.runNow();

        // Assert - job should still exist (within 168 hours)
        assertThat(repository.findById(job.id())).isPresent();
    }

    @Test
    void runNow_withMultipleJobs_shouldDeleteOnlyOldCompletedJobs() throws IOException {
        // Arrange
        Job oldSucceeded = repository.create(List.of("echo", "1"), "old-succeeded", jobsConfig);
        repository.markStarted(oldSucceeded.id(), 1L);
        repository.markCompleted(oldSucceeded.id(), 0, null);
        makeJobOld(oldSucceeded.id(), 30);

        Job recentSucceeded = repository.create(List.of("echo", "2"), "recent-succeeded", jobsConfig);
        repository.markStarted(recentSucceeded.id(), 2L);
        repository.markCompleted(recentSucceeded.id(), 0, null);

        Job running = repository.create(List.of("echo", "3"), "running", jobsConfig);
        repository.markStarted(running.id(), 3L);

        CleanupScheduler scheduler = new CleanupScheduler(repository, config);

        // Act
        scheduler.runNow();

        // Assert
        assertThat(repository.findById(oldSucceeded.id())).isEmpty();
        assertThat(repository.findById(recentSucceeded.id())).isPresent();
        assertThat(repository.findById(running.id())).isPresent();
    }

    /**
     * Makes a job appear old by modifying its finishedAt timestamp file.
     */
    private void makeJobOld(String jobId, int hoursOld) throws IOException {
        Path finishedAtFile = dirs.getJobDir(jobId).resolve("finished_at");
        Instant oldTime = Instant.now().minusSeconds(hoursOld * 3600L);
        Files.writeString(finishedAtFile, oldTime.toString());
    }
}
