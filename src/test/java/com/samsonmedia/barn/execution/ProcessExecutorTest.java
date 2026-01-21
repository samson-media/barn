package com.samsonmedia.barn.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for ProcessExecutor.
 */
class ProcessExecutorTest {

    @TempDir
    private Path tempDir;

    private ProcessExecutor executor;
    private Path stdoutFile;
    private Path stderrFile;

    @BeforeEach
    void setUp() {
        executor = new ProcessExecutor();
        stdoutFile = tempDir.resolve("logs/stdout.log");
        stderrFile = tempDir.resolve("logs/stderr.log");
    }

    @Nested
    class Execute {

        @Test
        void execute_withValidCommand_shouldStartProcess() throws IOException, InterruptedException {
            List<String> command = getEchoCommand("hello");

            Process process = executor.execute(command, tempDir, stdoutFile, stderrFile);

            assertThat(process).isNotNull();
            assertThat(process.pid()).isGreaterThan(0);

            process.waitFor();
            assertThat(process.exitValue()).isZero();
        }

        @Test
        void execute_shouldCaptureStdout() throws IOException, InterruptedException {
            List<String> command = getEchoCommand("hello world");

            Process process = executor.execute(command, tempDir, stdoutFile, stderrFile);
            process.waitFor();

            assertThat(Files.exists(stdoutFile)).isTrue();
            String content = Files.readString(stdoutFile);
            assertThat(content).contains("hello world");
        }

        @Test
        void execute_shouldCaptureStderr() throws IOException, InterruptedException {
            // Use a command that writes to stderr
            List<String> command = getStderrCommand("error message");

            Process process = executor.execute(command, tempDir, stdoutFile, stderrFile);
            process.waitFor();

            assertThat(Files.exists(stderrFile)).isTrue();
            String content = Files.readString(stderrFile);
            assertThat(content).contains("error message");
        }

        @Test
        void execute_shouldCreateParentDirectories() throws IOException, InterruptedException {
            Path nestedStdout = tempDir.resolve("deep/nested/logs/stdout.log");
            Path nestedStderr = tempDir.resolve("deep/nested/logs/stderr.log");

            Process process = executor.execute(getEchoCommand("test"), tempDir, nestedStdout, nestedStderr);
            process.waitFor();

            assertThat(Files.exists(nestedStdout.getParent())).isTrue();
        }

        @Test
        void execute_withEnvironment_shouldPassEnvironment() throws IOException, InterruptedException {
            Map<String, String> env = Map.of("TEST_VAR", "test_value");
            List<String> command = getEnvPrintCommand("TEST_VAR");

            Process process = executor.execute(command, tempDir, stdoutFile, stderrFile, env);
            process.waitFor();

            String content = Files.readString(stdoutFile);
            assertThat(content).contains("test_value");
        }

        @Test
        void execute_withNullCommand_shouldThrowException() {
            assertThatThrownBy(() ->
                executor.execute(null, tempDir, stdoutFile, stderrFile))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void execute_withEmptyCommand_shouldThrowException() {
            assertThatThrownBy(() ->
                executor.execute(List.of(), tempDir, stdoutFile, stderrFile))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void execute_withNullWorkingDir_shouldThrowException() {
            assertThatThrownBy(() ->
                executor.execute(getEchoCommand("test"), null, stdoutFile, stderrFile))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class IsRunning {

        @Test
        void isRunning_withRunningProcess_shouldReturnTrue() throws IOException, InterruptedException {
            Process process = executor.execute(getSleepCommand(60), tempDir, stdoutFile, stderrFile);

            try {
                assertThat(executor.isRunning(process.pid())).isTrue();
            } finally {
                process.destroyForcibly();
                process.waitFor();
            }
        }

        @Test
        void isRunning_withTerminatedProcess_shouldReturnFalse() throws IOException, InterruptedException {
            Process process = executor.execute(getEchoCommand("test"), tempDir, stdoutFile, stderrFile);
            process.waitFor();

            // Process handle might still exist briefly after termination
            Thread.sleep(100);
            assertThat(executor.isRunning(process.pid())).isFalse();
        }
    }

    @Nested
    class Kill {

        @Test
        void kill_withRunningProcess_shouldKillProcess() throws IOException, InterruptedException {
            Process process = executor.execute(getSleepCommand(60), tempDir, stdoutFile, stderrFile);
            long pid = process.pid();

            boolean killed = executor.kill(pid);

            assertThat(killed).isTrue();
            Thread.sleep(100);
            assertThat(executor.isRunning(pid)).isFalse();
        }

        @Test
        void kill_withNonexistentPid_shouldReturnFalse() {
            assertThat(executor.kill(999999999L)).isFalse();
        }
    }

    @Nested
    class WaitFor {

        @Test
        void waitFor_withTimeout_shouldReturnExitCode() throws IOException, InterruptedException {
            Process process = executor.execute(getEchoCommand("test"), tempDir, stdoutFile, stderrFile);

            Optional<Integer> exitCode = executor.waitFor(process, Duration.ofSeconds(10));

            assertThat(exitCode).isPresent().hasValue(0);
        }

        @Test
        void waitFor_withExpiredTimeout_shouldReturnEmpty() throws IOException, InterruptedException {
            Process process = executor.execute(getSleepCommand(60), tempDir, stdoutFile, stderrFile);

            try {
                Optional<Integer> exitCode = executor.waitFor(process, Duration.ofMillis(100));

                assertThat(exitCode).isEmpty();
            } finally {
                process.destroyForcibly();
                process.waitFor();
            }
        }

        @Test
        void waitForBlocking_shouldReturnExitCode() throws IOException, InterruptedException {
            Process process = executor.execute(getEchoCommand("test"), tempDir, stdoutFile, stderrFile);

            int exitCode = executor.waitFor(process);

            assertThat(exitCode).isZero();
        }
    }

    // Platform-agnostic command helpers

    private List<String> getEchoCommand(String message) {
        if (isWindows()) {
            return List.of("cmd", "/c", "echo", message);
        }
        return List.of("echo", message);
    }

    private List<String> getStderrCommand(String message) {
        if (isWindows()) {
            return List.of("cmd", "/c", "echo", message, "1>&2");
        }
        return List.of("sh", "-c", "echo '" + message + "' >&2");
    }

    private List<String> getEnvPrintCommand(String varName) {
        if (isWindows()) {
            return List.of("cmd", "/c", "echo", "%" + varName + "%");
        }
        return List.of("sh", "-c", "echo $" + varName);
    }

    private List<String> getSleepCommand(int seconds) {
        if (isWindows()) {
            return List.of("cmd", "/c", "timeout", "/t", String.valueOf(seconds), "/nobreak");
        }
        return List.of("sleep", String.valueOf(seconds));
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
