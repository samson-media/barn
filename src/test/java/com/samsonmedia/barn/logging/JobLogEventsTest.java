package com.samsonmedia.barn.logging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.state.JobState;

/**
 * Tests for JobLogEvents.
 */
class JobLogEventsTest {

    @Nested
    class JobCreated {

        @Test
        void jobCreated_withValidJob_shouldLogWithoutError() {
            Job job = createJob("job-123", JobState.QUEUED, null);

            assertThatCode(() -> JobLogEvents.jobCreated(job))
                .doesNotThrowAnyException();
        }

        @Test
        void jobCreated_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> JobLogEvents.jobCreated(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }

        @Test
        void jobCreated_withTaggedJob_shouldLogWithoutError() {
            Job job = createJob("job-123", JobState.QUEUED, "test-tag");

            assertThatCode(() -> JobLogEvents.jobCreated(job))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class JobStarted {

        @Test
        void jobStarted_withValidJob_shouldLogWithoutError() {
            Job job = createRunningJob("job-123", 12345L);

            assertThatCode(() -> JobLogEvents.jobStarted(job))
                .doesNotThrowAnyException();
        }

        @Test
        void jobStarted_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> JobLogEvents.jobStarted(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }
    }

    @Nested
    class JobCompleted {

        @Test
        void jobCompleted_withSuccessfulJob_shouldLogWithoutError() {
            Job job = createCompletedJob("job-123", JobState.SUCCEEDED, 0, null);

            assertThatCode(() -> JobLogEvents.jobCompleted(job, Duration.ofSeconds(10)))
                .doesNotThrowAnyException();
        }

        @Test
        void jobCompleted_withFailedJob_shouldLogWithoutError() {
            Job job = createCompletedJob("job-123", JobState.FAILED, 1, "Command failed");

            assertThatCode(() -> JobLogEvents.jobCompleted(job, Duration.ofSeconds(10)))
                .doesNotThrowAnyException();
        }

        @Test
        void jobCompleted_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> JobLogEvents.jobCompleted(null, Duration.ofSeconds(10)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }

        @Test
        void jobCompleted_withNullDuration_shouldThrowException() {
            Job job = createCompletedJob("job-123", JobState.SUCCEEDED, 0, null);

            assertThatThrownBy(() -> JobLogEvents.jobCompleted(job, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("duration");
        }
    }

    @Nested
    class JobRetryScheduled {

        @Test
        void jobRetryScheduled_withValidJob_shouldLogWithoutError() {
            Job job = createJob("job-123", JobState.QUEUED, null);
            Instant retryAt = Instant.now().plusSeconds(60);

            assertThatCode(() -> JobLogEvents.jobRetryScheduled(job, retryAt))
                .doesNotThrowAnyException();
        }

        @Test
        void jobRetryScheduled_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> JobLogEvents.jobRetryScheduled(null, Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }

        @Test
        void jobRetryScheduled_withNullRetryAt_shouldThrowException() {
            Job job = createJob("job-123", JobState.QUEUED, null);

            assertThatThrownBy(() -> JobLogEvents.jobRetryScheduled(job, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("retryAt");
        }
    }

    @Nested
    class JobCancelled {

        @Test
        void jobCancelled_withValidJob_shouldLogWithoutError() {
            Job job = createJob("job-123", JobState.CANCELED, null);

            assertThatCode(() -> JobLogEvents.jobCancelled(job))
                .doesNotThrowAnyException();
        }

        @Test
        void jobCancelled_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> JobLogEvents.jobCancelled(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }
    }

    @Nested
    class JobTimedOut {

        @Test
        void jobTimedOut_withValidJob_shouldLogWithoutError() {
            Job job = createJob("job-123", JobState.FAILED, null);

            assertThatCode(() -> JobLogEvents.jobTimedOut(job))
                .doesNotThrowAnyException();
        }

        @Test
        void jobTimedOut_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> JobLogEvents.jobTimedOut(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }
    }

    @Nested
    class JobCleaned {

        @Test
        void jobCleaned_withValidJobId_shouldLogWithoutError() {
            assertThatCode(() -> JobLogEvents.jobCleaned("job-123"))
                .doesNotThrowAnyException();
        }

        @Test
        void jobCleaned_withNullJobId_shouldThrowException() {
            assertThatThrownBy(() -> JobLogEvents.jobCleaned(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jobId");
        }
    }

    // Test helper methods

    private Job createJob(String id, JobState state, String tag) {
        return new Job(
            id,
            state,
            List.of("echo", "test"),
            tag,
            Instant.now(),
            null,  // startedAt
            null,  // finishedAt
            null,  // exitCode
            null,  // error
            null,  // pid
            null,  // heartbeat
            0,     // retryCount
            null   // retryAt
        );
    }

    private Job createRunningJob(String id, Long pid) {
        return new Job(
            id,
            JobState.RUNNING,
            List.of("echo", "test"),
            null,  // tag
            Instant.now(),
            Instant.now(),  // startedAt
            null,           // finishedAt
            null,           // exitCode
            null,           // error
            pid,
            Instant.now(),  // heartbeat
            0,              // retryCount
            null            // retryAt
        );
    }

    private Job createCompletedJob(String id, JobState state, Integer exitCode, String error) {
        return new Job(
            id,
            state,
            List.of("echo", "test"),
            null,  // tag
            Instant.now(),
            Instant.now(),  // startedAt
            Instant.now(),  // finishedAt
            exitCode,
            error,
            null,           // pid
            null,           // heartbeat
            0,              // retryCount
            null            // retryAt
        );
    }
}
