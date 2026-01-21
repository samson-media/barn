package com.samsonmedia.barn.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for UsageLogger.
 */
class UsageLoggerTest {

    @TempDir
    private Path tempDir;

    @Nested
    class Constructor {

        @Test
        void constructor_withValidPath_shouldCreateLogger() {
            Path logFile = tempDir.resolve("usage.csv");

            UsageLogger logger = new UsageLogger(logFile);

            assertThat(logger.getLogFile()).isEqualTo(logFile);
        }

        @Test
        void constructor_withNullPath_shouldThrowException() {
            assertThatThrownBy(() -> new UsageLogger(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("logFile");
        }
    }

    @Nested
    class ForJobLogsDir {

        @Test
        void forJobLogsDir_shouldCreateLoggerWithCorrectPath() {
            Path logsDir = tempDir.resolve("logs");

            UsageLogger logger = UsageLogger.forJobLogsDir(logsDir);

            assertThat(logger.getLogFile()).isEqualTo(logsDir.resolve(UsageLogger.USAGE_LOG_FILENAME));
        }
    }

    @Nested
    class Log {

        @Test
        void log_singleRecord_shouldWriteHeaderAndData() throws IOException {
            Path logFile = tempDir.resolve("usage.csv");
            UsageRecord record = UsageRecord.withoutGpu(Instant.parse("2024-01-15T10:30:00Z"), 50.5, 1024, 2048);

            try (UsageLogger logger = new UsageLogger(logFile)) {
                logger.log(record);
            }

            List<String> lines = Files.readAllLines(logFile);
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0)).isEqualTo(UsageRecord.csvHeader());
            assertThat(lines.get(1)).contains("2024-01-15T10:30:00Z");
        }

        @Test
        void log_multipleRecords_shouldAppendData() throws IOException {
            Path logFile = tempDir.resolve("usage.csv");
            UsageRecord record1 = UsageRecord.withoutGpu(Instant.parse("2024-01-15T10:30:00Z"), 50.5, 1024, 2048);
            UsageRecord record2 = UsageRecord.withoutGpu(Instant.parse("2024-01-15T10:30:05Z"), 60.0, 2048, 4096);

            try (UsageLogger logger = new UsageLogger(logFile)) {
                logger.log(record1);
                logger.log(record2);
            }

            List<String> lines = Files.readAllLines(logFile);
            assertThat(lines).hasSize(3);
            assertThat(lines.get(0)).isEqualTo(UsageRecord.csvHeader());
            assertThat(lines.get(1)).contains("10:30:00");
            assertThat(lines.get(2)).contains("10:30:05");
        }

        @Test
        void log_withNullRecord_shouldThrowException() throws IOException {
            Path logFile = tempDir.resolve("usage.csv");

            try (UsageLogger logger = new UsageLogger(logFile)) {
                assertThatThrownBy(() -> logger.log(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("record");
            }
        }

        @Test
        void log_shouldCreateParentDirectories() throws IOException {
            Path logFile = tempDir.resolve("nested/dir/usage.csv");
            UsageRecord record = UsageRecord.withoutGpu(Instant.now(), 50.0, 1024, 2048);

            try (UsageLogger logger = new UsageLogger(logFile)) {
                logger.log(record);
            }

            assertThat(Files.exists(logFile)).isTrue();
        }

        @Test
        void log_toExistingFile_shouldNotDuplicateHeader() throws IOException {
            Path logFile = tempDir.resolve("usage.csv");

            // First logger writes header and record
            try (UsageLogger logger = new UsageLogger(logFile)) {
                logger.log(UsageRecord.withoutGpu(Instant.now(), 50.0, 1024, 2048));
            }

            // Second logger should append without header
            try (UsageLogger logger = new UsageLogger(logFile)) {
                logger.log(UsageRecord.withoutGpu(Instant.now(), 60.0, 2048, 4096));
            }

            List<String> lines = Files.readAllLines(logFile);
            long headerCount = lines.stream()
                .filter(line -> line.startsWith("timestamp,"))
                .count();
            assertThat(headerCount).isEqualTo(1);
        }
    }

    @Nested
    class Flush {

        @Test
        void flush_afterLog_shouldPersistData() throws IOException {
            Path logFile = tempDir.resolve("usage.csv");

            try (UsageLogger logger = new UsageLogger(logFile)) {
                logger.log(UsageRecord.withoutGpu(Instant.now(), 50.0, 1024, 2048));
                logger.flush();

                assertThat(Files.exists(logFile)).isTrue();
                assertThat(Files.size(logFile)).isGreaterThan(0);
            }
        }

        @Test
        void flush_beforeAnyLog_shouldNotFail() throws IOException {
            Path logFile = tempDir.resolve("usage.csv");

            try (UsageLogger logger = new UsageLogger(logFile)) {
                logger.flush(); // Should not throw
            }
        }
    }

    @Nested
    class Close {

        @Test
        void close_shouldAllowMultipleCalls() throws IOException {
            Path logFile = tempDir.resolve("usage.csv");
            UsageLogger logger = new UsageLogger(logFile);
            logger.log(UsageRecord.withoutGpu(Instant.now(), 50.0, 1024, 2048));

            logger.close();
            logger.close(); // Should not throw
        }
    }
}
