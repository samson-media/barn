package com.samsonmedia.barn.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for UsageMonitor.
 */
class UsageMonitorTest {

    @TempDir
    private Path tempDir;

    @Nested
    class Constructor {

        @Test
        void constructor_withValidArgs_shouldCreateMonitor() {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir);

            assertThat(monitor).isNotNull();
            assertThat(monitor.isRunning()).isFalse();
        }

        @Test
        void constructor_withInvalidPid_shouldThrowException() {
            assertThatThrownBy(() -> new UsageMonitor(0, tempDir, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pid");
        }

        @Test
        void constructor_withNegativePid_shouldThrowException() {
            assertThatThrownBy(() -> new UsageMonitor(-1, tempDir, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pid");
        }

        @Test
        void constructor_withInvalidInterval_shouldThrowException() {
            long pid = ProcessHandle.current().pid();

            assertThatThrownBy(() -> new UsageMonitor(pid, tempDir, tempDir, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intervalSeconds");
        }

        @Test
        void constructor_withNullWorkDir_shouldThrowException() {
            long pid = ProcessHandle.current().pid();

            assertThatThrownBy(() -> new UsageMonitor(pid, null, tempDir))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("workDir");
        }
    }

    @Nested
    class StartAndStop {

        @Test
        void start_shouldSetRunningToTrue() throws IOException {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            try (UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir, 1)) {
                monitor.start();

                assertThat(monitor.isRunning()).isTrue();
            }
        }

        @Test
        void stop_shouldSetRunningToFalse() throws IOException {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            try (UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir, 1)) {
                monitor.start();
                monitor.stop();

                assertThat(monitor.isRunning()).isFalse();
            }
        }

        @Test
        void start_twice_shouldBeIdempotent() throws IOException {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            try (UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir, 1)) {
                monitor.start();
                monitor.start(); // Should not throw

                assertThat(monitor.isRunning()).isTrue();
            }
        }

        @Test
        void stop_withoutStart_shouldBeIdempotent() throws IOException {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            try (UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir)) {
                monitor.stop(); // Should not throw

                assertThat(monitor.isRunning()).isFalse();
            }
        }
    }

    @Nested
    class Logging {

        @Test
        void monitor_shouldCreateLogFile() throws Exception {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            try (UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir, 1)) {
                monitor.start();
                // Wait for at least one collection
                TimeUnit.MILLISECONDS.sleep(100);
                monitor.stop();
            }

            Path logFile = logsDir.resolve(UsageLogger.USAGE_LOG_FILENAME);
            assertThat(Files.exists(logFile)).isTrue();
        }

        @Test
        void monitor_shouldWriteRecords() throws Exception {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            try (UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir, 1)) {
                monitor.start();
                // Wait for initial collection plus one interval
                TimeUnit.SECONDS.sleep(2);
                monitor.stop();
            }

            Path logFile = logsDir.resolve(UsageLogger.USAGE_LOG_FILENAME);
            List<String> lines = Files.readAllLines(logFile);
            // Should have header + at least 2 records (initial + one interval)
            assertThat(lines.size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void getLogFile_shouldReturnCorrectPath() throws IOException {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            try (UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir)) {
                Path logFile = monitor.getLogFile();

                assertThat(logFile).isEqualTo(logsDir.resolve(UsageLogger.USAGE_LOG_FILENAME));
            }
        }
    }

    @Nested
    class Close {

        @Test
        void close_shouldStopMonitor() throws IOException {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir, 1);
            monitor.start();
            assertThat(monitor.isRunning()).isTrue();

            monitor.close();

            assertThat(monitor.isRunning()).isFalse();
        }

        @Test
        void close_shouldBeIdempotent() throws IOException {
            long pid = ProcessHandle.current().pid();
            Path logsDir = tempDir.resolve("logs");

            UsageMonitor monitor = new UsageMonitor(pid, tempDir, logsDir);
            monitor.close();
            monitor.close(); // Should not throw
        }
    }
}
