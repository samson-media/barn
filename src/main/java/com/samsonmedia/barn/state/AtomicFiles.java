package com.samsonmedia.barn.state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class for atomic file operations.
 *
 * <p>Atomic writes work by writing to a temporary file first, then atomically
 * renaming it to the target. This ensures that readers never see partial content.
 */
public final class AtomicFiles {

    private static final String TMP_SUFFIX = ".tmp";

    private AtomicFiles() {
        // Utility class - prevent instantiation
    }

    /**
     * Writes content to a file atomically.
     *
     * <p>The content is first written to a temporary file, then atomically
     * renamed to the target path. This ensures that the target file either
     * contains the complete content or doesn't exist.
     *
     * @param target the target file path
     * @param content the content to write
     * @throws IOException if writing fails
     */
    public static void writeAtomically(Path target, String content) throws IOException {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(content, "content must not be null");

        Path tempFile = target.resolveSibling(target.getFileName() + TMP_SUFFIX);

        try {
            Files.writeString(tempFile, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

            Files.move(tempFile, target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Clean up temp file on failure
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // Best effort cleanup
            }
            throw e;
        }
    }

    /**
     * Reads content from a file safely.
     *
     * <p>Returns an empty Optional if the file doesn't exist, rather than
     * throwing an exception. The content is trimmed of leading/trailing whitespace.
     *
     * @param path the file to read
     * @return the file content, or empty if the file doesn't exist
     * @throws IOException if reading fails for reasons other than file not found
     */
    public static Optional<String> readSafely(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");

        try {
            String content = Files.readString(path);
            return Optional.of(content.trim());
        } catch (NoSuchFileException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads content from a file, returning empty string if not found.
     *
     * @param path the file to read
     * @return the file content, or empty string if the file doesn't exist
     * @throws IOException if reading fails for reasons other than file not found
     */
    public static String readOrEmpty(Path path) throws IOException {
        return readSafely(path).orElse("");
    }

    /**
     * Deletes a file if it exists.
     *
     * @param path the file to delete
     * @return true if the file was deleted, false if it didn't exist
     * @throws IOException if deletion fails
     */
    public static boolean deleteIfExists(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        return Files.deleteIfExists(path);
    }

    /**
     * Creates parent directories for a file if they don't exist.
     *
     * @param path the file path
     * @throws IOException if directory creation fails
     */
    public static void ensureParentDirectories(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
