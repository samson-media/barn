package com.samsonmedia.barn.config;

/**
 * Represents the operating system Barn is running on.
 */
public enum OperatingSystem {
    LINUX,
    MACOS,
    WINDOWS;

    private static OperatingSystem detected;

    /**
     * Detects the current operating system.
     *
     * @return the detected operating system
     */
    public static OperatingSystem current() {
        if (detected == null) {
            detected = detect();
        }
        return detected;
    }

    private static OperatingSystem detect() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return WINDOWS;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return MACOS;
        } else {
            return LINUX;
        }
    }

    /**
     * Checks if the current OS is Unix-like (Linux or macOS).
     *
     * @return true if Unix-like, false otherwise
     */
    public boolean isUnixLike() {
        return this == LINUX || this == MACOS;
    }
}
