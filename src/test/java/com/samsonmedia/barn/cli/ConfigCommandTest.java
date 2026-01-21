package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Tests for ConfigCommand and its subcommands.
 */
class ConfigCommandTest {

    @TempDir
    private Path tempDir;

    private StringWriter outWriter;
    private StringWriter errWriter;
    private CommandLine cmd;

    @BeforeEach
    void setUp() {
        outWriter = new StringWriter();
        errWriter = new StringWriter();
        cmd = new CommandLine(new BarnCommand());
        cmd.setOut(new PrintWriter(outWriter));
        cmd.setErr(new PrintWriter(errWriter));
    }

    @Nested
    class ConfigParentCommand {

        @Test
        void config_withoutSubcommand_shouldShowUsage() {
            int exitCode = cmd.execute("config");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("show");
            assertThat(outWriter.toString()).contains("validate");
            assertThat(outWriter.toString()).contains("init");
        }

        @Test
        void config_withHelp_shouldShowUsage() {
            int exitCode = cmd.execute("config", "--help");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("Configuration management");
        }
    }

    @Nested
    class ConfigShowCommandTests {

        @Test
        void show_withDefaults_shouldShowDefaultConfiguration() {
            int exitCode = cmd.execute("config", "show", "--defaults");

            assertThat(exitCode).isZero();
            String output = outWriter.toString();
            assertThat(output).contains("Barn Configuration");
            assertThat(output).contains("[service]");
            assertThat(output).contains("[jobs]");
            assertThat(output).contains("[cleanup]");
            assertThat(output).contains("[storage]");
            assertThat(output).contains("Showing default values");
        }

        @Test
        void show_withDefaults_shouldShowLogLevel() {
            int exitCode = cmd.execute("config", "show", "--defaults");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("log_level = info");
        }

        @Test
        void show_withDefaults_shouldShowMaxConcurrentJobs() {
            int exitCode = cmd.execute("config", "show", "--defaults");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("max_concurrent_jobs = 4");
        }

        @Test
        void show_withJsonFormat_shouldOutputJson() {
            int exitCode = cmd.execute("config", "show", "--defaults", "--output", "JSON");

            assertThat(exitCode).isZero();
            String output = outWriter.toString();
            assertThat(output).contains("\"service\"");
            assertThat(output).contains("\"log_level\"");
        }

        @Test
        void show_withXmlFormat_shouldOutputXml() {
            int exitCode = cmd.execute("config", "show", "--defaults", "--output", "XML");

            assertThat(exitCode).isZero();
            String output = outWriter.toString();
            assertThat(output).contains("<service>");
            assertThat(output).contains("</service>");
        }

        @Test
        void show_withNonExistentConfig_shouldShowDefaultsMessage() {
            int exitCode = cmd.execute("config", "show");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("(none found, using defaults)");
        }

        @Test
        void show_withConfigFile_shouldShowConfigPath() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, "[service]\nlog_level = \"debug\"\n");

            int exitCode = cmd.execute("config", "show", "--config", configFile.toString());

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("Config file:");
            assertThat(outWriter.toString()).contains(configFile.toString());
        }

        @Test
        void show_withHelp_shouldShowUsage() {
            int exitCode = cmd.execute("config", "show", "--help");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("Show effective configuration");
        }
    }

    @Nested
    class ConfigValidateCommandTests {

        @Test
        void validate_withValidConfig_shouldSucceed() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [service]
                log_level = "info"
                max_concurrent_jobs = 4

                [jobs]
                default_timeout_seconds = 3600
                max_retries = 3

                [cleanup]
                enabled = true
                max_age_hours = 168

                [storage]
                base_dir = "/tmp/barn"
                max_disk_usage_gb = 100
                """);

            int exitCode = cmd.execute("config", "validate", "--file", configFile.toString());

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("Configuration is valid");
        }

        @Test
        void validate_withNonExistentFile_shouldFail() {
            Path nonExistent = tempDir.resolve("nonexistent.toml");

            int exitCode = cmd.execute("config", "validate", "--file", nonExistent.toString());

            assertThat(exitCode).isNotZero();
            assertThat(errWriter.toString()).contains("Configuration file not found");
        }

        @Test
        void validate_withInvalidSection_shouldReportError() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [unknown_section]
                foo = "bar"
                """);

            int exitCode = cmd.execute("config", "validate", "--file", configFile.toString());

            assertThat(exitCode).isNotZero();
            assertThat(outWriter.toString()).contains("Unknown section");
            assertThat(outWriter.toString()).contains("unknown_section");
        }

        @Test
        void validate_withInvalidKey_shouldReportError() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [service]
                invalid_key = "value"
                """);

            int exitCode = cmd.execute("config", "validate", "--file", configFile.toString());

            assertThat(exitCode).isNotZero();
            assertThat(outWriter.toString()).contains("Unknown key");
            assertThat(outWriter.toString()).contains("invalid_key");
        }

        @Test
        void validate_withInvalidLogLevel_shouldReportError() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [service]
                log_level = "invalid"
                """);

            int exitCode = cmd.execute("config", "validate", "--file", configFile.toString());

            assertThat(exitCode).isNotZero();
            assertThat(outWriter.toString()).contains("Invalid value for 'log_level'");
        }

        @Test
        void validate_withNegativeInteger_shouldReportError() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [service]
                max_concurrent_jobs = -1
                """);

            int exitCode = cmd.execute("config", "validate", "--file", configFile.toString());

            assertThat(exitCode).isNotZero();
            assertThat(outWriter.toString()).contains("max_concurrent_jobs");
            assertThat(outWriter.toString()).contains("must be positive");
        }

        @Test
        void validate_withInvalidBackoffMultiplier_shouldReportError() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [jobs]
                retry_backoff_multiplier = 0.5
                """);

            int exitCode = cmd.execute("config", "validate", "--file", configFile.toString());

            assertThat(exitCode).isNotZero();
            assertThat(outWriter.toString()).contains("retry_backoff_multiplier");
            assertThat(outWriter.toString()).contains("at least 1.0");
        }

        @Test
        void validate_withInvalidRetryOnExitCodes_shouldReportError() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [jobs]
                retry_on_exit_codes = "not a list"
                """);

            int exitCode = cmd.execute("config", "validate", "--file", configFile.toString());

            assertThat(exitCode).isNotZero();
            assertThat(outWriter.toString()).contains("retry_on_exit_codes");
            assertThat(outWriter.toString()).contains("must be a list");
        }

        @Test
        void validate_withInvalidBoolean_shouldReportError() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [cleanup]
                enabled = "yes"
                """);

            int exitCode = cmd.execute("config", "validate", "--file", configFile.toString());

            assertThat(exitCode).isNotZero();
            assertThat(outWriter.toString()).contains("enabled");
            assertThat(outWriter.toString()).contains("must be true or false");
        }

        @Test
        void validate_withJsonFormat_shouldOutputJson() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, """
                [unknown]
                foo = "bar"
                """);

            int exitCode = cmd.execute("config", "validate",
                "--file", configFile.toString(), "--output", "JSON");

            assertThat(exitCode).isNotZero();
            String output = outWriter.toString();
            assertThat(output).contains("\"valid\"");
            assertThat(output).contains("\"errors\"");
        }

        @Test
        void validate_withHelp_shouldShowUsage() {
            int exitCode = cmd.execute("config", "validate", "--help");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("Validate configuration file");
        }
    }

    @Nested
    class ConfigInitCommandTests {

        @Test
        void init_shouldCreateConfigFile() {
            Path configFile = tempDir.resolve("barn.toml");

            int exitCode = cmd.execute("config", "init", "--path", configFile.toString());

            assertThat(exitCode).isZero();
            assertThat(Files.exists(configFile)).isTrue();
            assertThat(outWriter.toString()).contains("Configuration file created");
        }

        @Test
        void init_shouldCreateValidToml() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");

            cmd.execute("config", "init", "--path", configFile.toString());

            String content = Files.readString(configFile);
            assertThat(content).contains("[service]");
            assertThat(content).contains("[jobs]");
            assertThat(content).contains("[cleanup]");
            assertThat(content).contains("[storage]");
        }

        @Test
        void init_shouldIncludeComments() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");

            cmd.execute("config", "init", "--path", configFile.toString());

            String content = Files.readString(configFile);
            assertThat(content).contains("# Barn Configuration");
            assertThat(content).contains("# Logging verbosity");
        }

        @Test
        void init_existingFile_withoutForce_shouldFail() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, "existing content");

            int exitCode = cmd.execute("config", "init", "--path", configFile.toString());

            assertThat(exitCode).isNotZero();
            assertThat(errWriter.toString()).contains("already exists");
            assertThat(errWriter.toString()).contains("--force");
        }

        @Test
        void init_existingFile_withForce_shouldOverwrite() throws IOException {
            Path configFile = tempDir.resolve("barn.toml");
            Files.writeString(configFile, "old content");

            int exitCode = cmd.execute("config", "init", "--path", configFile.toString(), "--force");

            assertThat(exitCode).isZero();
            String content = Files.readString(configFile);
            assertThat(content).contains("[service]");
            assertThat(content).doesNotContain("old content");
        }

        @Test
        void init_shouldCreateParentDirectories() {
            Path configFile = tempDir.resolve("subdir/nested/barn.toml");

            int exitCode = cmd.execute("config", "init", "--path", configFile.toString());

            assertThat(exitCode).isZero();
            assertThat(Files.exists(configFile)).isTrue();
        }

        @Test
        void init_withHelp_shouldShowUsage() {
            int exitCode = cmd.execute("config", "init", "--help");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("Create default configuration file");
        }
    }
}
