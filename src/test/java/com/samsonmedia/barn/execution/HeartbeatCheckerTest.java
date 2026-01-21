package com.samsonmedia.barn.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.state.JobState;

/**
 * Tests for HeartbeatChecker.
 */
class HeartbeatCheckerTest {

    private static final Duration DEFAULT_THRESHOLD = Duration.ofSeconds(30);

    private HeartbeatChecker checker;

    @BeforeEach
    void setUp() {
        checker = new HeartbeatChecker(DEFAULT_THRESHOLD);
    }

    @Nested
    class Constructor {

        @Test
        void constructor_withNullThreshold_shouldThrowException() {
            assertThatThrownBy(() -> new HeartbeatChecker(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withZeroThreshold_shouldThrowException() {
            assertThatThrownBy(() -> new HeartbeatChecker(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructor_withNegativeThreshold_shouldThrowException() {
            assertThatThrownBy(() -> new HeartbeatChecker(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructor_withValidThreshold_shouldSucceed() {
            HeartbeatChecker hc = new HeartbeatChecker(Duration.ofSeconds(10));
            assertThat(hc.getThreshold()).isEqualTo(Duration.ofSeconds(10));
        }
    }

    @Nested
    class IsStale {

        @Test
        void isStale_withQueuedJob_shouldReturnFalse() {
            Job job = createJob(JobState.QUEUED, null);

            assertThat(checker.isStale(job)).isFalse();
        }

        @Test
        void isStale_withSucceededJob_shouldReturnFalse() {
            Job job = createJob(JobState.SUCCEEDED, null);

            assertThat(checker.isStale(job)).isFalse();
        }

        @Test
        void isStale_withFailedJob_shouldReturnFalse() {
            Job job = createJob(JobState.FAILED, null);

            assertThat(checker.isStale(job)).isFalse();
        }

        @Test
        void isStale_withCanceledJob_shouldReturnFalse() {
            Job job = createJob(JobState.CANCELED, null);

            assertThat(checker.isStale(job)).isFalse();
        }

        @Test
        void isStale_withRunningJobNoHeartbeat_shouldReturnTrue() {
            Job job = createJob(JobState.RUNNING, null);

            assertThat(checker.isStale(job)).isTrue();
        }

        @Test
        void isStale_withRunningJobFreshHeartbeat_shouldReturnFalse() {
            Instant recent = Instant.now().minus(Duration.ofSeconds(5));
            Job job = createJob(JobState.RUNNING, recent);

            assertThat(checker.isStale(job)).isFalse();
        }

        @Test
        void isStale_withRunningJobStaleHeartbeat_shouldReturnTrue() {
            Instant stale = Instant.now().minus(Duration.ofSeconds(60));
            Job job = createJob(JobState.RUNNING, stale);

            assertThat(checker.isStale(job)).isTrue();
        }

        @Test
        void isStale_withRunningJobBoundaryHeartbeat_shouldReturnFalse() {
            // Heartbeat just inside threshold boundary should not be stale
            // Add 100ms buffer to account for test execution time
            Instant boundary = Instant.now().minus(DEFAULT_THRESHOLD).plusMillis(100);
            Job job = createJob(JobState.RUNNING, boundary);

            // Just inside the boundary is acceptable
            assertThat(checker.isStale(job)).isFalse();
        }

        @Test
        void isStale_withRunningJobJustPastThreshold_shouldReturnTrue() {
            Instant pastThreshold = Instant.now().minus(DEFAULT_THRESHOLD).minusMillis(1);
            Job job = createJob(JobState.RUNNING, pastThreshold);

            assertThat(checker.isStale(job)).isTrue();
        }

        @Test
        void isStale_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> checker.isStale(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class GetThreshold {

        @Test
        void getThreshold_shouldReturnConfiguredThreshold() {
            HeartbeatChecker hc = new HeartbeatChecker(Duration.ofMinutes(5));

            assertThat(hc.getThreshold()).isEqualTo(Duration.ofMinutes(5));
        }
    }

    private Job createJob(JobState state, Instant heartbeat) {
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
            state == JobState.RUNNING ? 12345L : null,  // pid
            heartbeat,
            0,  // retryCount
            null  // retryAt
        );
    }
}
