package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.samsonmedia.barn.jobs.LoadLevel;

/**
 * Tests for LoadLevelConfig record.
 */
class LoadLevelConfigTest {

    @Test
    void constructor_withValidValues_shouldCreateConfig() {
        var config = new LoadLevelConfig(4, 16, 64);

        assertThat(config.maxHighJobs()).isEqualTo(4);
        assertThat(config.maxMediumJobs()).isEqualTo(16);
        assertThat(config.maxLowJobs()).isEqualTo(64);
    }

    @Test
    void constructor_withZeroHighJobs_shouldThrowException() {
        assertThatThrownBy(() -> new LoadLevelConfig(0, 8, 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxHighJobs must be at least 1");
    }

    @Test
    void constructor_withNegativeHighJobs_shouldThrowException() {
        assertThatThrownBy(() -> new LoadLevelConfig(-1, 8, 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxHighJobs must be at least 1");
    }

    @Test
    void constructor_withZeroMediumJobs_shouldThrowException() {
        assertThatThrownBy(() -> new LoadLevelConfig(2, 0, 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxMediumJobs must be at least 1");
    }

    @Test
    void constructor_withZeroLowJobs_shouldThrowException() {
        assertThatThrownBy(() -> new LoadLevelConfig(2, 8, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxLowJobs must be at least 1");
    }

    @Test
    void withDefaults_shouldReturnDefaultValues() {
        var config = LoadLevelConfig.withDefaults();

        assertThat(config.maxHighJobs()).isEqualTo(LoadLevelConfig.DEFAULT_MAX_HIGH_JOBS);
        assertThat(config.maxMediumJobs()).isEqualTo(LoadLevelConfig.DEFAULT_MAX_MEDIUM_JOBS);
        assertThat(config.maxLowJobs()).isEqualTo(LoadLevelConfig.DEFAULT_MAX_LOW_JOBS);
    }

    @Test
    void defaultConstants_shouldMatchLoadLevelDefaults() {
        assertThat(LoadLevelConfig.DEFAULT_MAX_HIGH_JOBS).isEqualTo(LoadLevel.HIGH.getDefaultMaxJobs());
        assertThat(LoadLevelConfig.DEFAULT_MAX_MEDIUM_JOBS).isEqualTo(LoadLevel.MEDIUM.getDefaultMaxJobs());
        assertThat(LoadLevelConfig.DEFAULT_MAX_LOW_JOBS).isEqualTo(LoadLevel.LOW.getDefaultMaxJobs());
    }

    @Test
    void getMaxJobsFor_shouldReturnCorrectLimit() {
        var config = new LoadLevelConfig(4, 16, 64);

        assertThat(config.getMaxJobsFor(LoadLevel.HIGH)).isEqualTo(4);
        assertThat(config.getMaxJobsFor(LoadLevel.MEDIUM)).isEqualTo(16);
        assertThat(config.getMaxJobsFor(LoadLevel.LOW)).isEqualTo(64);
    }

    @Test
    void getMaxJobsFor_withNull_shouldThrowException() {
        var config = LoadLevelConfig.withDefaults();

        assertThatThrownBy(() -> config.getMaxJobsFor(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("level must not be null");
    }

    @Test
    void getTotalMaxJobs_shouldReturnSum() {
        var config = new LoadLevelConfig(4, 16, 64);

        assertThat(config.getTotalMaxJobs()).isEqualTo(84);
    }
}
