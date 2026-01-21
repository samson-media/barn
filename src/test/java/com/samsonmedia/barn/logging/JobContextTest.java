package com.samsonmedia.barn.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.state.JobState;

/**
 * Tests for JobContext.
 */
@SuppressWarnings("try")
class JobContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    class ForJob {

        @Test
        void forJob_shouldSetJobIdInMdc() {
            Job job = createJob("job-123", null);

            try (JobContext ctx = JobContext.forJob(job)) {
                assertThat(ctx).isNotNull();
                assertThat(MDC.get(JobContext.KEY_JOB_ID)).isEqualTo("job-123");
            }
        }

        @Test
        void forJob_withTag_shouldSetJobTagInMdc() {
            Job job = createJob("job-123", "test-tag");

            try (JobContext ctx = JobContext.forJob(job)) {
                assertThat(ctx).isNotNull();
                assertThat(MDC.get(JobContext.KEY_JOB_TAG)).isEqualTo("test-tag");
            }
        }

        @Test
        void forJob_withoutTag_shouldNotSetJobTagInMdc() {
            Job job = createJob("job-123", null);

            try (JobContext ctx = JobContext.forJob(job)) {
                assertThat(ctx).isNotNull();
                assertThat(MDC.get(JobContext.KEY_JOB_TAG)).isNull();
            }
        }

        @Test
        void forJob_whenClosed_shouldClearMdc() {
            Job job = createJob("job-123", "test-tag");

            try (JobContext ctx = JobContext.forJob(job)) {
                assertThat(ctx).isNotNull();
                assertThat(MDC.get(JobContext.KEY_JOB_ID)).isNotNull();
            }

            assertThat(MDC.get(JobContext.KEY_JOB_ID)).isNull();
            assertThat(MDC.get(JobContext.KEY_JOB_TAG)).isNull();
        }

        @Test
        void forJob_withNull_shouldThrowException() {
            assertThatThrownBy(() -> JobContext.forJob(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }
    }

    @Nested
    class ForJobId {

        @Test
        void forJobId_shouldSetJobIdInMdc() {
            try (JobContext ctx = JobContext.forJobId("job-456")) {
                assertThat(ctx).isNotNull();
                assertThat(MDC.get(JobContext.KEY_JOB_ID)).isEqualTo("job-456");
            }
        }

        @Test
        void forJobId_whenClosed_shouldClearMdc() {
            try (JobContext ctx = JobContext.forJobId("job-456")) {
                assertThat(ctx).isNotNull();
                assertThat(MDC.get(JobContext.KEY_JOB_ID)).isNotNull();
            }

            assertThat(MDC.get(JobContext.KEY_JOB_ID)).isNull();
        }

        @Test
        void forJobId_withNull_shouldThrowException() {
            assertThatThrownBy(() -> JobContext.forJobId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jobId");
        }
    }

    @Nested
    class GetCurrentJobId {

        @Test
        void getCurrentJobId_withinContext_shouldReturnJobId() {
            try (JobContext ctx = JobContext.forJobId("job-789")) {
                assertThat(ctx).isNotNull();
                assertThat(JobContext.getCurrentJobId()).isEqualTo("job-789");
            }
        }

        @Test
        void getCurrentJobId_outsideContext_shouldReturnNull() {
            assertThat(JobContext.getCurrentJobId()).isNull();
        }
    }

    @Nested
    class GetCurrentJobTag {

        @Test
        void getCurrentJobTag_withinContext_shouldReturnJobTag() {
            Job job = createJob("job-123", "my-tag");

            try (JobContext ctx = JobContext.forJob(job)) {
                assertThat(ctx).isNotNull();
                assertThat(JobContext.getCurrentJobTag()).isEqualTo("my-tag");
            }
        }

        @Test
        void getCurrentJobTag_outsideContext_shouldReturnNull() {
            assertThat(JobContext.getCurrentJobTag()).isNull();
        }
    }

    @Nested
    class NestedContexts {

        @Test
        void nestedContexts_shouldOverwritePreviousValues() {
            Job job1 = createJob("job-1", "tag-1");
            Job job2 = createJob("job-2", "tag-2");

            try (JobContext ctx1 = JobContext.forJob(job1)) {
                assertThat(ctx1).isNotNull();
                assertThat(JobContext.getCurrentJobId()).isEqualTo("job-1");

                try (JobContext ctx2 = JobContext.forJob(job2)) {
                    assertThat(ctx2).isNotNull();
                    assertThat(JobContext.getCurrentJobId()).isEqualTo("job-2");
                }

                // After inner context closes, values are cleared (not restored)
                assertThat(JobContext.getCurrentJobId()).isNull();
            }
        }
    }

    // Test helper methods

    private Job createJob(String id, String tag) {
        return new Job(
            id,
            JobState.QUEUED,
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
}
