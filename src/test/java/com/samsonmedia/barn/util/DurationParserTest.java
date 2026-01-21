package com.samsonmedia.barn.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for DurationParser.
 */
class DurationParserTest {

    @Nested
    class ValidDurations {

        @Test
        void parse_minutes_shouldReturnCorrectDuration() {
            Duration result = DurationParser.parse("30m");

            assertThat(result).isEqualTo(Duration.ofMinutes(30));
        }

        @Test
        void parse_hours_shouldReturnCorrectDuration() {
            Duration result = DurationParser.parse("24h");

            assertThat(result).isEqualTo(Duration.ofHours(24));
        }

        @Test
        void parse_days_shouldReturnCorrectDuration() {
            Duration result = DurationParser.parse("7d");

            assertThat(result).isEqualTo(Duration.ofDays(7));
        }

        @Test
        void parse_weeks_shouldReturnCorrectDuration() {
            Duration result = DurationParser.parse("2w");

            assertThat(result).isEqualTo(Duration.ofDays(14));
        }

        @Test
        void parse_singleDigit_shouldWork() {
            Duration result = DurationParser.parse("1h");

            assertThat(result).isEqualTo(Duration.ofHours(1));
        }

        @Test
        void parse_multiDigit_shouldWork() {
            Duration result = DurationParser.parse("168h");

            assertThat(result).isEqualTo(Duration.ofHours(168));
        }

        @Test
        void parse_withWhitespace_shouldTrim() {
            Duration result = DurationParser.parse("  24h  ");

            assertThat(result).isEqualTo(Duration.ofHours(24));
        }

        @Test
        void parse_uppercaseUnit_shouldWork() {
            Duration result = DurationParser.parse("24H");

            assertThat(result).isEqualTo(Duration.ofHours(24));
        }
    }

    @Nested
    class InvalidDurations {

        @Test
        void parse_null_shouldThrow() {
            assertThatThrownBy(() -> DurationParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parse_empty_shouldThrow() {
            assertThatThrownBy(() -> DurationParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parse_blank_shouldThrow() {
            assertThatThrownBy(() -> DurationParser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parse_invalidUnit_shouldThrow() {
            assertThatThrownBy(() -> DurationParser.parse("24x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");
        }

        @Test
        void parse_noNumber_shouldThrow() {
            assertThatThrownBy(() -> DurationParser.parse("h"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parse_noUnit_shouldThrow() {
            assertThatThrownBy(() -> DurationParser.parse("24"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parse_negativeNumber_shouldThrow() {
            assertThatThrownBy(() -> DurationParser.parse("-24h"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parse_zeroValue_shouldThrow() {
            assertThatThrownBy(() -> DurationParser.parse("0h"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
