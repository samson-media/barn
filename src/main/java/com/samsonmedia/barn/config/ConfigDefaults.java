package com.samsonmedia.barn.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides platform-specific default values for configuration.
 */
public final class ConfigDefaults {

    private ConfigDefaults() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the default base directory for Barn data.
     *
     * @return the default base directory
     */
    public static Path getDefaultBaseDir() {
        return switch (OperatingSystem.current()) {
            case WINDOWS -> Path.of(System.getenv("TEMP"), "barn");
            case LINUX, MACOS -> Path.of("/tmp/barn");
        };
    }

    /**
     * Gets the default IPC socket path.
     *
     * @return the default IPC socket path
     */
    public static Path getDefaultIpcSocket() {
        return switch (OperatingSystem.current()) {
            case WINDOWS -> Path.of("\\\\.\\pipe\\barn");
            case LINUX, MACOS -> Path.of("/tmp/barn/barn.sock");
        };
    }

    /**
     * Gets the system-wide configuration file path.
     *
     * @return the system config path
     */
    public static Path getSystemConfigPath() {
        return switch (OperatingSystem.current()) {
            case WINDOWS -> {
                String programData = System.getenv("PROGRAMDATA");
                if (programData == null || programData.isBlank()) {
                    programData = "C:\\ProgramData";
                }
                yield Path.of(programData, "barn", "barn.conf");
            }
            case LINUX, MACOS -> Path.of("/etc/barn/barn.conf");
        };
    }

    /**
     * Gets the user-specific configuration file path.
     *
     * @return the user config path
     */
    public static Path getUserConfigPath() {
        String userHome = System.getProperty("user.home", "");
        return switch (OperatingSystem.current()) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                if (appData == null || appData.isBlank()) {
                    appData = Path.of(userHome, "AppData", "Roaming").toString();
                }
                yield Path.of(appData, "barn", "barn.conf");
            }
            case MACOS -> Path.of(userHome, "Library", "Application Support", "barn", "barn.conf");
            case LINUX -> Path.of(userHome, ".config", "barn", "barn.conf");
        };
    }

    /**
     * Gets the list of configuration file paths in priority order.
     *
     * <p>Paths are returned in order of increasing priority - later paths
     * override earlier ones.
     *
     * @return list of config paths to check
     */
    public static List<Path> getConfigSearchPaths() {
        List<Path> paths = new ArrayList<>();
        paths.add(getSystemConfigPath());
        paths.add(getUserConfigPath());
        return paths;
    }
}
