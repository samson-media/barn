package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TomlParser.
 */
class TomlParserTest {

    @Nested
    class BasicParsing {

        @Test
        void parse_withEmptyContent_shouldReturnEmptyMap() {
            Map<String, Map<String, Object>> result = TomlParser.parse("");
            assertThat(result).isEmpty();
        }

        @Test
        void parse_withOnlyComments_shouldReturnEmptyMap() {
            String content = """
                # This is a comment
                # Another comment
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result).isEmpty();
        }

        @Test
        void parse_withOnlyWhitespace_shouldReturnEmptyMap() {
            String content = "   \n   \n   ";
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class SectionParsing {

        @Test
        void parse_withSingleSection_shouldCreateSection() {
            String content = "[service]";
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result).containsKey("service");
        }

        @Test
        void parse_withMultipleSections_shouldCreateAllSections() {
            String content = """
                [service]
                [jobs]
                [cleanup]
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result).containsKeys("service", "jobs", "cleanup");
        }
    }

    @Nested
    class StringValueParsing {

        @Test
        void parse_withStringValue_shouldParseCorrectly() {
            String content = """
                [service]
                log_level = "debug"
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("service").get("log_level")).isEqualTo("debug");
        }

        @Test
        void parse_withStringContainingSpaces_shouldPreserveSpaces() {
            String content = """
                [service]
                name = "my service name"
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("service").get("name")).isEqualTo("my service name");
        }

        @Test
        void parse_withEscapedQuotes_shouldUnescape() {
            String content = """
                [service]
                value = "say \\"hello\\""
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("service").get("value")).isEqualTo("say \"hello\"");
        }

        @Test
        void parse_withEscapedNewline_shouldUnescape() {
            String content = """
                [service]
                value = "line1\\nline2"
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("service").get("value")).isEqualTo("line1\nline2");
        }
    }

    @Nested
    class IntegerValueParsing {

        @Test
        void parse_withPositiveInteger_shouldParseCorrectly() {
            String content = """
                [jobs]
                max_retries = 5
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("jobs").get("max_retries")).isEqualTo(5);
        }

        @Test
        void parse_withNegativeInteger_shouldParseCorrectly() {
            String content = """
                [jobs]
                value = -10
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("jobs").get("value")).isEqualTo(-10);
        }

        @Test
        void parse_withZero_shouldParseCorrectly() {
            String content = """
                [jobs]
                value = 0
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("jobs").get("value")).isEqualTo(0);
        }
    }

    @Nested
    class FloatValueParsing {

        @Test
        void parse_withPositiveFloat_shouldParseCorrectly() {
            String content = """
                [jobs]
                multiplier = 2.5
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("jobs").get("multiplier")).isEqualTo(2.5);
        }

        @Test
        void parse_withNegativeFloat_shouldParseCorrectly() {
            String content = """
                [jobs]
                value = -1.5
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("jobs").get("value")).isEqualTo(-1.5);
        }
    }

    @Nested
    class BooleanValueParsing {

        @Test
        void parse_withTrueValue_shouldParseCorrectly() {
            String content = """
                [cleanup]
                enabled = true
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("cleanup").get("enabled")).isEqualTo(true);
        }

        @Test
        void parse_withFalseValue_shouldParseCorrectly() {
            String content = """
                [cleanup]
                enabled = false
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("cleanup").get("enabled")).isEqualTo(false);
        }

        @Test
        void parse_withUppercaseBoolean_shouldParseCorrectly() {
            String content = """
                [cleanup]
                enabled = TRUE
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("cleanup").get("enabled")).isEqualTo(true);
        }
    }

    @Nested
    class ArrayValueParsing {

        @Test
        void parse_withEmptyArray_shouldReturnEmptyList() {
            String content = """
                [jobs]
                exit_codes = []
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("jobs").get("exit_codes")).isEqualTo(List.of());
        }

        @Test
        void parse_withIntegerArray_shouldParseCorrectly() {
            String content = """
                [jobs]
                exit_codes = [1, 2, 3]
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("jobs").get("exit_codes")).isEqualTo(List.of(1, 2, 3));
        }

        @Test
        void parse_withStringArray_shouldParseCorrectly() {
            String content = """
                [jobs]
                tags = ["a", "b", "c"]
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("jobs").get("tags")).isEqualTo(List.of("a", "b", "c"));
        }
    }

    @Nested
    class CommentHandling {

        @Test
        void parse_withInlineComment_shouldIgnoreComment() {
            String content = """
                [service]
                max_jobs = 4 # This is a comment
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("service").get("max_jobs")).isEqualTo(4);
        }

        @Test
        void parse_withCommentInString_shouldPreserveHash() {
            String content = """
                [service]
                name = "job#123"
                """;
            Map<String, Map<String, Object>> result = TomlParser.parse(content);
            assertThat(result.get("service").get("name")).isEqualTo("job#123");
        }
    }

    @Nested
    class ComplexScenarios {

        @Test
        void parse_withFullConfigFile_shouldParseAllSections() {
            String content = """
                [service]
                log_level = "info"
                max_concurrent_jobs = 4
                heartbeat_interval_seconds = 5

                [jobs]
                default_timeout_seconds = 3600
                max_retries = 3
                retry_delay_seconds = 30
                retry_backoff_multiplier = 2.0
                retry_on_exit_codes = [1, 2]

                [cleanup]
                enabled = true
                max_age_hours = 72
                keep_failed_jobs = true

                [storage]
                base_dir = "/tmp/barn"
                max_disk_usage_gb = 50
                """;

            Map<String, Map<String, Object>> result = TomlParser.parse(content);

            assertThat(result).containsKeys("service", "jobs", "cleanup", "storage");
            assertThat(result.get("service").get("log_level")).isEqualTo("info");
            assertThat(result.get("jobs").get("retry_on_exit_codes")).isEqualTo(List.of(1, 2));
            assertThat(result.get("cleanup").get("enabled")).isEqualTo(true);
            assertThat(result.get("storage").get("base_dir")).isEqualTo("/tmp/barn");
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void parse_withNullContent_shouldThrowException() {
            assertThatThrownBy(() -> TomlParser.parse((String) null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void parse_withKeyValueOutsideSection_shouldThrowException() {
            String content = """
                name = "value"
                """;
            assertThatThrownBy(() -> TomlParser.parse(content))
                .isInstanceOf(TomlParser.TomlParseException.class)
                .hasMessageContaining("outside of section");
        }

        @Test
        void parse_withInvalidSyntax_shouldThrowException() {
            String content = """
                [service]
                this is not valid
                """;
            assertThatThrownBy(() -> TomlParser.parse(content))
                .isInstanceOf(TomlParser.TomlParseException.class)
                .hasMessageContaining("Invalid syntax");
        }

        @Test
        void parse_withUnterminatedString_shouldThrowException() {
            String content = """
                [service]
                value = "unterminated
                """;
            assertThatThrownBy(() -> TomlParser.parse(content))
                .isInstanceOf(TomlParser.TomlParseException.class);
        }
    }
}
