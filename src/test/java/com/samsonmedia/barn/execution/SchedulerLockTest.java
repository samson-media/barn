package com.samsonmedia.barn.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for SchedulerLock.
 */
class SchedulerLockTest {

    @TempDir
    private Path tempDir;

    @Nested
    class TryAcquire {

        @Test
        void tryAcquire_withNoExistingLock_shouldSucceed() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            Optional<SchedulerLock> lock = SchedulerLock.tryAcquire(lockFile);

            assertThat(lock).isPresent();
            assertThat(lock.get().isValid()).isTrue();
            lock.get().close();
        }

        @Test
        void tryAcquire_shouldCreateLockFile() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            try (SchedulerLock lock = SchedulerLock.tryAcquire(lockFile).orElseThrow()) {
                assertThat(lock).isNotNull();
                assertThat(Files.exists(lockFile)).isTrue();
            }
        }

        @Test
        void tryAcquire_shouldCreateParentDirectories() throws IOException {
            Path lockFile = tempDir.resolve("locks/nested/scheduler.lock");

            try (SchedulerLock lock = SchedulerLock.tryAcquire(lockFile).orElseThrow()) {
                assertThat(lock).isNotNull();
                assertThat(Files.exists(lockFile.getParent())).isTrue();
            }
        }

        @Test
        void tryAcquire_withExistingLock_shouldReturnEmpty() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            try (SchedulerLock firstLock = SchedulerLock.tryAcquire(lockFile).orElseThrow()) {
                assertThat(firstLock).isNotNull();
                Optional<SchedulerLock> secondLock = SchedulerLock.tryAcquire(lockFile);

                assertThat(secondLock).isEmpty();
            }
        }

        @Test
        void tryAcquire_afterRelease_shouldSucceed() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            // First acquire and release
            SchedulerLock firstLock = SchedulerLock.tryAcquire(lockFile).orElseThrow();
            firstLock.close();

            // Should be able to acquire again
            Optional<SchedulerLock> secondLock = SchedulerLock.tryAcquire(lockFile);

            assertThat(secondLock).isPresent();
            secondLock.get().close();
        }

        @Test
        void tryAcquire_withNullLockFile_shouldThrowException() {
            assertThatThrownBy(() -> SchedulerLock.tryAcquire(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class GetLockFile {

        @Test
        void getLockFile_shouldReturnLockFilePath() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            try (SchedulerLock lock = SchedulerLock.tryAcquire(lockFile).orElseThrow()) {
                assertThat(lock.getLockFile()).isEqualTo(lockFile);
            }
        }
    }

    @Nested
    class IsValid {

        @Test
        void isValid_withActiveLock_shouldReturnTrue() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            try (SchedulerLock lock = SchedulerLock.tryAcquire(lockFile).orElseThrow()) {
                assertThat(lock.isValid()).isTrue();
            }
        }

        @Test
        void isValid_afterClose_shouldReturnFalse() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            SchedulerLock lock = SchedulerLock.tryAcquire(lockFile).orElseThrow();
            lock.close();

            assertThat(lock.isValid()).isFalse();
        }
    }

    @Nested
    class Close {

        @Test
        void close_shouldReleaseLock() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            SchedulerLock lock = SchedulerLock.tryAcquire(lockFile).orElseThrow();
            lock.close();

            // Should be able to acquire again
            Optional<SchedulerLock> newLock = SchedulerLock.tryAcquire(lockFile);
            assertThat(newLock).isPresent();
            newLock.get().close();
        }

        @Test
        void close_canBeCalledMultipleTimes() throws IOException {
            Path lockFile = tempDir.resolve("scheduler.lock");

            SchedulerLock lock = SchedulerLock.tryAcquire(lockFile).orElseThrow();
            lock.close();
            lock.close(); // Should not throw
            lock.close(); // Should not throw
        }
    }
}
