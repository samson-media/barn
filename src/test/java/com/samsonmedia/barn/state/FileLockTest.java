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
 * Tests for FileLock.
 */
class FileLockTest {

    @TempDir
    private Path tempDir;

    @Test
    void tryAcquire_shouldAcquireLock() throws IOException {
        Path lockFile = tempDir.resolve("test.lock");

        Optional<FileLock> lock = FileLock.tryAcquire(lockFile);

        assertThat(lock).isPresent();
        lock.get().close();
    }

    @Test
    void tryAcquire_shouldCreateLockFile() throws IOException {
        Path lockFile = tempDir.resolve("test.lock");

        try (FileLock lock = FileLock.tryAcquire(lockFile).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(Files.exists(lockFile)).isTrue();
        }
    }

    @Test
    void tryAcquire_withNestedPath_shouldCreateParentDirs() throws IOException {
        Path lockFile = tempDir.resolve("a/b/c/test.lock");

        try (FileLock lock = FileLock.tryAcquire(lockFile).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(Files.exists(lockFile)).isTrue();
        }
    }

    @Test
    void tryAcquire_whenAlreadyLocked_shouldReturnEmpty() throws IOException {
        Path lockFile = tempDir.resolve("test.lock");

        try (FileLock lock1 = FileLock.tryAcquire(lockFile).orElseThrow()) {
            assertThat(lock1).isNotNull();
            Optional<FileLock> lock2 = FileLock.tryAcquire(lockFile);

            assertThat(lock2).isEmpty();
        }
    }

    @Test
    void close_shouldReleaseLock() throws IOException {
        Path lockFile = tempDir.resolve("test.lock");

        FileLock lock1 = FileLock.tryAcquire(lockFile).orElseThrow();
        lock1.close();

        // Should be able to acquire lock again
        Optional<FileLock> lock2 = FileLock.tryAcquire(lockFile);
        assertThat(lock2).isPresent();
        lock2.get().close();
    }

    @Test
    void close_shouldDeleteLockFile() throws IOException {
        Path lockFile = tempDir.resolve("test.lock");

        FileLock lock = FileLock.tryAcquire(lockFile).orElseThrow();
        lock.close();

        assertThat(Files.exists(lockFile)).isFalse();
    }

    @Test
    void getLockFile_shouldReturnLockFilePath() throws IOException {
        Path lockFile = tempDir.resolve("test.lock");

        try (FileLock lock = FileLock.tryAcquire(lockFile).orElseThrow()) {
            assertThat(lock.getLockFile()).isEqualTo(lockFile);
        }
    }

    @Test
    void isLocked_withLockedFile_shouldReturnTrue() throws IOException {
        Path lockFile = tempDir.resolve("test.lock");

        try (FileLock lock = FileLock.tryAcquire(lockFile).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(FileLock.isLocked(lockFile)).isTrue();
        }
    }

    @Test
    void isLocked_withUnlockedFile_shouldReturnFalse() throws IOException {
        Path lockFile = tempDir.resolve("test.lock");

        // Acquire and release
        FileLock lock = FileLock.tryAcquire(lockFile).orElseThrow();
        lock.close();

        assertThat(FileLock.isLocked(lockFile)).isFalse();
    }

    @Test
    void isLocked_withNonexistentFile_shouldReturnFalse() {
        Path lockFile = tempDir.resolve("nonexistent.lock");

        assertThat(FileLock.isLocked(lockFile)).isFalse();
    }

    @Test
    void tryAcquire_withNullPath_shouldThrowException() {
        assertThatThrownBy(() -> FileLock.tryAcquire(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isLocked_withNullPath_shouldThrowException() {
        assertThatThrownBy(() -> FileLock.isLocked(null))
            .isInstanceOf(NullPointerException.class);
    }
}
