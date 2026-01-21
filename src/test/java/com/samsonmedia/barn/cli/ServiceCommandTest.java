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
 * Tests for service commands.
 */
class ServiceCommandTest {

    @TempDir
    private Path tempDir;

    private StringWriter stdout;
    private StringWriter stderr;
    private CommandLine cmd;

    @BeforeEach
    void setUp() {
        stdout = new StringWriter();
        stderr = new StringWriter();
        cmd = new CommandLine(new BarnCommand());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Nested
    class ServiceParentCommand {

        @Test
        void service_withoutSubcommand_shouldShowUsage() {
            // Act
            int exitCode = cmd.execute("service");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("Manage the Barn service");
        }

        @Test
        void service_withHelp_shouldShowUsage() {
            // Act
            int exitCode = cmd.execute("service", "--help");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString())
                .contains("service")
                .contains("start")
                .contains("stop")
                .contains("status");
        }
    }

    @Nested
    class ServiceStartCommand {

        @Test
        void start_shouldOutputStartMessage() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "start", "--barn-dir", barnDir.toString(), "--foreground-test");

            // Assert
            assertThat(stdout.toString()).contains("started");
        }

        @Test
        void start_withForeground_shouldRunInForeground() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "start", "--barn-dir", barnDir.toString(), "--foreground", "--dry-run");

            // Assert
            assertThat(exitCode).isEqualTo(0);
        }

        @Test
        void start_whenAlreadyRunning_shouldShowError() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Create a fake PID file with current process PID
            Path pidFile = barnDir.resolve("barn.pid");
            Files.writeString(pidFile, String.valueOf(ProcessHandle.current().pid()));

            // Act
            int exitCode = cmd.execute("service", "start", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isNotEqualTo(0);
            assertThat(stderr.toString()).contains("already running");
        }

        @Test
        void start_withDryRun_shouldNotActuallyStart() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "start", "--barn-dir", barnDir.toString(), "--dry-run");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("dry run");
            // PID file should not exist after dry run
            assertThat(Files.exists(barnDir.resolve("barn.pid"))).isFalse();
        }

        @Test
        void start_withStalePidFile_shouldSucceed() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Create a PID file with non-existent PID
            Path pidFile = barnDir.resolve("barn.pid");
            Files.writeString(pidFile, "999999999");

            // Act
            int exitCode = cmd.execute("service", "start", "--barn-dir", barnDir.toString(), "--foreground-test");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("started");
        }
    }

    @Nested
    class ServiceStopCommand {

        @Test
        void stop_whenNotRunning_shouldShowError() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "stop", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isNotEqualTo(0);
            assertThat(stderr.toString()).contains("not running");
        }

        @Test
        void stop_withForce_shouldForceStop() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act - force stop even when not running should not error
            int exitCode = cmd.execute("service", "stop", "--barn-dir", barnDir.toString(), "--force");

            // Assert
            // Force stop without running service just succeeds silently
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("not running");
        }

        @Test
        void stop_withStalePidFile_shouldCleanupAndShowError() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Create a PID file with a non-existent PID
            Path pidFile = barnDir.resolve("barn.pid");
            Files.writeString(pidFile, "999999999");

            // Act
            int exitCode = cmd.execute("service", "stop", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isNotEqualTo(0);
            assertThat(stderr.toString()).contains("not running");
            assertThat(Files.exists(pidFile)).isFalse();
        }

        @Test
        void stop_withForceAndStalePidFile_shouldSucceed() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Create a PID file with a non-existent PID
            Path pidFile = barnDir.resolve("barn.pid");
            Files.writeString(pidFile, "999999999");

            // Act
            int exitCode = cmd.execute("service", "stop", "--barn-dir", barnDir.toString(), "--force");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("not running");
            assertThat(Files.exists(pidFile)).isFalse();
        }

        @Test
        void stop_withInvalidPidFile_shouldShowError() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Create a PID file with invalid content
            Path pidFile = barnDir.resolve("barn.pid");
            Files.writeString(pidFile, "not-a-number");

            // Act
            int exitCode = cmd.execute("service", "stop", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isNotEqualTo(0);
            assertThat(stderr.toString()).contains("Failed");
        }
    }

    @Nested
    class ServiceStatusCommand {

        @Test
        void status_whenNotRunning_shouldShowStopped() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "status", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString().toLowerCase()).contains("stopped");
        }

        @Test
        void status_withJsonFormat_shouldOutputJson() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "status", "--barn-dir", barnDir.toString(), "--output", "json");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("\"status\"");
            assertThat(stdout.toString()).contains("\"running\"");
        }

        @Test
        void status_withXmlFormat_shouldOutputXml() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "status", "--barn-dir", barnDir.toString(), "--output", "xml");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("<status>");
        }

        @Test
        void status_withStalePidFile_shouldShowStopped() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);
            Path pidFile = barnDir.resolve("barn.pid");
            Files.writeString(pidFile, "999999999");

            // Act
            int exitCode = cmd.execute("service", "status", "--barn-dir", barnDir.toString());

            // Assert - also verifies data directory is shown
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString().toLowerCase()).contains("stopped");
            assertThat(stdout.toString()).contains(barnDir.toString());
        }

        @Test
        void status_whenRunning_shouldShowRunningStatus() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);
            // Use current process PID to simulate a running service
            Path pidFile = barnDir.resolve("barn.pid");
            Files.writeString(pidFile, String.valueOf(ProcessHandle.current().pid()));

            // Act
            int exitCode = cmd.execute("service", "status", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString().toLowerCase()).contains("running");
            assertThat(stdout.toString()).contains("PID");
        }
    }

    @Nested
    class ServiceRestartCommand {

        @Test
        void restart_whenNotRunning_shouldShowError() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "restart", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isNotEqualTo(0);
            assertThat(stderr.toString()).contains("not running");
        }

    }

    @Nested
    class ServiceReloadCommand {

        @Test
        void reload_whenNotRunning_shouldShowError() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "reload", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isNotEqualTo(0);
            assertThat(stderr.toString()).contains("not running");
        }

    }

    @Nested
    class ServiceLogsCommand {

        @Test
        void logs_whenNoLogFile_shouldShowMessage() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Files.createDirectories(barnDir);

            // Act
            int exitCode = cmd.execute("service", "logs", "--barn-dir", barnDir.toString());

            // Assert
            // Exit code 0 if no logs exist, with message
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("No log file found");
        }

        @Test
        void logs_withLines_shouldLimitOutput() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Path logsDir = barnDir.resolve("logs");
            Files.createDirectories(logsDir);
            Path logFile = logsDir.resolve("barn.log");

            // Create a log file with multiple lines
            StringBuilder logContent = new StringBuilder();
            for (int i = 1; i <= 100; i++) {
                logContent.append("Log line ").append(i).append("\n");
            }
            Files.writeString(logFile, logContent.toString());

            // Act
            int exitCode = cmd.execute("service", "logs", "--barn-dir", barnDir.toString(), "--lines", "10");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            String output = stdout.toString();
            // Should contain last 10 lines
            assertThat(output).contains("Log line 91");
            assertThat(output).contains("Log line 100");
            // Should not contain line 90 or earlier
            assertThat(output).doesNotContain("Log line 90");
        }

        @Test
        void logs_withDefaultLines_shouldShowLast100() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Path logsDir = barnDir.resolve("logs");
            Files.createDirectories(logsDir);
            Path logFile = logsDir.resolve("barn.log");

            // Create a log file with multiple lines
            StringBuilder logContent = new StringBuilder();
            for (int i = 1; i <= 200; i++) {
                logContent.append("Log line ").append(i).append("\n");
            }
            Files.writeString(logFile, logContent.toString());

            // Act
            int exitCode = cmd.execute("service", "logs", "--barn-dir", barnDir.toString());

            // Assert
            assertThat(exitCode).isEqualTo(0);
            String output = stdout.toString();
            // Should contain last 100 lines
            assertThat(output).contains("Log line 101");
            assertThat(output).contains("Log line 200");
            // Should not contain line 100 or earlier
            assertThat(output).doesNotContain("Log line 100\n");
        }

        @Test
        void logs_withFewerLinesThanRequested_shouldShowAllLines() throws IOException {
            // Arrange
            Path barnDir = tempDir.resolve("barn");
            Path logsDir = barnDir.resolve("logs");
            Files.createDirectories(logsDir);
            Path logFile = logsDir.resolve("barn.log");

            // Create a small log file
            Files.writeString(logFile, "Line 1\nLine 2\nLine 3\n");

            // Act
            int exitCode = cmd.execute("service", "logs", "--barn-dir", barnDir.toString(), "--lines", "100");

            // Assert
            assertThat(exitCode).isEqualTo(0);
            String output = stdout.toString();
            assertThat(output).contains("Line 1");
            assertThat(output).contains("Line 2");
            assertThat(output).contains("Line 3");
        }
    }
}
