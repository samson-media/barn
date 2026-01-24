package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for configuration record classes.
 */
class ConfigTest {

    @Nested
    class ServiceConfigTests {

        @Test
        void constructor_withValidValues_shouldCreateInstance() {
            ServiceConfig config = new ServiceConfig(
                LogLevel.DEBUG, 8, 10, Path.of("/tmp/barn.sock"), 60);

            assertThat(config.logLevel()).isEqualTo(LogLevel.DEBUG);
            assertThat(config.maxConcurrentJobs()).isEqualTo(8);
            assertThat(config.heartbeatIntervalSeconds()).isEqualTo(10);
            assertThat(config.ipcSocket()).isEqualTo(Path.of("/tmp/barn.sock"));
            assertThat(config.staleHeartbeatThresholdSeconds()).isEqualTo(60);
        }

        @Test
        void constructor_withNullLogLevel_shouldThrowException() {
            assertThatThrownBy(() -> new ServiceConfig(
                null, 4, 5, Path.of("/tmp/barn.sock"), 30))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("logLevel");
        }

        @Test
        void constructor_withNullIpcSocket_shouldThrowException() {
            assertThatThrownBy(() -> new ServiceConfig(
                LogLevel.INFO, 4, 5, null, 30))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ipcSocket");
        }

        @Test
        void constructor_withZeroMaxConcurrentJobs_shouldThrowException() {
            assertThatThrownBy(() -> new ServiceConfig(
                LogLevel.INFO, 0, 5, Path.of("/tmp/barn.sock"), 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentJobs");
        }

        @Test
        void constructor_withZeroHeartbeatInterval_shouldThrowException() {
            assertThatThrownBy(() -> new ServiceConfig(
                LogLevel.INFO, 4, 0, Path.of("/tmp/barn.sock"), 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("heartbeatIntervalSeconds");
        }

        @Test
        void withDefaults_shouldReturnConfigWithDefaultValues() {
            ServiceConfig config = ServiceConfig.withDefaults();

            assertThat(config.logLevel()).isEqualTo(LogLevel.INFO);
            assertThat(config.maxConcurrentJobs()).isEqualTo(4);
            assertThat(config.heartbeatIntervalSeconds()).isEqualTo(5);
            assertThat(config.staleHeartbeatThresholdSeconds()).isEqualTo(30);
            assertThat(config.ipcSocket()).isNotNull();
        }
    }

    @Nested
    class JobsConfigTests {

        @Test
        void constructor_withValidValues_shouldCreateInstance() {
            JobsConfig config = new JobsConfig(7200, 5, 60, 3.0, List.of(1, 2, 3));

            assertThat(config.defaultTimeoutSeconds()).isEqualTo(7200);
            assertThat(config.maxRetries()).isEqualTo(5);
            assertThat(config.retryDelaySeconds()).isEqualTo(60);
            assertThat(config.retryBackoffMultiplier()).isEqualTo(3.0);
            assertThat(config.retryOnExitCodes()).containsExactly(1, 2, 3);
        }

        @Test
        void constructor_withNullRetryOnExitCodes_shouldThrowException() {
            assertThatThrownBy(() -> new JobsConfig(3600, 3, 30, 2.0, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("retryOnExitCodes");
        }

        @Test
        void constructor_withZeroTimeout_shouldThrowException() {
            assertThatThrownBy(() -> new JobsConfig(0, 3, 30, 2.0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultTimeoutSeconds");
        }

        @Test
        void constructor_withNegativeMaxRetries_shouldThrowException() {
            assertThatThrownBy(() -> new JobsConfig(3600, -1, 30, 2.0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetries");
        }

        @Test
        void constructor_withBackoffMultiplierLessThanOne_shouldThrowException() {
            assertThatThrownBy(() -> new JobsConfig(3600, 3, 30, 0.5, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryBackoffMultiplier");
        }

        @Test
        void constructor_shouldCreateDefensiveCopyOfExitCodes() {
            List<Integer> codes = new java.util.ArrayList<>();
            codes.add(1);
            JobsConfig config = new JobsConfig(3600, 3, 30, 2.0, codes);
            codes.add(2);

            assertThat(config.retryOnExitCodes()).containsExactly(1);
        }

        @Test
        void withDefaults_shouldReturnConfigWithDefaultValues() {
            JobsConfig config = JobsConfig.withDefaults();

            assertThat(config.defaultTimeoutSeconds()).isEqualTo(3600);
            assertThat(config.maxRetries()).isEqualTo(3);
            assertThat(config.retryDelaySeconds()).isEqualTo(30);
            assertThat(config.retryBackoffMultiplier()).isEqualTo(2.0);
            assertThat(config.retryOnExitCodes()).isEmpty();
        }
    }

    @Nested
    class CleanupConfigTests {

        @Test
        void constructor_withValidValues_shouldCreateInstance() {
            CleanupConfig config = new CleanupConfig(false, 24, 30, false, 48);

            assertThat(config.enabled()).isFalse();
            assertThat(config.maxAgeHours()).isEqualTo(24);
            assertThat(config.cleanupIntervalMinutes()).isEqualTo(30);
            assertThat(config.keepFailedJobs()).isFalse();
            assertThat(config.keepFailedJobsHours()).isEqualTo(48);
        }

        @Test
        void constructor_withZeroMaxAgeHours_shouldThrowException() {
            assertThatThrownBy(() -> new CleanupConfig(true, 0, 60, true, 168))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAgeHours");
        }

        @Test
        void constructor_withZeroCleanupInterval_shouldThrowException() {
            assertThatThrownBy(() -> new CleanupConfig(true, 72, 0, true, 168))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cleanupIntervalMinutes");
        }

        @Test
        void withDefaults_shouldReturnConfigWithDefaultValues() {
            CleanupConfig config = CleanupConfig.withDefaults();

            assertThat(config.enabled()).isTrue();
            assertThat(config.maxAgeHours()).isEqualTo(72);
            assertThat(config.cleanupIntervalMinutes()).isEqualTo(60);
            assertThat(config.keepFailedJobs()).isTrue();
            assertThat(config.keepFailedJobsHours()).isEqualTo(168);
        }
    }

    @Nested
    class StorageConfigTests {

        @Test
        void constructor_withValidValues_shouldCreateInstance() {
            StorageConfig config = new StorageConfig(Path.of("/data/barn"), 100, true);

            assertThat(config.baseDir()).isEqualTo(Path.of("/data/barn"));
            assertThat(config.maxDiskUsageGb()).isEqualTo(100);
            assertThat(config.preserveWorkDir()).isTrue();
        }

        @Test
        void constructor_withNullBaseDir_shouldThrowException() {
            assertThatThrownBy(() -> new StorageConfig(null, 50, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseDir");
        }

        @Test
        void constructor_withZeroMaxDiskUsage_shouldThrowException() {
            assertThatThrownBy(() -> new StorageConfig(Path.of("/tmp/barn"), 0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDiskUsageGb");
        }

        @Test
        void withDefaults_shouldReturnConfigWithDefaultValues() {
            StorageConfig config = StorageConfig.withDefaults();

            assertThat(config.baseDir()).isNotNull();
            assertThat(config.maxDiskUsageGb()).isEqualTo(50);
            assertThat(config.preserveWorkDir()).isFalse();
        }
    }

    @Nested
    class TopLevelConfigTests {

        @Test
        void constructor_withValidValues_shouldCreateInstance() {
            Config config = new Config(
                ServiceConfig.withDefaults(),
                JobsConfig.withDefaults(),
                CleanupConfig.withDefaults(),
                StorageConfig.withDefaults(),
                LoadLevelConfig.withDefaults()
            );

            assertThat(config.service()).isNotNull();
            assertThat(config.jobs()).isNotNull();
            assertThat(config.cleanup()).isNotNull();
            assertThat(config.storage()).isNotNull();
            assertThat(config.loadLevels()).isNotNull();
        }

        @Test
        void constructor_withNullService_shouldThrowException() {
            assertThatThrownBy(() -> new Config(
                null,
                JobsConfig.withDefaults(),
                CleanupConfig.withDefaults(),
                StorageConfig.withDefaults(),
                LoadLevelConfig.withDefaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("service");
        }

        @Test
        void constructor_withNullJobs_shouldThrowException() {
            assertThatThrownBy(() -> new Config(
                ServiceConfig.withDefaults(),
                null,
                CleanupConfig.withDefaults(),
                StorageConfig.withDefaults(),
                LoadLevelConfig.withDefaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jobs");
        }

        @Test
        void constructor_withNullLoadLevels_shouldThrowException() {
            assertThatThrownBy(() -> new Config(
                ServiceConfig.withDefaults(),
                JobsConfig.withDefaults(),
                CleanupConfig.withDefaults(),
                StorageConfig.withDefaults(),
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("loadLevels");
        }

        @Test
        void withDefaults_shouldReturnFullyPopulatedConfig() {
            Config config = Config.withDefaults();

            assertThat(config.service()).isNotNull();
            assertThat(config.jobs()).isNotNull();
            assertThat(config.cleanup()).isNotNull();
            assertThat(config.storage()).isNotNull();
            assertThat(config.loadLevels()).isNotNull();
        }
    }
}
