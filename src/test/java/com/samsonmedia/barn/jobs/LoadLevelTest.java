package com.samsonmedia.barn.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for LoadLevel enum.
 */
class LoadLevelTest {

    @Test
    void values_shouldContainAllLoadLevels() {
        assertThat(LoadLevel.values()).containsExactly(
            LoadLevel.HIGH,
            LoadLevel.MEDIUM,
            LoadLevel.LOW
        );
    }

    @Test
    void fromString_withValidLowercase_shouldReturnLoadLevel() {
        assertThat(LoadLevel.fromString("high")).isEqualTo(LoadLevel.HIGH);
        assertThat(LoadLevel.fromString("medium")).isEqualTo(LoadLevel.MEDIUM);
        assertThat(LoadLevel.fromString("low")).isEqualTo(LoadLevel.LOW);
    }

    @Test
    void fromString_withValidUppercase_shouldReturnLoadLevel() {
        assertThat(LoadLevel.fromString("HIGH")).isEqualTo(LoadLevel.HIGH);
        assertThat(LoadLevel.fromString("MEDIUM")).isEqualTo(LoadLevel.MEDIUM);
        assertThat(LoadLevel.fromString("LOW")).isEqualTo(LoadLevel.LOW);
    }

    @Test
    void fromString_withMixedCase_shouldReturnLoadLevel() {
        assertThat(LoadLevel.fromString("High")).isEqualTo(LoadLevel.HIGH);
        assertThat(LoadLevel.fromString("Medium")).isEqualTo(LoadLevel.MEDIUM);
        assertThat(LoadLevel.fromString("Low")).isEqualTo(LoadLevel.LOW);
    }

    @Test
    void fromString_withWhitespace_shouldTrimAndParse() {
        assertThat(LoadLevel.fromString("  high  ")).isEqualTo(LoadLevel.HIGH);
        assertThat(LoadLevel.fromString(" medium ")).isEqualTo(LoadLevel.MEDIUM);
    }

    @Test
    void fromString_withNull_shouldThrowException() {
        assertThatThrownBy(() -> LoadLevel.fromString(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or blank");
    }

    @Test
    void fromString_withBlank_shouldThrowException() {
        assertThatThrownBy(() -> LoadLevel.fromString("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or blank");
    }

    @Test
    void fromString_withInvalidValue_shouldThrowException() {
        assertThatThrownBy(() -> LoadLevel.fromString("invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid load level")
            .hasMessageContaining("invalid")
            .hasMessageContaining("high, medium, low");
    }

    @Test
    void toLowercase_shouldReturnLowercaseString() {
        assertThat(LoadLevel.HIGH.toLowercase()).isEqualTo("high");
        assertThat(LoadLevel.MEDIUM.toLowercase()).isEqualTo("medium");
        assertThat(LoadLevel.LOW.toLowercase()).isEqualTo("low");
    }

    @Test
    void getDefaultMaxJobs_shouldReturnCorrectDefaults() {
        assertThat(LoadLevel.HIGH.getDefaultMaxJobs()).isEqualTo(2);
        assertThat(LoadLevel.MEDIUM.getDefaultMaxJobs()).isEqualTo(8);
        assertThat(LoadLevel.LOW.getDefaultMaxJobs()).isEqualTo(32);
    }

    @Test
    void getDefault_shouldReturnMedium() {
        assertThat(LoadLevel.getDefault()).isEqualTo(LoadLevel.MEDIUM);
    }
}
