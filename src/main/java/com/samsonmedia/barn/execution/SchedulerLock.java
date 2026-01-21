package com.samsonmedia.barn.execution;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock to ensure only one scheduler runs at a time.
 *
 * <p>Uses file-based locking to prevent multiple scheduler instances.
 * The lock is held for the lifetime of this object and released when closed.
 */
public final class SchedulerLock implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerLock.class);

    private final Path lockFile;
    private final FileChannel channel;
    private final java.nio.channels.FileLock lock;

    private SchedulerLock(Path lockFile, FileChannel channel, java.nio.channels.FileLock lock) {
        this.lockFile = lockFile;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Attempts to acquire the scheduler lock.
     *
     * @param lockFile the lock file path
     * @return the lock if acquired, or empty if already held
     * @throws IOException if lock acquisition fails for reasons other than contention
     */
    public static Optional<SchedulerLock> tryAcquire(Path lockFile) throws IOException {
        Objects.requireNonNull(lockFile, "lockFile must not be null");

        // Ensure parent directory exists
        Path parent = lockFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        FileChannel channel = FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
        );

        try {
            java.nio.channels.FileLock lock = channel.tryLock();

            if (lock == null) {
                // Lock is held by another process
                channel.close();
                LOG.debug("Scheduler lock already held: {}", lockFile);
                return Optional.empty();
            }

            LOG.info("Acquired scheduler lock: {}", lockFile);
            return Optional.of(new SchedulerLock(lockFile, channel, lock));

        } catch (OverlappingFileLockException e) {
            // Lock is held by another thread in the same JVM
            channel.close();
            LOG.debug("Scheduler lock held by same JVM: {}", lockFile);
            return Optional.empty();
        } catch (IOException e) {
            channel.close();
            throw e;
        }
    }

    /**
     * Returns the lock file path.
     *
     * @return the lock file path
     */
    public Path getLockFile() {
        return lockFile;
    }

    /**
     * Checks if the lock is still valid.
     *
     * @return true if the lock is valid
     */
    public boolean isValid() {
        return lock != null && lock.isValid();
    }

    /**
     * Releases the lock.
     */
    @Override
    public void close() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException e) {
            LOG.warn("Error releasing scheduler lock: {}", e.getMessage());
        }

        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            LOG.warn("Error closing scheduler lock channel: {}", e.getMessage());
        }

        LOG.info("Released scheduler lock: {}", lockFile);
    }
}
