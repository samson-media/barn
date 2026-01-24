package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for ConfigLoader.
 */
class ConfigLoaderTest {

    @TempDir
    private Path tempDir;

    @Nested
    class DefaultLoading {

        @Test
        void load_withNoConfigFiles_shouldReturnDefaults() {
            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.load();

            assertThat(config.service().logLevel()).isEqualTo(LogLevel.INFO);
            assertThat(config.service().maxConcurrentJobs()).isEqualTo(4);
            assertThat(config.jobs().maxRetries()).isEqualTo(3);
            assertThat(config.cleanup().enabled()).isTrue();
        }
    }

    @Nested
    class FileLoading {

        @Test
        void loadFromFile_withValidFile_shouldParseValues() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [service]
                log_level = "debug"
                max_concurrent_jobs = 8

                [jobs]
                max_retries = 5
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.loadFromFile(configFile);

            assertThat(config.service().logLevel()).isEqualTo(LogLevel.DEBUG);
            assertThat(config.service().maxConcurrentJobs()).isEqualTo(8);
            assertThat(config.jobs().maxRetries()).isEqualTo(5);
        }

        @Test
        void loadFromFile_withPartialConfig_shouldUseDefaultsForMissing() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [service]
                log_level = "warn"
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.loadFromFile(configFile);

            assertThat(config.service().logLevel()).isEqualTo(LogLevel.WARN);
            assertThat(config.service().maxConcurrentJobs()).isEqualTo(4); // Default
            assertThat(config.jobs().maxRetries()).isEqualTo(3); // Default
        }

        @Test
        void loadFromFile_withNonexistentFile_shouldThrowException() {
            Path configFile = tempDir.resolve("nonexistent.conf");

            ConfigLoader loader = new ConfigLoader(Map.of());
            assertThatThrownBy(() -> loader.loadFromFile(configFile))
                .isInstanceOf(ConfigLoader.ConfigException.class)
                .hasMessageContaining("Failed to read");
        }

        @Test
        void loadFromFile_withInvalidToml_shouldThrowException() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, """
                not valid toml
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            assertThatThrownBy(() -> loader.loadFromFile(configFile))
                .isInstanceOf(ConfigLoader.ConfigException.class)
                .hasMessageContaining("Failed to parse");
        }
    }

    @Nested
    class EnvironmentOverrides {

        @Test
        void load_withEnvOverride_shouldOverrideFileValue() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [service]
                log_level = "info"
                """);

            Map<String, String> env = new HashMap<>();
            env.put("BARN_SERVICE_LOG_LEVEL", "debug");

            ConfigLoader loader = new ConfigLoader(env);
            Config config = loader.load(Optional.of(configFile));

            assertThat(config.service().logLevel()).isEqualTo(LogLevel.DEBUG);
        }

        @Test
        void load_withEnvOverrideInteger_shouldParseCorrectly() {
            Map<String, String> env = new HashMap<>();
            env.put("BARN_SERVICE_MAX_CONCURRENT_JOBS", "16");

            ConfigLoader loader = new ConfigLoader(env);
            Config config = loader.load();

            assertThat(config.service().maxConcurrentJobs()).isEqualTo(16);
        }

        @Test
        void load_withEnvOverrideBoolean_shouldParseCorrectly() {
            Map<String, String> env = new HashMap<>();
            env.put("BARN_CLEANUP_ENABLED", "false");

            ConfigLoader loader = new ConfigLoader(env);
            Config config = loader.load();

            assertThat(config.cleanup().enabled()).isFalse();
        }

        @Test
        void load_withEnvOverrideFloat_shouldParseCorrectly() {
            Map<String, String> env = new HashMap<>();
            env.put("BARN_JOBS_RETRY_BACKOFF_MULTIPLIER", "3.5");

            ConfigLoader loader = new ConfigLoader(env);
            Config config = loader.load();

            assertThat(config.jobs().retryBackoffMultiplier()).isEqualTo(3.5);
        }

        @Test
        void load_withMultipleEnvOverrides_shouldApplyAll() {
            Map<String, String> env = new HashMap<>();
            env.put("BARN_SERVICE_LOG_LEVEL", "error");
            env.put("BARN_JOBS_MAX_RETRIES", "10");
            env.put("BARN_CLEANUP_MAX_AGE_HOURS", "24");

            ConfigLoader loader = new ConfigLoader(env);
            Config config = loader.load();

            assertThat(config.service().logLevel()).isEqualTo(LogLevel.ERROR);
            assertThat(config.jobs().maxRetries()).isEqualTo(10);
            assertThat(config.cleanup().maxAgeHours()).isEqualTo(24);
        }

        @Test
        void load_withNonBarnEnvVars_shouldIgnore() {
            Map<String, String> env = new HashMap<>();
            env.put("PATH", "/usr/bin");
            env.put("HOME", "/home/user");
            env.put("SOME_OTHER_VAR", "value");

            ConfigLoader loader = new ConfigLoader(env);
            Config config = loader.load();

            // Should load defaults without error
            assertThat(config.service().logLevel()).isEqualTo(LogLevel.INFO);
        }
    }

    @Nested
    class PathConfiguration {

        @Test
        void load_withIpcSocketPath_shouldParsePath() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [service]
                ipc_socket = "/custom/path/barn.sock"
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.loadFromFile(configFile);

            assertThat(config.service().ipcSocket()).isEqualTo(Path.of("/custom/path/barn.sock"));
        }

        @Test
        void load_withBaseDir_shouldParsePath() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [storage]
                base_dir = "/data/barn"
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.loadFromFile(configFile);

            assertThat(config.storage().baseDir()).isEqualTo(Path.of("/data/barn"));
        }
    }

    @Nested
    class ValidationErrors {

        @Test
        void load_withInvalidLogLevel_shouldThrowException() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [service]
                log_level = "invalid"
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            assertThatThrownBy(() -> loader.loadFromFile(configFile))
                .isInstanceOf(ConfigLoader.ConfigException.class)
                .hasMessageContaining("log_level");
        }

        @Test
        void load_withInvalidMaxConcurrentJobs_shouldThrowException() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [service]
                max_concurrent_jobs = 0
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            assertThatThrownBy(() -> loader.loadFromFile(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentJobs");
        }

        @Test
        void load_withInvalidBackoffMultiplier_shouldThrowException() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [jobs]
                retry_backoff_multiplier = 0.5
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            assertThatThrownBy(() -> loader.loadFromFile(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryBackoffMultiplier");
        }
    }

    @Nested
    class ExplicitPathLoading {

        @Test
        void load_withExplicitPath_shouldUseExplicitPath() throws IOException {
            Path configFile = tempDir.resolve("custom.conf");
            Files.writeString(configFile, """
                [service]
                log_level = "error"
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.load(Optional.of(configFile));

            assertThat(config.service().logLevel()).isEqualTo(LogLevel.ERROR);
        }

        @Test
        void load_withEmptyExplicitPath_shouldUseDefaults() {
            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.load(Optional.empty());

            assertThat(config.service().logLevel()).isEqualTo(LogLevel.INFO);
        }
    }

    @Nested
    class LoadLevelsConfig {

        @Test
        void load_withLoadLevels_shouldParseValues() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [load_levels]
                max_high_jobs = 4
                max_medium_jobs = 16
                max_low_jobs = 64
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.loadFromFile(configFile);

            assertThat(config.loadLevels().maxHighJobs()).isEqualTo(4);
            assertThat(config.loadLevels().maxMediumJobs()).isEqualTo(16);
            assertThat(config.loadLevels().maxLowJobs()).isEqualTo(64);
        }

        @Test
        void load_withNoLoadLevels_shouldUseDefaults() {
            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.load();

            assertThat(config.loadLevels().maxHighJobs()).isEqualTo(2);
            assertThat(config.loadLevels().maxMediumJobs()).isEqualTo(8);
            assertThat(config.loadLevels().maxLowJobs()).isEqualTo(32);
        }

        @Test
        void load_withEnvOverride_shouldOverrideLoadLevelValues() {
            // Environment variables use BARN_LOADLEVELS_ (no underscore) because
            // the parser splits on first underscore for section name
            Map<String, String> env = new HashMap<>();
            env.put("BARN_LOADLEVELS_MAX_HIGH_JOBS", "10");

            ConfigLoader loader = new ConfigLoader(env);
            Config config = loader.load();

            assertThat(config.loadLevels().maxHighJobs()).isEqualTo(10);
        }
    }

    @Nested
    class RetryOnExitCodes {

        @Test
        void load_withRetryOnExitCodes_shouldParseList() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [jobs]
                retry_on_exit_codes = [1, 2, 255]
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.loadFromFile(configFile);

            assertThat(config.jobs().retryOnExitCodes()).containsExactly(1, 2, 255);
        }

        @Test
        void load_withEmptyRetryOnExitCodes_shouldReturnEmptyList() throws IOException {
            Path configFile = tempDir.resolve("barn.conf");
            Files.writeString(configFile, """
                [jobs]
                retry_on_exit_codes = []
                """);

            ConfigLoader loader = new ConfigLoader(Map.of());
            Config config = loader.loadFromFile(configFile);

            assertThat(config.jobs().retryOnExitCodes()).isEmpty();
        }
    }
}
