package com.samsonmedia.barn.state;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;

import com.samsonmedia.barn.logging.BarnLogger;

/**
 * A file-based lock for coordinating access to resources.
 *
 * <p>Uses {@link FileChannel#tryLock()} for cross-process locking.
 * The lock is released when the FileLock is closed.
 */
public final class FileLock implements AutoCloseable {

    private static final BarnLogger LOG = BarnLogger.getLogger(FileLock.class);

    private final Path lockFile;
    private final FileChannel channel;
    private final java.nio.channels.FileLock lock;

    private FileLock(Path lockFile, FileChannel channel, java.nio.channels.FileLock lock) {
        this.lockFile = lockFile;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Attempts to acquire a lock on the specified file.
     *
     * <p>This is a non-blocking operation. If the lock cannot be acquired
     * (because another process holds it), this method returns an empty Optional.
     *
     * @param lockFile the path to the lock file
     * @return the acquired lock, or empty if the lock is already held
     * @throws IOException if lock file creation or acquisition fails
     */
    public static Optional<FileLock> tryAcquire(Path lockFile) throws IOException {
        Objects.requireNonNull(lockFile, "lockFile must not be null");

        // Ensure parent directory exists
        Path parent = lockFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);

            java.nio.channels.FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                // Lock is held by another thread in this JVM
                channel.close();
                LOG.debug("Could not acquire lock (held by same JVM): {}", lockFile);
                return Optional.empty();
            }
            if (lock == null) {
                // Lock is held by another process
                channel.close();
                LOG.debug("Could not acquire lock: {}", lockFile);
                return Optional.empty();
            }

            LOG.debug("Acquired lock: {}", lockFile);
            return Optional.of(new FileLock(lockFile, channel, lock));

        } catch (IOException e) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // Best effort cleanup
                }
            }
            throw e;
        }
    }

    /**
     * Checks if a lock file is currently locked by another process.
     *
     * <p>This method tries to acquire the lock temporarily. If it succeeds,
     * the lock is immediately released, indicating no one else holds it.
     *
     * @param lockFile the path to the lock file
     * @return true if the lock is held by another process, false otherwise
     */
    public static boolean isLocked(Path lockFile) {
        Objects.requireNonNull(lockFile, "lockFile must not be null");

        if (!Files.exists(lockFile)) {
            return false;
        }

        try (FileLock lock = tryAcquire(lockFile).orElse(null)) {
            if (lock == null) {
                return true; // Couldn't acquire, so it's locked
            }
            return false; // Acquired and released, so it wasn't locked
        } catch (IOException e) {
            LOG.debug("Error checking lock status: {}", e.getMessage());
            return false; // Assume not locked on error
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
     * Releases the lock and closes the file channel.
     *
     * <p>The lock file is deleted after the lock is released.
     */
    @Override
    public void close() throws IOException {
        try {
            if (lock.isValid()) {
                lock.release();
                LOG.debug("Released lock: {}", lockFile);
            }
        } finally {
            channel.close();
            // Try to delete the lock file, but don't fail if it doesn't work
            try {
                Files.deleteIfExists(lockFile);
            } catch (IOException e) {
                LOG.debug("Could not delete lock file: {}", lockFile);
            }
        }
    }
}
