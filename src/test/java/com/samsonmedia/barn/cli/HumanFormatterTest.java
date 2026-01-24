package com.samsonmedia.barn.cli;

import static com.samsonmedia.barn.jobs.LoadLevel.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.state.JobState;

/**
 * Tests for HumanFormatter.
 */
class HumanFormatterTest {

    private HumanFormatter formatter;

    @BeforeEach
    void setUp() {
        // Disable colors for testing
        formatter = new HumanFormatter(false);
    }

    @Nested
    class Format {

        @Test
        void format_withNull_shouldReturnEmpty() {
            assertThat(formatter.format(null)).isEmpty();
        }

        @Test
        void format_withString_shouldReturnString() {
            assertThat(formatter.format("hello")).isEqualTo("hello");
        }

        @Test
        void format_withJob_shouldFormatJobDetails() {
            Job job = createJob("job-12345678", JobState.RUNNING, "test-tag");

            String result = formatter.format(job);

            assertThat(result).contains("Job: job-12345678");
            assertThat(result).contains("State:");
            assertThat(result).contains("running");
            assertThat(result).contains("Load:");
            assertThat(result).contains("medium");
            assertThat(result).contains("Tag:");
            assertThat(result).contains("test-tag");
        }

        @Test
        void format_withCompletedJob_shouldShowExitCode() {
            Job job = createCompletedJob("job-12345678", 0);

            String result = formatter.format(job);

            assertThat(result).contains("Exit:");
            assertThat(result).contains("0");
        }

        @Test
        void format_withFailedJob_shouldShowError() {
            Job job = createFailedJob("job-12345678", 1, "Process failed");

            String result = formatter.format(job);

            assertThat(result).contains("Exit:");
            assertThat(result).contains("1");
            assertThat(result).contains("Error:");
            assertThat(result).contains("Process failed");
        }

        @Test
        void format_withMap_shouldFormatKeyValues() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key1", "value1");
            map.put("key2", "value2");

            String result = formatter.format(map);

            assertThat(result).contains("key1:");
            assertThat(result).contains("value1");
            assertThat(result).contains("key2:");
            assertThat(result).contains("value2");
        }
    }

    @Nested
    class FormatList {

        @Test
        void formatList_withNull_shouldReturnNoResults() {
            assertThat(formatter.formatList(null)).isEqualTo("No results found.");
        }

        @Test
        void formatList_withEmpty_shouldReturnNoResults() {
            assertThat(formatter.formatList(List.of())).isEqualTo("No results found.");
        }

        @Test
        void formatList_withJobs_shouldFormatTable() {
            List<Job> jobs = List.of(
                createJob("job-11111111", JobState.RUNNING, "tag1"),
                createJob("job-22222222", JobState.QUEUED, "tag2")
            );

            String result = formatter.formatList(jobs);

            assertThat(result).contains("ID");
            assertThat(result).contains("STATE");
            assertThat(result).contains("LOAD");
            assertThat(result).contains("TAG");
            assertThat(result).contains("job-11111111");
            assertThat(result).contains("job-22222222");
            assertThat(result).contains("running");
            assertThat(result).contains("queued");
            assertThat(result).contains("medium");
        }

        @Test
        void formatList_withStrings_shouldFormatEach() {
            List<String> strings = List.of("item1", "item2", "item3");

            String result = formatter.formatList(strings);

            assertThat(result).contains("item1");
            assertThat(result).contains("item2");
            assertThat(result).contains("item3");
        }
    }

    @Nested
    class FormatError {

        @Test
        void formatError_withMessageOnly_shouldFormatError() {
            String result = formatter.formatError("Something went wrong", null);

            assertThat(result).contains("Error:");
            assertThat(result).contains("Something went wrong");
        }

        @Test
        void formatError_withCause_shouldIncludeCause() {
            Exception cause = new RuntimeException("Root cause");
            String result = formatter.formatError("Something went wrong", cause);

            assertThat(result).contains("Error:");
            assertThat(result).contains("Something went wrong");
            assertThat(result).contains("Cause:");
            assertThat(result).contains("Root cause");
        }
    }

    @Nested
    class Colors {

        @Test
        void constructor_withColors_shouldUseAnsiCodes() {
            HumanFormatter colorFormatter = new HumanFormatter(true);

            String result = colorFormatter.formatError("Error message", null);

            // Should contain ANSI escape codes
            assertThat(result).contains("\u001B[");
        }

        @Test
        void constructor_withoutColors_shouldNotUseAnsiCodes() {
            String result = formatter.formatError("Error message", null);

            // Should not contain ANSI escape codes
            assertThat(result).doesNotContain("\u001B[");
        }
    }

    // Helper methods

    private Job createJob(String id, JobState state, String tag) {
        return new Job(
            id,
            state,
            List.of("echo", "test"),
            tag,
            Instant.now(),
            state == JobState.RUNNING ? Instant.now().minus(Duration.ofMinutes(1)) : null,
            null,
            null,
            null,
            state == JobState.RUNNING ? 12345L : null,
            null,
            0,
            null,
            MEDIUM
        );
    }

    private Job createCompletedJob(String id, int exitCode) {
        return new Job(
            id,
            JobState.SUCCEEDED,
            List.of("echo", "test"),
            "tag",
            Instant.now().minus(Duration.ofMinutes(5)),
            Instant.now().minus(Duration.ofMinutes(4)),
            Instant.now(),
            exitCode,
            null,
            null,
            null,
            0,
            null,
            MEDIUM
        );
    }

    private Job createFailedJob(String id, int exitCode, String error) {
        return new Job(
            id,
            JobState.FAILED,
            List.of("echo", "test"),
            "tag",
            Instant.now().minus(Duration.ofMinutes(5)),
            Instant.now().minus(Duration.ofMinutes(4)),
            Instant.now(),
            exitCode,
            error,
            null,
            null,
            0,
            null,
            MEDIUM
        );
    }
}
