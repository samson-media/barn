package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for LoadFileParser.
 */
class LoadFileParserTest {

    @TempDir
    private Path tempDir;

    @Test
    void parse_withEmptyFile_shouldReturnEmptyList() throws IOException {
        Path loadFile = tempDir.resolve("empty.load");
        Files.writeString(loadFile, "");

        List<CommandMatcher> matchers = LoadFileParser.parse(loadFile);

        assertThat(matchers).isEmpty();
    }

    @Test
    void parse_withOnlyComments_shouldReturnEmptyList() throws IOException {
        Path loadFile = tempDir.resolve("comments.load");
        Files.writeString(loadFile, """
            # This is a comment
            # Another comment
            """);

        List<CommandMatcher> matchers = LoadFileParser.parse(loadFile);

        assertThat(matchers).isEmpty();
    }

    @Test
    void parse_withOnlyBlankLines_shouldReturnEmptyList() throws IOException {
        Path loadFile = tempDir.resolve("blank.load");
        Files.writeString(loadFile, """

            \s\s
            \t
            """);

        List<CommandMatcher> matchers = LoadFileParser.parse(loadFile);

        assertThat(matchers).isEmpty();
    }

    @Test
    void parse_withExecutableNames_shouldReturnExecutableNameMatchers() throws IOException {
        Path loadFile = tempDir.resolve("executables.load");
        Files.writeString(loadFile, """
            ffmpeg
            ffprobe
            handbrake
            """);

        List<CommandMatcher> matchers = LoadFileParser.parse(loadFile);

        assertThat(matchers).hasSize(3);
        assertThat(matchers.get(0)).isInstanceOf(CommandMatcher.ExecutableName.class);
        assertThat(matchers.get(1)).isInstanceOf(CommandMatcher.ExecutableName.class);
        assertThat(matchers.get(2)).isInstanceOf(CommandMatcher.ExecutableName.class);
    }

    @Test
    void parse_withFullPaths_shouldReturnFullPathMatchers() throws IOException {
        Path loadFile = tempDir.resolve("paths.load");
        Files.writeString(loadFile, """
            /usr/local/bin/ffmpeg
            /opt/media/encoders/x264
            """);

        List<CommandMatcher> matchers = LoadFileParser.parse(loadFile);

        assertThat(matchers).hasSize(2);
        assertThat(matchers.get(0)).isInstanceOf(CommandMatcher.FullPath.class);
        assertThat(matchers.get(1)).isInstanceOf(CommandMatcher.FullPath.class);
    }

    @Test
    void parse_withDirectoryPaths_shouldReturnDirectoryMatchers() throws IOException {
        Path loadFile = tempDir.resolve("dirs.load");
        Files.writeString(loadFile, """
            /opt/encoders/
            /usr/local/media/
            """);

        List<CommandMatcher> matchers = LoadFileParser.parse(loadFile);

        assertThat(matchers).hasSize(2);
        assertThat(matchers.get(0)).isInstanceOf(CommandMatcher.Directory.class);
        assertThat(matchers.get(1)).isInstanceOf(CommandMatcher.Directory.class);
    }

    @Test
    void parse_withMixedEntries_shouldReturnCorrectMatchers() throws IOException {
        Path loadFile = tempDir.resolve("mixed.load");
        Files.writeString(loadFile, """
            # High load commands
            ffmpeg
            /usr/local/bin/custom-encoder
            /opt/encoders/

            # More commands
            handbrake
            """);

        List<CommandMatcher> matchers = LoadFileParser.parse(loadFile);

        assertThat(matchers).hasSize(4);
        assertThat(matchers.get(0)).isInstanceOf(CommandMatcher.ExecutableName.class);
        assertThat(matchers.get(1)).isInstanceOf(CommandMatcher.FullPath.class);
        assertThat(matchers.get(2)).isInstanceOf(CommandMatcher.Directory.class);
        assertThat(matchers.get(3)).isInstanceOf(CommandMatcher.ExecutableName.class);
    }

    @Test
    void parse_withNonExistentFile_shouldThrowIOException() {
        Path nonExistent = tempDir.resolve("nonexistent.load");

        assertThatThrownBy(() -> LoadFileParser.parse(nonExistent))
            .isInstanceOf(IOException.class);
    }

    @Test
    void parse_withNullPath_shouldThrowException() {
        assertThatThrownBy(() -> LoadFileParser.parse(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parse_withWhitespaceAroundEntries_shouldTrimEntries() throws IOException {
        Path loadFile = tempDir.resolve("whitespace.load");
        Files.writeString(loadFile, """
              ffmpeg  \s
            \t  curl\t
            """);

        List<CommandMatcher> matchers = LoadFileParser.parse(loadFile);

        assertThat(matchers).hasSize(2);
        assertThat(matchers.get(0).matches(List.of("ffmpeg", "-version"))).isTrue();
        assertThat(matchers.get(1).matches(List.of("curl", "--help"))).isTrue();
    }

    @Test
    void parseIfExists_withExistingFile_shouldReturnMatchers() throws IOException {
        Path loadFile = tempDir.resolve("existing.load");
        Files.writeString(loadFile, "ffmpeg\n");

        List<CommandMatcher> matchers = LoadFileParser.parseIfExists(loadFile);

        assertThat(matchers).hasSize(1);
    }

    @Test
    void parseIfExists_withNonExistentFile_shouldReturnEmptyList() {
        Path nonExistent = tempDir.resolve("nonexistent.load");

        List<CommandMatcher> matchers = LoadFileParser.parseIfExists(nonExistent);

        assertThat(matchers).isEmpty();
    }
}
