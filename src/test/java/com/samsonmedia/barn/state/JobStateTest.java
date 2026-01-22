package com.samsonmedia.barn.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for JobState enum.
 */
class JobStateTest {

    @Test
    void fromString_withValidLowercase_shouldReturnState() {
        assertThat(JobState.fromString("queued")).isEqualTo(JobState.QUEUED);
        assertThat(JobState.fromString("running")).isEqualTo(JobState.RUNNING);
        assertThat(JobState.fromString("succeeded")).isEqualTo(JobState.SUCCEEDED);
        assertThat(JobState.fromString("failed")).isEqualTo(JobState.FAILED);
        assertThat(JobState.fromString("canceled")).isEqualTo(JobState.CANCELED);
        assertThat(JobState.fromString("killed")).isEqualTo(JobState.KILLED);
    }

    @Test
    void fromString_withValidUppercase_shouldReturnState() {
        assertThat(JobState.fromString("QUEUED")).isEqualTo(JobState.QUEUED);
        assertThat(JobState.fromString("RUNNING")).isEqualTo(JobState.RUNNING);
    }

    @Test
    void fromString_withMixedCase_shouldReturnState() {
        assertThat(JobState.fromString("Running")).isEqualTo(JobState.RUNNING);
        assertThat(JobState.fromString("Failed")).isEqualTo(JobState.FAILED);
    }

    @Test
    void fromString_withWhitespace_shouldTrimAndParse() {
        assertThat(JobState.fromString("  queued  ")).isEqualTo(JobState.QUEUED);
    }

    @Test
    void fromString_withNull_shouldThrowException() {
        assertThatThrownBy(() -> JobState.fromString(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or blank");
    }

    @Test
    void fromString_withBlank_shouldThrowException() {
        assertThatThrownBy(() -> JobState.fromString("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromString_withInvalidValue_shouldThrowException() {
        assertThatThrownBy(() -> JobState.fromString("invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid job state")
            .hasMessageContaining("queued, running, succeeded, failed, canceled, killed");
    }

    @Test
    void toLowercase_shouldReturnLowercaseName() {
        assertThat(JobState.QUEUED.toLowercase()).isEqualTo("queued");
        assertThat(JobState.RUNNING.toLowercase()).isEqualTo("running");
        assertThat(JobState.SUCCEEDED.toLowercase()).isEqualTo("succeeded");
        assertThat(JobState.FAILED.toLowercase()).isEqualTo("failed");
        assertThat(JobState.CANCELED.toLowercase()).isEqualTo("canceled");
        assertThat(JobState.KILLED.toLowercase()).isEqualTo("killed");
    }

    @Test
    void isTerminal_forTerminalStates_shouldReturnTrue() {
        assertThat(JobState.SUCCEEDED.isTerminal()).isTrue();
        assertThat(JobState.FAILED.isTerminal()).isTrue();
        assertThat(JobState.CANCELED.isTerminal()).isTrue();
        assertThat(JobState.KILLED.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_forActiveStates_shouldReturnFalse() {
        assertThat(JobState.QUEUED.isTerminal()).isFalse();
        assertThat(JobState.RUNNING.isTerminal()).isFalse();
    }

    @Test
    void isActive_forActiveStates_shouldReturnTrue() {
        assertThat(JobState.QUEUED.isActive()).isTrue();
        assertThat(JobState.RUNNING.isActive()).isTrue();
    }

    @Test
    void isActive_forTerminalStates_shouldReturnFalse() {
        assertThat(JobState.SUCCEEDED.isActive()).isFalse();
        assertThat(JobState.FAILED.isActive()).isFalse();
        assertThat(JobState.CANCELED.isActive()).isFalse();
        assertThat(JobState.KILLED.isActive()).isFalse();
    }
}
