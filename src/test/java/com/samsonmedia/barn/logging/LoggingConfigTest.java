package com.samsonmedia.barn.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.LogLevel;

/**
 * Tests for LoggingConfig.
 */
class LoggingConfigTest {

    private LogLevel originalLevel;

    @BeforeEach
    void setUp() {
        originalLevel = LoggingConfig.getLogLevel();
    }

    @AfterEach
    void tearDown() {
        LoggingConfig.setLogLevel(originalLevel);
    }

    @Nested
    class SetLogLevel {

        @Test
        void setLogLevel_withDebug_shouldChangeLevel() {
            LoggingConfig.setLogLevel(LogLevel.DEBUG);

            assertThat(LoggingConfig.getLogLevel()).isEqualTo(LogLevel.DEBUG);
        }

        @Test
        void setLogLevel_withInfo_shouldChangeLevel() {
            LoggingConfig.setLogLevel(LogLevel.INFO);

            assertThat(LoggingConfig.getLogLevel()).isEqualTo(LogLevel.INFO);
        }

        @Test
        void setLogLevel_withWarn_shouldChangeLevel() {
            LoggingConfig.setLogLevel(LogLevel.WARN);

            assertThat(LoggingConfig.getLogLevel()).isEqualTo(LogLevel.WARN);
        }

        @Test
        void setLogLevel_withError_shouldChangeLevel() {
            LoggingConfig.setLogLevel(LogLevel.ERROR);

            assertThat(LoggingConfig.getLogLevel()).isEqualTo(LogLevel.ERROR);
        }

        @Test
        void setLogLevel_withNull_shouldThrowException() {
            assertThatThrownBy(() -> LoggingConfig.setLogLevel(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("level");
        }
    }

    @Nested
    class GetLogLevel {

        @Test
        void getLogLevel_shouldReturnCurrentLevel() {
            LoggingConfig.setLogLevel(LogLevel.WARN);

            LogLevel level = LoggingConfig.getLogLevel();

            assertThat(level).isEqualTo(LogLevel.WARN);
        }
    }

    @Nested
    class EnsureLogDirectory {

        @TempDir
        private Path tempDir;

        @Test
        void ensureLogDirectory_withNonExistentDirectory_shouldCreateIt() throws IOException {
            Path logDir = tempDir.resolve("logs");

            LoggingConfig.ensureLogDirectory(logDir);

            assertThat(Files.exists(logDir)).isTrue();
            assertThat(Files.isDirectory(logDir)).isTrue();
        }

        @Test
        void ensureLogDirectory_withExistingDirectory_shouldNotFail() throws IOException {
            Path logDir = tempDir.resolve("logs");
            Files.createDirectories(logDir);

            LoggingConfig.ensureLogDirectory(logDir);

            assertThat(Files.exists(logDir)).isTrue();
        }

        @Test
        void ensureLogDirectory_withNestedPath_shouldCreateAllDirectories() throws IOException {
            Path logDir = tempDir.resolve("a/b/c/logs");

            LoggingConfig.ensureLogDirectory(logDir);

            assertThat(Files.exists(logDir)).isTrue();
        }

        @Test
        void ensureLogDirectory_withNull_shouldThrowException() {
            assertThatThrownBy(() -> LoggingConfig.ensureLogDirectory(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("logDir");
        }
    }

    @Nested
    class GetDefaultLogDirectory {

        @Test
        void getDefaultLogDirectory_shouldReturnPath() {
            Path logDir = LoggingConfig.getDefaultLogDirectory();

            assertThat(logDir).isNotNull();
            assertThat(logDir.toString()).contains("barn");
            assertThat(logDir.toString()).contains("logs");
        }

        @Test
        void getDefaultLogDirectory_withSystemProperty_shouldUseIt() {
            String original = System.getProperty("BARN_LOG_DIR");
            try {
                System.setProperty("BARN_LOG_DIR", "/custom/log/dir");

                Path logDir = LoggingConfig.getDefaultLogDirectory();

                assertThat(logDir.toString()).isEqualTo("/custom/log/dir");
            } finally {
                if (original != null) {
                    System.setProperty("BARN_LOG_DIR", original);
                } else {
                    System.clearProperty("BARN_LOG_DIR");
                }
            }
        }
    }
}
