package com.samsonmedia.barn.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ProcessUtils.
 */
class ProcessUtilsTest {

    @Nested
    class IsAlive {

        @Test
        void isAlive_withCurrentProcess_shouldReturnTrue() {
            long currentPid = ProcessUtils.getCurrentPid();

            assertThat(ProcessUtils.isAlive(currentPid)).isTrue();
        }

        @Test
        void isAlive_withNonexistentPid_shouldReturnFalse() {
            // Use a very high PID that's unlikely to exist
            assertThat(ProcessUtils.isAlive(999999999L)).isFalse();
        }

        @Test
        void isAlive_withRunningProcess_shouldReturnTrue() throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder(getSleepCommand(5));
            Process process = builder.start();

            try {
                assertThat(ProcessUtils.isAlive(process.pid())).isTrue();
            } finally {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    @Nested
    class KillTree {

        @Test
        void killTree_withRunningProcess_shouldKillProcess() throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder(getSleepCommand(60));
            Process process = builder.start();
            long pid = process.pid();

            assertThat(ProcessUtils.isAlive(pid)).isTrue();

            boolean killed = ProcessUtils.killTree(pid);

            assertThat(killed).isTrue();
            // Wait a moment for the process to die
            Thread.sleep(100);
            assertThat(ProcessUtils.isAlive(pid)).isFalse();
        }

        @Test
        void killTree_withNonexistentPid_shouldReturnFalse() {
            assertThat(ProcessUtils.killTree(999999999L)).isFalse();
        }
    }

    @Nested
    class KillTreeForcibly {

        @Test
        void killTreeForcibly_withRunningProcess_shouldKillProcess() throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder(getSleepCommand(60));
            Process process = builder.start();
            long pid = process.pid();

            assertThat(ProcessUtils.isAlive(pid)).isTrue();

            boolean killed = ProcessUtils.killTreeForcibly(pid);

            assertThat(killed).isTrue();
            Thread.sleep(100);
            assertThat(ProcessUtils.isAlive(pid)).isFalse();
        }

        @Test
        void killTreeForcibly_withNonexistentPid_shouldReturnFalse() {
            assertThat(ProcessUtils.killTreeForcibly(999999999L)).isFalse();
        }
    }

    @Nested
    class GetHandle {

        @Test
        void getHandle_withCurrentProcess_shouldReturnHandle() {
            long currentPid = ProcessUtils.getCurrentPid();

            Optional<ProcessHandle> handle = ProcessUtils.getHandle(currentPid);

            assertThat(handle).isPresent();
            assertThat(handle.get().pid()).isEqualTo(currentPid);
        }

        @Test
        void getHandle_withNonexistentPid_shouldReturnEmpty() {
            Optional<ProcessHandle> handle = ProcessUtils.getHandle(999999999L);

            assertThat(handle).isEmpty();
        }
    }

    @Nested
    class GetExitCode {

        @Test
        void getExitCode_withRunningProcess_shouldReturnEmpty() throws IOException {
            ProcessBuilder builder = new ProcessBuilder(getSleepCommand(60));
            Process process = builder.start();

            try {
                assertThat(ProcessUtils.getExitCode(process)).isEmpty();
            } finally {
                process.destroyForcibly();
            }
        }

        @Test
        void getExitCode_withTerminatedProcess_shouldReturnCode() throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder(getEchoCommand("test"));
            Process process = builder.start();
            process.waitFor();

            assertThat(ProcessUtils.getExitCode(process)).isPresent().hasValue(0);
        }
    }

    @Nested
    class GetCurrentPid {

        @Test
        void getCurrentPid_shouldReturnPositiveValue() {
            long pid = ProcessUtils.getCurrentPid();

            assertThat(pid).isGreaterThan(0);
        }

        @Test
        void getCurrentPid_shouldReturnConsistentValue() {
            long pid1 = ProcessUtils.getCurrentPid();
            long pid2 = ProcessUtils.getCurrentPid();

            assertThat(pid1).isEqualTo(pid2);
        }
    }

    // Platform-agnostic command helpers

    private List<String> getSleepCommand(int seconds) {
        if (isWindows()) {
            return List.of("cmd", "/c", "timeout", "/t", String.valueOf(seconds), "/nobreak");
        }
        return List.of("sleep", String.valueOf(seconds));
    }

    private List<String> getEchoCommand(String message) {
        if (isWindows()) {
            return List.of("cmd", "/c", "echo", message);
        }
        return List.of("echo", message);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
