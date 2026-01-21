package com.samsonmedia.barn.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for AtomicFiles utility class.
 */
class AtomicFilesTest {

    @TempDir
    private Path tempDir;

    @Test
    void writeAtomically_withValidContent_shouldWriteFile() throws IOException {
        Path target = tempDir.resolve("test.txt");

        AtomicFiles.writeAtomically(target, "hello world");

        assertThat(Files.readString(target)).isEqualTo("hello world");
    }

    @Test
    void writeAtomically_withExistingFile_shouldOverwrite() throws IOException {
        Path target = tempDir.resolve("test.txt");
        Files.writeString(target, "old content");

        AtomicFiles.writeAtomically(target, "new content");

        assertThat(Files.readString(target)).isEqualTo("new content");
    }

    @Test
    void writeAtomically_shouldNotLeaveTempFile() throws IOException {
        Path target = tempDir.resolve("test.txt");

        AtomicFiles.writeAtomically(target, "content");

        assertThat(Files.exists(tempDir.resolve("test.txt.tmp"))).isFalse();
    }

    @Test
    void writeAtomically_withNullTarget_shouldThrowException() {
        assertThatThrownBy(() -> AtomicFiles.writeAtomically(null, "content"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void writeAtomically_withNullContent_shouldThrowException() {
        Path target = tempDir.resolve("test.txt");
        assertThatThrownBy(() -> AtomicFiles.writeAtomically(target, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void readSafely_withExistingFile_shouldReturnContent() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "  hello  ");

        Optional<String> result = AtomicFiles.readSafely(file);

        assertThat(result).hasValue("hello");
    }

    @Test
    void readSafely_withNonexistentFile_shouldReturnEmpty() throws IOException {
        Path file = tempDir.resolve("nonexistent.txt");

        Optional<String> result = AtomicFiles.readSafely(file);

        assertThat(result).isEmpty();
    }

    @Test
    void readSafely_withNullPath_shouldThrowException() {
        assertThatThrownBy(() -> AtomicFiles.readSafely(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void readOrEmpty_withExistingFile_shouldReturnContent() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "content");

        String result = AtomicFiles.readOrEmpty(file);

        assertThat(result).isEqualTo("content");
    }

    @Test
    void readOrEmpty_withNonexistentFile_shouldReturnEmptyString() throws IOException {
        Path file = tempDir.resolve("nonexistent.txt");

        String result = AtomicFiles.readOrEmpty(file);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteIfExists_withExistingFile_shouldDeleteAndReturnTrue() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "content");

        boolean result = AtomicFiles.deleteIfExists(file);

        assertThat(result).isTrue();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void deleteIfExists_withNonexistentFile_shouldReturnFalse() throws IOException {
        Path file = tempDir.resolve("nonexistent.txt");

        boolean result = AtomicFiles.deleteIfExists(file);

        assertThat(result).isFalse();
    }

    @Test
    void ensureParentDirectories_withNestedPath_shouldCreateDirectories() throws IOException {
        Path nested = tempDir.resolve("a/b/c/file.txt");

        AtomicFiles.ensureParentDirectories(nested);

        assertThat(Files.exists(tempDir.resolve("a/b/c"))).isTrue();
    }

    @Test
    void ensureParentDirectories_withExistingParent_shouldNotFail() throws IOException {
        Path file = tempDir.resolve("file.txt");

        AtomicFiles.ensureParentDirectories(file);

        // No exception means success
    }
}
