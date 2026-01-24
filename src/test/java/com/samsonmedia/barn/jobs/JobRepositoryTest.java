package com.samsonmedia.barn.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;

/**
 * Tests for JobRepository.
 */
class JobRepositoryTest {

    @TempDir
    private Path tempDir;

    private BarnDirectories dirs;
    private JobRepository repository;
    private JobsConfig config;

    @BeforeEach
    void setUp() throws IOException {
        dirs = new BarnDirectories(tempDir);
        dirs.initialize();
        repository = new JobRepository(dirs);
        config = JobsConfig.withDefaults();
    }

    @Nested
    class Create {

        @Test
        void create_shouldCreateJobDirectory() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test-tag", config);

            assertThat(Files.isDirectory(dirs.getJobDir(job.id()))).isTrue();
        }

        @Test
        void create_shouldWriteManifest() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test-tag", config);

            Path manifestPath = dirs.getJobDir(job.id()).resolve("manifest.json");
            assertThat(Files.exists(manifestPath)).isTrue();
            String content = Files.readString(manifestPath);
            assertThat(content).contains("echo");
            assertThat(content).contains("hello");
        }

        @Test
        void create_shouldWriteInitialState() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test-tag", config);

            Path statePath = dirs.getJobDir(job.id()).resolve("state");
            assertThat(Files.readString(statePath).trim()).isEqualTo("queued");
        }

        @Test
        void create_shouldReturnQueuedJob() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test-tag", config);

            assertThat(job.state()).isEqualTo(JobState.QUEUED);
            assertThat(job.command()).containsExactly("echo", "hello");
            assertThat(job.tag()).isEqualTo("test-tag");
        }

        @Test
        void create_withNullTag_shouldSucceed() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);

            assertThat(job.tag()).isNull();
        }

        @Test
        void create_shouldDefaultToMediumLoadLevel() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);

            assertThat(job.loadLevel()).isEqualTo(LoadLevel.MEDIUM);
        }

        @Test
        void create_withLoadLevel_shouldUseSpecifiedLevel() throws IOException {
            Job job = repository.create(List.of("ffmpeg", "-version"), null, config, LoadLevel.HIGH);

            assertThat(job.loadLevel()).isEqualTo(LoadLevel.HIGH);
        }

        @Test
        void create_withLoadLevel_shouldPersistInManifest() throws IOException {
            Job job = repository.create(List.of("curl", "-h"), null, config, LoadLevel.LOW);

            Path manifestPath = dirs.getJobDir(job.id()).resolve("manifest.json");
            String content = Files.readString(manifestPath);
            assertThat(content).contains("\"loadLevel\" : \"LOW\"");
        }

        @Test
        void create_withLoadLevel_shouldBeReadableByFindById() throws IOException {
            Job created = repository.create(List.of("wget"), null, config, LoadLevel.LOW);

            Optional<Job> found = repository.findById(created.id());

            assertThat(found).isPresent();
            assertThat(found.get().loadLevel()).isEqualTo(LoadLevel.LOW);
        }
    }

    @Nested
    class FindById {

        @Test
        void findById_withExistingJob_shouldReturnJob() throws IOException {
            Job created = repository.create(List.of("echo"), "tag", config);

            Optional<Job> found = repository.findById(created.id());

            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(created.id());
            assertThat(found.get().command()).isEqualTo(created.command());
        }

        @Test
        void findById_withNonexistentJob_shouldReturnEmpty() throws IOException {
            Optional<Job> found = repository.findById("job-nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    class FindAll {

        @Test
        void findAll_withNoJobs_shouldReturnEmpty() throws IOException {
            List<Job> jobs = repository.findAll();

            assertThat(jobs).isEmpty();
        }

        @Test
        void findAll_withMultipleJobs_shouldReturnAll() throws IOException {
            repository.create(List.of("echo", "1"), "tag1", config);
            repository.create(List.of("echo", "2"), "tag2", config);
            repository.create(List.of("echo", "3"), "tag3", config);

            List<Job> jobs = repository.findAll();

            assertThat(jobs).hasSize(3);
        }
    }

    @Nested
    class FindByState {

        @Test
        void findByState_shouldFilterByState() throws IOException {
            Job job1 = repository.create(List.of("echo", "1"), null, config);
            Job job2 = repository.create(List.of("echo", "2"), null, config);
            repository.markStarted(job1.id(), 12345L);

            List<Job> queued = repository.findByState(JobState.QUEUED);
            List<Job> running = repository.findByState(JobState.RUNNING);

            assertThat(queued).hasSize(1);
            assertThat(queued.get(0).id()).isEqualTo(job2.id());
            assertThat(running).hasSize(1);
            assertThat(running.get(0).id()).isEqualTo(job1.id());
        }
    }

    @Nested
    class MarkStarted {

        @Test
        void markStarted_shouldUpdateState() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);

            repository.markStarted(job.id(), 12345L);

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.RUNNING);
            assertThat(updated.pid()).isEqualTo(12345L);
            assertThat(updated.startedAt()).isNotNull();
            assertThat(updated.heartbeat()).isNotNull();
        }
    }

    @Nested
    class UpdateHeartbeat {

        @Test
        void updateHeartbeat_shouldWriteTimestamp() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);
            repository.markStarted(job.id(), 12345L);
            Instant newHeartbeat = Instant.now().plusSeconds(60);

            repository.updateHeartbeat(job.id(), newHeartbeat);

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.heartbeat()).isEqualTo(newHeartbeat);
        }
    }

    @Nested
    class MarkCompleted {

        @Test
        void markCompleted_withZeroExitCode_shouldSetSucceeded() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);
            repository.markStarted(job.id(), 12345L);

            repository.markCompleted(job.id(), 0, null);

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.SUCCEEDED);
            assertThat(updated.exitCode()).isZero();
            assertThat(updated.finishedAt()).isNotNull();
        }

        @Test
        void markCompleted_withNonZeroExitCode_shouldSetFailed() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);
            repository.markStarted(job.id(), 12345L);

            repository.markCompleted(job.id(), 1, "Process failed");

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.FAILED);
            assertThat(updated.exitCode()).isEqualTo(1);
            assertThat(updated.error()).isEqualTo("Process failed");
        }
    }

    @Nested
    class MarkFailed {

        @Test
        void markFailed_withSymbolicCode_shouldSetFailed() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);
            repository.markStarted(job.id(), 12345L);

            repository.markFailed(job.id(), "orphaned_process", "Process was orphaned");

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.FAILED);
            assertThat(updated.error()).isEqualTo("Process was orphaned");
        }
    }

    @Nested
    class MarkKilled {

        @Test
        void markKilled_shouldSetKilledState() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);
            repository.markStarted(job.id(), 12345L);

            repository.markKilled(job.id(), "Process killed - daemon restarted");

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.KILLED);
            assertThat(updated.error()).isEqualTo("Process killed - daemon restarted");
            assertThat(updated.finishedAt()).isNotNull();
        }

        @Test
        void markKilled_withNullError_shouldSucceed() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);
            repository.markStarted(job.id(), 12345L);

            repository.markKilled(job.id(), null);

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.KILLED);
            assertThat(updated.error()).isNull();
        }
    }

    @Nested
    class ScheduleRetry {

        @Test
        void scheduleRetry_shouldIncrementCountAndResetState() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);
            repository.markStarted(job.id(), 12345L);
            repository.markCompleted(job.id(), 1, "Failed");
            Instant retryAt = Instant.now().plusSeconds(30);

            repository.scheduleRetry(job.id(), retryAt);

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.QUEUED);
            assertThat(updated.retryCount()).isEqualTo(1);
            assertThat(updated.retryAt()).isEqualTo(retryAt);
        }

        @Test
        void scheduleRetry_fromKilledState_shouldTransitionToQueued() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);
            repository.markStarted(job.id(), 12345L);
            repository.markKilled(job.id(), "Daemon restarted");
            Instant retryAt = Instant.now().plusSeconds(30);

            repository.scheduleRetry(job.id(), retryAt);

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.QUEUED);
            assertThat(updated.retryCount()).isEqualTo(1);
            assertThat(updated.retryAt()).isEqualTo(retryAt);
        }
    }

    @Nested
    class MarkCanceled {

        @Test
        void markCanceled_shouldSetCanceledState() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);

            repository.markCanceled(job.id());

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.CANCELED);
            assertThat(updated.finishedAt()).isNotNull();
        }
    }

    @Nested
    class Delete {

        @Test
        void delete_shouldRemoveJobDirectory() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);

            repository.delete(job.id());

            assertThat(Files.exists(dirs.getJobDir(job.id()))).isFalse();
            assertThat(repository.findById(job.id())).isEmpty();
        }
    }

    @Nested
    class LoadManifest {

        @Test
        void loadManifest_shouldReturnManifest() throws IOException {
            Job job = repository.create(List.of("echo", "test"), "my-tag", config);

            JobManifest manifest = repository.loadManifest(job.id());

            assertThat(manifest.id()).isEqualTo(job.id());
            assertThat(manifest.command()).containsExactly("echo", "test");
            assertThat(manifest.tag()).isEqualTo("my-tag");
            assertThat(manifest.maxRetries()).isEqualTo(config.maxRetries());
        }
    }

    @Nested
    class StateTransitionValidation {

        @Test
        void updateState_withInvalidTransition_shouldThrowException() throws IOException {
            Job job = repository.create(List.of("echo"), null, config);

            assertThatThrownBy(() -> repository.updateState(job.id(), JobState.SUCCEEDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid state transition");
        }
    }
}
