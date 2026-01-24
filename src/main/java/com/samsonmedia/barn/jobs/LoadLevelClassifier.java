package com.samsonmedia.barn.jobs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.samsonmedia.barn.config.CommandMatcher;
import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.config.LoadFileParser;

/**
 * Classifies commands into load levels based on whitelist files.
 *
 * <p>The classifier loads whitelist files from the config directory:
 * <ul>
 *   <li>{@code high.load} - Commands that are CPU/GPU intensive (e.g., transcoding)</li>
 *   <li>{@code medium.load} - General purpose commands</li>
 *   <li>{@code low.load} - Commands that are network/IO intensive (e.g., downloads)</li>
 * </ul>
 *
 * <p>Commands are matched against each list in priority order: HIGH, MEDIUM, LOW.
 * If a command is not matched by any list, it defaults to MEDIUM.
 */
public final class LoadLevelClassifier {

    /** File name for high load whitelist. */
    public static final String HIGH_LOAD_FILE = "high.load";

    /** File name for medium load whitelist. */
    public static final String MEDIUM_LOAD_FILE = "medium.load";

    /** File name for low load whitelist. */
    public static final String LOW_LOAD_FILE = "low.load";

    private final List<CommandMatcher> highMatchers;
    private final List<CommandMatcher> mediumMatchers;
    private final List<CommandMatcher> lowMatchers;

    /**
     * Creates a LoadLevelClassifier with the given matchers.
     *
     * @param highMatchers matchers for HIGH load level
     * @param mediumMatchers matchers for MEDIUM load level
     * @param lowMatchers matchers for LOW load level
     */
    public LoadLevelClassifier(
            List<CommandMatcher> highMatchers,
            List<CommandMatcher> mediumMatchers,
            List<CommandMatcher> lowMatchers) {
        this.highMatchers = List.copyOf(Objects.requireNonNull(highMatchers, "highMatchers"));
        this.mediumMatchers = List.copyOf(Objects.requireNonNull(mediumMatchers, "mediumMatchers"));
        this.lowMatchers = List.copyOf(Objects.requireNonNull(lowMatchers, "lowMatchers"));
    }

    /**
     * Loads a classifier from whitelist files in the specified directory.
     *
     * <p>If a whitelist file doesn't exist, an empty list is used for that level.
     *
     * @param configDir the directory containing whitelist files
     * @return a new classifier
     */
    public static LoadLevelClassifier load(Path configDir) {
        Objects.requireNonNull(configDir, "configDir must not be null");

        List<CommandMatcher> high = LoadFileParser.parseIfExists(configDir.resolve(HIGH_LOAD_FILE));
        List<CommandMatcher> medium = LoadFileParser.parseIfExists(configDir.resolve(MEDIUM_LOAD_FILE));
        List<CommandMatcher> low = LoadFileParser.parseIfExists(configDir.resolve(LOW_LOAD_FILE));

        return new LoadLevelClassifier(high, medium, low);
    }

    /**
     * Loads a classifier from the system config directory.
     *
     * <p>Searches for whitelist files in the system config directory
     * (e.g., /etc/barn/ on Linux/macOS, %PROGRAMDATA%\barn\ on Windows).
     *
     * <p>If no config directory is found, returns a classifier with empty whitelists
     * (all commands default to MEDIUM).
     *
     * @return a new classifier
     */
    public static LoadLevelClassifier loadFromConfigDir() {
        Path systemConfig = ConfigDefaults.getSystemConfigDir();
        if (Files.isDirectory(systemConfig) && hasAnyLoadFile(systemConfig)) {
            return load(systemConfig);
        }

        // Return empty classifier (all commands default to MEDIUM)
        return new LoadLevelClassifier(List.of(), List.of(), List.of());
    }

    private static boolean hasAnyLoadFile(Path dir) {
        return Files.exists(dir.resolve(HIGH_LOAD_FILE))
            || Files.exists(dir.resolve(MEDIUM_LOAD_FILE))
            || Files.exists(dir.resolve(LOW_LOAD_FILE));
    }

    /**
     * Classifies a command into a load level.
     *
     * <p>Commands are checked against each whitelist in priority order:
     * HIGH, MEDIUM, LOW. If a command matches multiple lists, the highest
     * priority level wins. If no match is found, MEDIUM is returned.
     *
     * @param command the command and arguments (first element is the executable)
     * @return the load level for this command
     */
    public LoadLevel classify(List<String> command) {
        Objects.requireNonNull(command, "command must not be null");

        if (command.isEmpty()) {
            return LoadLevel.getDefault();
        }

        // Check HIGH first (highest priority)
        for (CommandMatcher matcher : highMatchers) {
            if (matcher.matches(command)) {
                return LoadLevel.HIGH;
            }
        }

        // Check MEDIUM second
        for (CommandMatcher matcher : mediumMatchers) {
            if (matcher.matches(command)) {
                return LoadLevel.MEDIUM;
            }
        }

        // Check LOW third
        for (CommandMatcher matcher : lowMatchers) {
            if (matcher.matches(command)) {
                return LoadLevel.LOW;
            }
        }

        // Default to MEDIUM
        return LoadLevel.getDefault();
    }
}
