package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for LogLevel enum.
 */
class LogLevelTest {

    @Test
    void fromString_withValidLowercase_shouldReturnLogLevel() {
        assertThat(LogLevel.fromString("debug")).isEqualTo(LogLevel.DEBUG);
        assertThat(LogLevel.fromString("info")).isEqualTo(LogLevel.INFO);
        assertThat(LogLevel.fromString("warn")).isEqualTo(LogLevel.WARN);
        assertThat(LogLevel.fromString("error")).isEqualTo(LogLevel.ERROR);
    }

    @Test
    void fromString_withValidUppercase_shouldReturnLogLevel() {
        assertThat(LogLevel.fromString("DEBUG")).isEqualTo(LogLevel.DEBUG);
        assertThat(LogLevel.fromString("INFO")).isEqualTo(LogLevel.INFO);
        assertThat(LogLevel.fromString("WARN")).isEqualTo(LogLevel.WARN);
        assertThat(LogLevel.fromString("ERROR")).isEqualTo(LogLevel.ERROR);
    }

    @Test
    void fromString_withMixedCase_shouldReturnLogLevel() {
        assertThat(LogLevel.fromString("Debug")).isEqualTo(LogLevel.DEBUG);
        assertThat(LogLevel.fromString("Info")).isEqualTo(LogLevel.INFO);
    }

    @Test
    void fromString_withWhitespace_shouldTrimAndParse() {
        assertThat(LogLevel.fromString("  info  ")).isEqualTo(LogLevel.INFO);
    }

    @Test
    void fromString_withNull_shouldThrowException() {
        assertThatThrownBy(() -> LogLevel.fromString(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or blank");
    }

    @Test
    void fromString_withBlank_shouldThrowException() {
        assertThatThrownBy(() -> LogLevel.fromString("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or blank");
    }

    @Test
    void fromString_withInvalidValue_shouldThrowException() {
        assertThatThrownBy(() -> LogLevel.fromString("invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid log level")
            .hasMessageContaining("invalid")
            .hasMessageContaining("debug, info, warn, error");
    }
}
