package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for CommandMatcher sealed interface and implementations.
 */
class CommandMatcherTest {

    @Nested
    class ExecutableNameMatcherTests {

        @Test
        void matches_withMatchingExecutableName_shouldReturnTrue() {
            var matcher = new CommandMatcher.ExecutableName("ffmpeg");

            assertThat(matcher.matches(List.of("ffmpeg", "-i", "input.mp4"))).isTrue();
            assertThat(matcher.matches(List.of("/usr/bin/ffmpeg", "-i", "input.mp4"))).isTrue();
            assertThat(matcher.matches(List.of("/opt/local/bin/ffmpeg", "-version"))).isTrue();
        }

        @Test
        void matches_withNonMatchingExecutableName_shouldReturnFalse() {
            var matcher = new CommandMatcher.ExecutableName("ffmpeg");

            assertThat(matcher.matches(List.of("curl", "http://example.com"))).isFalse();
            assertThat(matcher.matches(List.of("/usr/bin/curl", "-O", "file"))).isFalse();
        }

        @Test
        void matches_withPartialMatch_shouldReturnFalse() {
            var matcher = new CommandMatcher.ExecutableName("ffmpeg");

            // ffmpegXXX should not match
            assertThat(matcher.matches(List.of("ffmpegXXX", "-i", "input.mp4"))).isFalse();
            // prefix should not match
            assertThat(matcher.matches(List.of("/usr/bin/ffmpegXXX", "-version"))).isFalse();
        }

        @Test
        void matches_withEmptyCommand_shouldReturnFalse() {
            var matcher = new CommandMatcher.ExecutableName("ffmpeg");

            assertThat(matcher.matches(List.of())).isFalse();
        }

        @Test
        void constructor_withNull_shouldThrowException() {
            assertThatThrownBy(() -> new CommandMatcher.ExecutableName(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withBlank_shouldThrowException() {
            assertThatThrownBy(() -> new CommandMatcher.ExecutableName("  "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class FullPathMatcherTests {

        @Test
        void matches_withExactPath_shouldReturnTrue() {
            var matcher = new CommandMatcher.FullPath(Path.of("/usr/local/bin/ffmpeg"));

            assertThat(matcher.matches(List.of("/usr/local/bin/ffmpeg", "-i", "input.mp4"))).isTrue();
        }

        @Test
        void matches_withDifferentPath_shouldReturnFalse() {
            var matcher = new CommandMatcher.FullPath(Path.of("/usr/local/bin/ffmpeg"));

            assertThat(matcher.matches(List.of("/usr/bin/ffmpeg", "-i", "input.mp4"))).isFalse();
            assertThat(matcher.matches(List.of("ffmpeg", "-i", "input.mp4"))).isFalse();
        }

        @Test
        void matches_withEmptyCommand_shouldReturnFalse() {
            var matcher = new CommandMatcher.FullPath(Path.of("/usr/local/bin/ffmpeg"));

            assertThat(matcher.matches(List.of())).isFalse();
        }

        @Test
        void constructor_withNull_shouldThrowException() {
            assertThatThrownBy(() -> new CommandMatcher.FullPath(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class DirectoryMatcherTests {

        @Test
        void matches_withCommandInDirectory_shouldReturnTrue() {
            var matcher = new CommandMatcher.Directory(Path.of("/opt/encoders/"));

            assertThat(matcher.matches(List.of("/opt/encoders/x264", "-i", "input.mp4"))).isTrue();
            assertThat(matcher.matches(List.of("/opt/encoders/x265", "-o", "output.mp4"))).isTrue();
            assertThat(matcher.matches(List.of("/opt/encoders/av1an", "-i", "input.mp4"))).isTrue();
        }

        @Test
        void matches_withCommandNotInDirectory_shouldReturnFalse() {
            var matcher = new CommandMatcher.Directory(Path.of("/opt/encoders/"));

            assertThat(matcher.matches(List.of("/usr/bin/ffmpeg", "-i", "input.mp4"))).isFalse();
            assertThat(matcher.matches(List.of("ffmpeg", "-i", "input.mp4"))).isFalse();
        }

        @Test
        void matches_withSubdirectory_shouldReturnFalse() {
            var matcher = new CommandMatcher.Directory(Path.of("/opt/encoders/"));

            // Subdirectories should not match
            assertThat(matcher.matches(List.of("/opt/encoders/subdir/ffmpeg", "-version"))).isFalse();
        }

        @Test
        void matches_withEmptyCommand_shouldReturnFalse() {
            var matcher = new CommandMatcher.Directory(Path.of("/opt/encoders/"));

            assertThat(matcher.matches(List.of())).isFalse();
        }

        @Test
        void constructor_withNull_shouldThrowException() {
            assertThatThrownBy(() -> new CommandMatcher.Directory(null))
                .isInstanceOf(NullPointerException.class);
        }
    }
}
