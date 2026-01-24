package com.samsonmedia.barn.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses load level whitelist files (.load files).
 *
 * <p>Load files use a gitignore-like format:
 * <ul>
 *   <li>Lines starting with # are comments</li>
 *   <li>Empty lines are ignored</li>
 *   <li>Plain names (e.g., "ffmpeg") match any executable with that name</li>
 *   <li>Full paths (e.g., "/usr/bin/ffmpeg") match that exact path</li>
 *   <li>Directories with trailing slash (e.g., "/opt/encoders/") match any executable in that directory</li>
 * </ul>
 */
public final class LoadFileParser {

    private LoadFileParser() {
        // Utility class
    }

    /**
     * Parses a load file into a list of command matchers.
     *
     * @param path the path to the load file
     * @return a list of matchers, in file order
     * @throws IOException if the file cannot be read
     */
    public static List<CommandMatcher> parse(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");

        List<String> lines = Files.readAllLines(path);
        List<CommandMatcher> matchers = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.strip();

            // Skip comments and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            matchers.add(createMatcher(trimmed));
        }

        return matchers;
    }

    /**
     * Parses a load file if it exists, otherwise returns an empty list.
     *
     * @param path the path to the load file
     * @return a list of matchers, or empty list if file doesn't exist
     */
    public static List<CommandMatcher> parseIfExists(Path path) {
        Objects.requireNonNull(path, "path must not be null");

        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            return parse(path);
        } catch (IOException e) {
            // Log and return empty list for robustness
            return List.of();
        }
    }

    private static CommandMatcher createMatcher(String entry) {
        // Directory matcher: ends with /
        if (entry.endsWith("/")) {
            return new CommandMatcher.Directory(Path.of(entry));
        }

        // Full path matcher: contains path separator
        if (entry.contains("/")) {
            return new CommandMatcher.FullPath(Path.of(entry));
        }

        // Executable name matcher: just a name
        return new CommandMatcher.ExecutableName(entry);
    }
}
