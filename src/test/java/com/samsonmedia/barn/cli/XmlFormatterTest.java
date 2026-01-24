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
 * Tests for XmlFormatter.
 */
class XmlFormatterTest {

    private XmlFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new XmlFormatter();
    }

    @Nested
    class Format {

        @Test
        void format_withNull_shouldReturnNullElement() {
            String result = formatter.format(null);

            assertThat(result).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            assertThat(result).contains("<null/>");
        }

        @Test
        void format_withString_shouldReturnResultElement() {
            String result = formatter.format("hello");

            assertThat(result).contains("<result>hello</result>");
        }

        @Test
        void format_withJob_shouldProduceValidXml() {
            Job job = createJob("job-12345678", JobState.RUNNING, "test-tag");

            String result = formatter.format(job);

            assertThat(result).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            assertThat(result).contains("<job>");
            assertThat(result).contains("<id>job-12345678</id>");
            assertThat(result).contains("<state>running</state>");
            assertThat(result).contains("<tag>test-tag</tag>");
            assertThat(result).contains("</job>");
        }

        @Test
        void format_withJob_shouldIncludeCommand() {
            Job job = createJob("job-12345678", JobState.RUNNING, "test-tag");

            String result = formatter.format(job);

            assertThat(result).contains("<command>");
            assertThat(result).contains("<arg>echo</arg>");
            assertThat(result).contains("<arg>test</arg>");
            assertThat(result).contains("</command>");
        }

        @Test
        void format_withJob_shouldFormatDatesAsIso8601() {
            Job job = createJob("job-12345678", JobState.RUNNING, "test-tag");

            String result = formatter.format(job);

            assertThat(result).contains("<created_at>");
            // ISO-8601 format
            assertThat(result).matches("(?s).*<created_at>\\d{4}-\\d{2}-\\d{2}T.*</created_at>.*");
        }

        @Test
        void format_withMap_shouldProduceValidXml() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key1", "value1");
            map.put("key2", "value2");

            String result = formatter.format(map);

            assertThat(result).contains("<result>");
            assertThat(result).contains("<key1>value1</key1>");
            assertThat(result).contains("<key2>value2</key2>");
            assertThat(result).contains("</result>");
        }
    }

    @Nested
    class FormatList {

        @Test
        void formatList_withNull_shouldReturnEmptyResults() {
            String result = formatter.formatList(null);

            assertThat(result).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            assertThat(result).contains("<results/>");
        }

        @Test
        void formatList_withEmpty_shouldReturnEmptyResults() {
            String result = formatter.formatList(List.of());

            assertThat(result).contains("<results/>");
        }

        @Test
        void formatList_withJobs_shouldProduceValidXml() {
            List<Job> jobs = List.of(
                createJob("job-11111111", JobState.RUNNING, "tag1"),
                createJob("job-22222222", JobState.QUEUED, "tag2")
            );

            String result = formatter.formatList(jobs);

            assertThat(result).contains("<results>");
            assertThat(result).contains("<job>");
            assertThat(result).contains("<id>job-11111111</id>");
            assertThat(result).contains("<id>job-22222222</id>");
            assertThat(result).contains("</results>");
        }

        @Test
        void formatList_withStrings_shouldProduceValidXml() {
            List<String> strings = List.of("one", "two", "three");

            String result = formatter.formatList(strings);

            assertThat(result).contains("<results>");
            assertThat(result).contains("<item>one</item>");
            assertThat(result).contains("<item>two</item>");
            assertThat(result).contains("<item>three</item>");
            assertThat(result).contains("</results>");
        }
    }

    @Nested
    class FormatError {

        @Test
        void formatError_withMessageOnly_shouldProduceValidXml() {
            String result = formatter.formatError("Something went wrong", null);

            assertThat(result).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            assertThat(result).contains("<error>");
            assertThat(result).contains("<message>Something went wrong</message>");
            assertThat(result).contains("</error>");
        }

        @Test
        void formatError_withCause_shouldIncludeCause() {
            Exception cause = new RuntimeException("Root cause");
            String result = formatter.formatError("Something went wrong", cause);

            assertThat(result).contains("<message>Something went wrong</message>");
            assertThat(result).contains("<cause>Root cause</cause>");
            assertThat(result).contains("<type>RuntimeException</type>");
        }
    }

    @Nested
    class XmlEscaping {

        @Test
        void format_withSpecialCharacters_shouldEscapeThem() {
            String result = formatter.format("<script>alert('xss')</script>");

            assertThat(result).contains("&lt;script&gt;");
            assertThat(result).contains("&apos;xss&apos;");
            assertThat(result).doesNotContain("<script>");
        }

        @Test
        void formatError_withSpecialCharacters_shouldEscapeThem() {
            String result = formatter.formatError("Error: <test> & \"quotes\"", null);

            assertThat(result).contains("&lt;test&gt;");
            assertThat(result).contains("&amp;");
            assertThat(result).contains("&quot;quotes&quot;");
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
