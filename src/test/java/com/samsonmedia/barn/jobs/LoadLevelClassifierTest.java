package com.samsonmedia.barn.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for LoadLevelClassifier.
 */
class LoadLevelClassifierTest {

    @TempDir
    private Path tempDir;

    @Nested
    class ClassificationTests {

        private LoadLevelClassifier classifier;

        @BeforeEach
        void setUp() throws IOException {
            // Create high.load with ffmpeg, x264
            Files.writeString(tempDir.resolve("high.load"), """
                # High load commands
                ffmpeg
                x264
                /usr/local/bin/handbrake
                """);

            // Create empty medium.load
            Files.writeString(tempDir.resolve("medium.load"), """
                # Medium load - empty by default
                """);

            // Create low.load with curl, wget
            Files.writeString(tempDir.resolve("low.load"), """
                # Low load commands
                curl
                wget
                rclone
                """);

            classifier = LoadLevelClassifier.load(tempDir);
        }

        @Test
        void classify_withHighLoadCommand_shouldReturnHigh() {
            assertThat(classifier.classify(List.of("ffmpeg", "-i", "input.mp4"))).isEqualTo(LoadLevel.HIGH);
            assertThat(classifier.classify(List.of("/usr/bin/ffmpeg", "-version"))).isEqualTo(LoadLevel.HIGH);
            assertThat(classifier.classify(List.of("x264", "--preset", "slow"))).isEqualTo(LoadLevel.HIGH);
            assertThat(classifier.classify(List.of("/usr/local/bin/handbrake", "-i", "input"))).isEqualTo(LoadLevel.HIGH);
        }

        @Test
        void classify_withLowLoadCommand_shouldReturnLow() {
            assertThat(classifier.classify(List.of("curl", "http://example.com"))).isEqualTo(LoadLevel.LOW);
            assertThat(classifier.classify(List.of("/usr/bin/wget", "-O", "file"))).isEqualTo(LoadLevel.LOW);
            assertThat(classifier.classify(List.of("rclone", "sync", "source:", "dest:"))).isEqualTo(LoadLevel.LOW);
        }

        @Test
        void classify_withUnknownCommand_shouldReturnMedium() {
            assertThat(classifier.classify(List.of("echo", "hello"))).isEqualTo(LoadLevel.MEDIUM);
            assertThat(classifier.classify(List.of("/bin/bash", "-c", "ls"))).isEqualTo(LoadLevel.MEDIUM);
            assertThat(classifier.classify(List.of("python3", "script.py"))).isEqualTo(LoadLevel.MEDIUM);
        }

        @Test
        void classify_withEmptyCommand_shouldReturnMedium() {
            assertThat(classifier.classify(List.of())).isEqualTo(LoadLevel.MEDIUM);
        }

        @Test
        void classify_withNull_shouldThrowException() {
            assertThatThrownBy(() -> classifier.classify(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class LoadingTests {

        @Test
        void load_withAllFilesPresent_shouldLoadAllMatchers() throws IOException {
            Files.writeString(tempDir.resolve("high.load"), "ffmpeg\n");
            Files.writeString(tempDir.resolve("medium.load"), "custom-tool\n");
            Files.writeString(tempDir.resolve("low.load"), "curl\n");

            LoadLevelClassifier classifier = LoadLevelClassifier.load(tempDir);

            assertThat(classifier.classify(List.of("ffmpeg"))).isEqualTo(LoadLevel.HIGH);
            assertThat(classifier.classify(List.of("custom-tool"))).isEqualTo(LoadLevel.MEDIUM);
            assertThat(classifier.classify(List.of("curl"))).isEqualTo(LoadLevel.LOW);
        }

        @Test
        void load_withMissingFiles_shouldTreatAsEmpty() throws IOException {
            // No load files exist

            LoadLevelClassifier classifier = LoadLevelClassifier.load(tempDir);

            // Everything should default to MEDIUM
            assertThat(classifier.classify(List.of("ffmpeg"))).isEqualTo(LoadLevel.MEDIUM);
            assertThat(classifier.classify(List.of("curl"))).isEqualTo(LoadLevel.MEDIUM);
        }

        @Test
        void load_withNullPath_shouldThrowException() {
            assertThatThrownBy(() -> LoadLevelClassifier.load(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class PriorityTests {

        @Test
        void classify_whenInMultipleLists_shouldPrioritizeHighOverLow() throws IOException {
            // Put ffmpeg in both high and low
            Files.writeString(tempDir.resolve("high.load"), "ffmpeg\n");
            Files.writeString(tempDir.resolve("low.load"), "ffmpeg\n");

            LoadLevelClassifier classifier = LoadLevelClassifier.load(tempDir);

            // HIGH should win
            assertThat(classifier.classify(List.of("ffmpeg"))).isEqualTo(LoadLevel.HIGH);
        }

        @Test
        void classify_whenInMediumAndLow_shouldPrioritizeMediumOverLow() throws IOException {
            Files.writeString(tempDir.resolve("medium.load"), "curl\n");
            Files.writeString(tempDir.resolve("low.load"), "curl\n");

            LoadLevelClassifier classifier = LoadLevelClassifier.load(tempDir);

            // MEDIUM should win over LOW
            assertThat(classifier.classify(List.of("curl"))).isEqualTo(LoadLevel.MEDIUM);
        }
    }
}
