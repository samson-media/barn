package com.samsonmedia.barn.jobs;

import static com.samsonmedia.barn.jobs.LoadLevel.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.samsonmedia.barn.state.JobState;

/**
 * Tests for Job record.
 */
class JobTest {

    @Test
    void constructor_withValidValues_shouldCreateJob() {
        Job job = new Job(
            "job-12345",
            JobState.QUEUED,
            List.of("echo", "hello"),
            "test-tag",
            Instant.now(),
            null, null, null, null, null, null, 0, null, MEDIUM
        );

        assertThat(job.id()).isEqualTo("job-12345");
        assertThat(job.state()).isEqualTo(JobState.QUEUED);
        assertThat(job.command()).containsExactly("echo", "hello");
        assertThat(job.tag()).isEqualTo("test-tag");
    }

    @Test
    void constructor_withNullId_shouldThrowException() {
        assertThatThrownBy(() -> new Job(
            null, JobState.QUEUED, List.of("echo"), null,
            Instant.now(), null, null, null, null, null, null, 0, null, MEDIUM))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id");
    }

    @Test
    void constructor_withBlankId_shouldThrowException() {
        assertThatThrownBy(() -> new Job(
            "  ", JobState.QUEUED, List.of("echo"), null,
            Instant.now(), null, null, null, null, null, null, 0, null, MEDIUM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void constructor_withNullState_shouldThrowException() {
        assertThatThrownBy(() -> new Job(
            "job-12345", null, List.of("echo"), null,
            Instant.now(), null, null, null, null, null, null, 0, null, MEDIUM))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("state");
    }

    @Test
    void constructor_withEmptyCommand_shouldThrowException() {
        assertThatThrownBy(() -> new Job(
            "job-12345", JobState.QUEUED, List.of(), null,
            Instant.now(), null, null, null, null, null, null, 0, null, MEDIUM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void constructor_withNegativeRetryCount_shouldThrowException() {
        assertThatThrownBy(() -> new Job(
            "job-12345", JobState.QUEUED, List.of("echo"), null,
            Instant.now(), null, null, null, null, null, null, -1, null, MEDIUM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retryCount");
    }

    @Test
    void constructor_shouldMakeDefensiveCopyOfCommand() {
        List<String> command = new java.util.ArrayList<>();
        command.add("echo");
        Job job = Job.createQueued("job-12345", command, null);
        command.add("modified");

        assertThat(job.command()).containsExactly("echo");
    }

    @Test
    void createQueued_shouldCreateQueuedJob() {
        Job job = Job.createQueued("job-12345", List.of("echo", "test"), "my-tag");

        assertThat(job.id()).isEqualTo("job-12345");
        assertThat(job.state()).isEqualTo(JobState.QUEUED);
        assertThat(job.command()).containsExactly("echo", "test");
        assertThat(job.tag()).isEqualTo("my-tag");
        assertThat(job.createdAt()).isNotNull();
        assertThat(job.startedAt()).isNull();
        assertThat(job.finishedAt()).isNull();
        assertThat(job.exitCode()).isNull();
        assertThat(job.retryCount()).isZero();
    }

    @Test
    void withState_shouldCreateCopyWithNewState() {
        Job original = Job.createQueued("job-12345", List.of("echo"), null);

        Job updated = original.withState(JobState.RUNNING);

        assertThat(updated.state()).isEqualTo(JobState.RUNNING);
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.command()).isEqualTo(original.command());
        assertThat(original.state()).isEqualTo(JobState.QUEUED); // Original unchanged
    }

    @Test
    void withStarted_shouldSetRunningStateAndPid() {
        Job original = Job.createQueued("job-12345", List.of("echo"), null);

        Job started = original.withStarted(12345L);

        assertThat(started.state()).isEqualTo(JobState.RUNNING);
        assertThat(started.pid()).isEqualTo(12345L);
        assertThat(started.startedAt()).isNotNull();
        assertThat(started.heartbeat()).isNotNull();
    }

    @Test
    void withCompleted_withZeroExitCode_shouldSetSucceeded() {
        Job running = Job.createQueued("job-12345", List.of("echo"), null)
            .withStarted(12345L);

        Job completed = running.withCompleted(0, null);

        assertThat(completed.state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(completed.exitCode()).isZero();
        assertThat(completed.finishedAt()).isNotNull();
    }

    @Test
    void withCompleted_withNonZeroExitCode_shouldSetFailed() {
        Job running = Job.createQueued("job-12345", List.of("echo"), null)
            .withStarted(12345L);

        Job completed = running.withCompleted(1, "Process failed");

        assertThat(completed.state()).isEqualTo(JobState.FAILED);
        assertThat(completed.exitCode()).isEqualTo(1);
        assertThat(completed.error()).isEqualTo("Process failed");
    }

    @Test
    void withHeartbeat_shouldUpdateHeartbeat() {
        Job running = Job.createQueued("job-12345", List.of("echo"), null)
            .withStarted(12345L);
        Instant newHeartbeat = Instant.now().plusSeconds(60);

        Job updated = running.withHeartbeat(newHeartbeat);

        assertThat(updated.heartbeat()).isEqualTo(newHeartbeat);
    }

    @Test
    void withRetry_shouldIncrementRetryCountAndResetState() {
        Job failed = Job.createQueued("job-12345", List.of("echo"), null)
            .withStarted(12345L)
            .withCompleted(1, "Failed");
        Instant retryAt = Instant.now().plusSeconds(30);

        Job retrying = failed.withRetry(retryAt);

        assertThat(retrying.state()).isEqualTo(JobState.QUEUED);
        assertThat(retrying.retryCount()).isEqualTo(1);
        assertThat(retrying.retryAt()).isEqualTo(retryAt);
        assertThat(retrying.startedAt()).isNull();
        assertThat(retrying.finishedAt()).isNull();
    }

    @Test
    void withCanceled_shouldSetCanceledState() {
        Job running = Job.createQueued("job-12345", List.of("echo"), null)
            .withStarted(12345L);

        Job canceled = running.withCanceled();

        assertThat(canceled.state()).isEqualTo(JobState.CANCELED);
        assertThat(canceled.error()).contains("canceled");
        assertThat(canceled.finishedAt()).isNotNull();
    }

    @Test
    void isTerminal_forTerminalStates_shouldReturnTrue() {
        Job succeeded = Job.createQueued("job-1", List.of("echo"), null)
            .withStarted(1L).withCompleted(0, null);
        Job failed = Job.createQueued("job-2", List.of("echo"), null)
            .withStarted(1L).withCompleted(1, null);
        Job canceled = Job.createQueued("job-3", List.of("echo"), null)
            .withCanceled();

        assertThat(succeeded.isTerminal()).isTrue();
        assertThat(failed.isTerminal()).isTrue();
        assertThat(canceled.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_forActiveStates_shouldReturnFalse() {
        Job queued = Job.createQueued("job-1", List.of("echo"), null);
        Job running = queued.withStarted(1L);

        assertThat(queued.isTerminal()).isFalse();
        assertThat(running.isTerminal()).isFalse();
    }

    @Test
    void isRunning_shouldReturnTrueOnlyForRunning() {
        Job queued = Job.createQueued("job-1", List.of("echo"), null);
        Job running = queued.withStarted(1L);
        Job completed = running.withCompleted(0, null);

        assertThat(queued.isRunning()).isFalse();
        assertThat(running.isRunning()).isTrue();
        assertThat(completed.isRunning()).isFalse();
    }

    @Test
    void isQueued_shouldReturnTrueOnlyForQueued() {
        Job queued = Job.createQueued("job-1", List.of("echo"), null);
        Job running = queued.withStarted(1L);

        assertThat(queued.isQueued()).isTrue();
        assertThat(running.isQueued()).isFalse();
    }

    @Test
    void createQueued_shouldDefaultToMediumLoadLevel() {
        Job job = Job.createQueued("job-12345", List.of("echo", "test"), "my-tag");

        assertThat(job.loadLevel()).isEqualTo(LoadLevel.MEDIUM);
    }

    @Test
    void createQueued_withLoadLevel_shouldUseSpecifiedLevel() {
        Job job = Job.createQueued("job-12345", List.of("ffmpeg", "-i", "input.mp4"),
            "transcode", LoadLevel.HIGH);

        assertThat(job.loadLevel()).isEqualTo(LoadLevel.HIGH);
    }

    @Test
    void withLoadLevel_shouldCreateCopyWithNewLoadLevel() {
        Job original = Job.createQueued("job-12345", List.of("echo"), null);

        Job updated = original.withLoadLevel(LoadLevel.LOW);

        assertThat(updated.loadLevel()).isEqualTo(LoadLevel.LOW);
        assertThat(original.loadLevel()).isEqualTo(LoadLevel.MEDIUM); // Original unchanged
    }
}
