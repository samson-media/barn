package com.samsonmedia.barn.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Matches commands against load level whitelist entries.
 *
 * <p>Commands can be matched by:
 * <ul>
 *   <li>Executable name only (e.g., "ffmpeg")</li>
 *   <li>Full path to executable (e.g., "/usr/local/bin/ffmpeg")</li>
 *   <li>Directory containing executables (e.g., "/opt/encoders/")</li>
 * </ul>
 */
public sealed interface CommandMatcher {

    /**
     * Checks if this matcher matches the given command.
     *
     * @param command the command and arguments (first element is the executable)
     * @return true if this matcher matches the command
     */
    boolean matches(List<String> command);

    /**
     * Matches commands by executable name only.
     *
     * <p>The executable name is extracted from the command path and compared.
     * For example, "ffmpeg" matches both "ffmpeg" and "/usr/bin/ffmpeg".
     *
     * @param name the executable name to match (without path)
     */
    record ExecutableName(String name) implements CommandMatcher {

        /**
         * Creates an ExecutableName matcher with validation.
         */
        public ExecutableName {
            Objects.requireNonNull(name, "name must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }

        @Override
        public boolean matches(List<String> command) {
            if (command.isEmpty()) {
                return false;
            }
            String executable = command.getFirst();
            Path fileName = Path.of(executable).getFileName();
            if (fileName == null) {
                return false;
            }
            return name.equals(fileName.toString());
        }
    }

    /**
     * Matches commands by exact full path.
     *
     * <p>The command must exactly match the specified path.
     *
     * @param path the full path to match
     */
    record FullPath(Path path) implements CommandMatcher {

        /**
         * Creates a FullPath matcher with validation.
         */
        public FullPath {
            Objects.requireNonNull(path, "path must not be null");
        }

        @Override
        public boolean matches(List<String> command) {
            if (command.isEmpty()) {
                return false;
            }
            return Path.of(command.getFirst()).equals(path);
        }
    }

    /**
     * Matches commands whose executables are in the specified directory.
     *
     * <p>Subdirectories are not matched.
     *
     * @param directory the directory containing executables
     */
    record Directory(Path directory) implements CommandMatcher {

        /**
         * Creates a Directory matcher with validation.
         */
        public Directory {
            Objects.requireNonNull(directory, "directory must not be null");
        }

        @Override
        public boolean matches(List<String> command) {
            if (command.isEmpty()) {
                return false;
            }
            Path commandPath = Path.of(command.getFirst());
            Path parent = commandPath.getParent();
            if (parent == null) {
                return false;
            }
            // Normalize both paths for comparison
            return parent.normalize().equals(directory.normalize());
        }
    }
}
