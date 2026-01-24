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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.state.JobState;

/**
 * Tests for JsonFormatter.
 */
class JsonFormatterTest {

    private JsonFormatter formatter;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        formatter = new JsonFormatter();
        mapper = new ObjectMapper();
    }

    @Nested
    class Format {

        @Test
        void format_withNull_shouldReturnNull() {
            assertThat(formatter.format(null)).isEqualTo("null");
        }

        @Test
        void format_withString_shouldReturnQuotedString() throws JsonProcessingException {
            String result = formatter.format("hello");
            JsonNode node = mapper.readTree(result);

            assertThat(node.asText()).isEqualTo("hello");
        }

        @Test
        void format_withNumber_shouldReturnNumber() throws JsonProcessingException {
            String result = formatter.format(42);
            JsonNode node = mapper.readTree(result);

            assertThat(node.asInt()).isEqualTo(42);
        }

        @Test
        void format_withJob_shouldProduceValidJson() throws JsonProcessingException {
            Job job = createJob("job-12345678", JobState.RUNNING, "test-tag");

            String result = formatter.format(job);
            JsonNode node = mapper.readTree(result);

            assertThat(node.get("id").asText()).isEqualTo("job-12345678");
            assertThat(node.get("state").asText()).isEqualTo("RUNNING");
            assertThat(node.get("tag").asText()).isEqualTo("test-tag");
        }

        @Test
        void format_withJob_shouldFormatDatesAsIso8601() throws JsonProcessingException {
            Job job = createJob("job-12345678", JobState.RUNNING, "test-tag");

            String result = formatter.format(job);
            JsonNode node = mapper.readTree(result);

            // ISO-8601 format ends with Z for UTC
            String createdAt = node.get("createdAt").asText();
            assertThat(createdAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }

        @Test
        void format_withMap_shouldProduceValidJson() throws JsonProcessingException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key1", "value1");
            map.put("key2", 42);

            String result = formatter.format(map);
            JsonNode node = mapper.readTree(result);

            assertThat(node.get("key1").asText()).isEqualTo("value1");
            assertThat(node.get("key2").asInt()).isEqualTo(42);
        }
    }

    @Nested
    class FormatList {

        @Test
        void formatList_withNull_shouldReturnEmptyArray() throws JsonProcessingException {
            String result = formatter.formatList(null);
            JsonNode node = mapper.readTree(result);

            assertThat(node.isArray()).isTrue();
            assertThat(node.size()).isZero();
        }

        @Test
        void formatList_withEmpty_shouldReturnEmptyArray() throws JsonProcessingException {
            String result = formatter.formatList(List.of());
            JsonNode node = mapper.readTree(result);

            assertThat(node.isArray()).isTrue();
            assertThat(node.size()).isZero();
        }

        @Test
        void formatList_withJobs_shouldProduceValidJsonArray() throws JsonProcessingException {
            List<Job> jobs = List.of(
                createJob("job-11111111", JobState.RUNNING, "tag1"),
                createJob("job-22222222", JobState.QUEUED, "tag2")
            );

            String result = formatter.formatList(jobs);
            JsonNode node = mapper.readTree(result);

            assertThat(node.isArray()).isTrue();
            assertThat(node.size()).isEqualTo(2);
            assertThat(node.get(0).get("id").asText()).isEqualTo("job-11111111");
            assertThat(node.get(1).get("id").asText()).isEqualTo("job-22222222");
        }

        @Test
        void formatList_withStrings_shouldProduceValidJsonArray() throws JsonProcessingException {
            List<String> strings = List.of("one", "two", "three");

            String result = formatter.formatList(strings);
            JsonNode node = mapper.readTree(result);

            assertThat(node.isArray()).isTrue();
            assertThat(node.size()).isEqualTo(3);
            assertThat(node.get(0).asText()).isEqualTo("one");
        }
    }

    @Nested
    class FormatError {

        @Test
        void formatError_withMessageOnly_shouldProduceValidJson() throws JsonProcessingException {
            String result = formatter.formatError("Something went wrong", null);
            JsonNode node = mapper.readTree(result);

            assertThat(node.get("error").asBoolean()).isTrue();
            assertThat(node.get("message").asText()).isEqualTo("Something went wrong");
            assertThat(node.has("cause")).isFalse();
        }

        @Test
        void formatError_withCause_shouldIncludeCauseInJson() throws JsonProcessingException {
            Exception cause = new RuntimeException("Root cause");
            String result = formatter.formatError("Something went wrong", cause);
            JsonNode node = mapper.readTree(result);

            assertThat(node.get("error").asBoolean()).isTrue();
            assertThat(node.get("message").asText()).isEqualTo("Something went wrong");
            assertThat(node.get("cause").asText()).isEqualTo("Root cause");
            assertThat(node.get("type").asText()).isEqualTo("RuntimeException");
        }

        @Test
        void formatError_shouldAlwaysBeValidJson() throws JsonProcessingException {
            String result = formatter.formatError("Error with \"quotes\"", null);

            // Should not throw when parsing
            JsonNode node = mapper.readTree(result);
            assertThat(node.get("message").asText()).isEqualTo("Error with \"quotes\"");
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
}
