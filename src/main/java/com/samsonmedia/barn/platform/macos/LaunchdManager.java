package com.samsonmedia.barn.platform.macos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages macOS launchd service operations.
 *
 * <p>Provides methods for loading, unloading, starting, and stopping
 * launchd services using the launchctl command.
 */
public class LaunchdManager {

    private static final Logger LOG = LoggerFactory.getLogger(LaunchdManager.class);
    private static final String LABEL = "com.samsonmedia.barn";
    private static final long COMMAND_TIMEOUT_SECONDS = 30;

    /**
     * Gets the launchd service label.
     *
     * @return the service label
     */
    public String getLabel() {
        return LABEL;
    }

    /**
     * Gets the path for the system daemon plist file.
     *
     * @return the system daemon plist path
     */
    public Path getSystemDaemonPath() {
        return Path.of("/Library/LaunchDaemons", LABEL + ".plist");
    }

    /**
     * Gets the path for the user agent plist file.
     *
     * @return the user agent plist path
     */
    public Path getUserAgentPath() {
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, "Library", "LaunchAgents", LABEL + ".plist");
    }

    /**
     * Loads a launchd plist file.
     *
     * @param plistPath the path to the plist file
     * @throws LaunchdException if the load fails
     */
    public void load(Path plistPath) {
        Objects.requireNonNull(plistPath, "plistPath must not be null");
        LOG.info("Loading launchd plist: {}", plistPath);

        int exitCode = exec("launchctl", "load", plistPath.toString());
        if (exitCode != 0) {
            throw new LaunchdException("Failed to load plist: " + plistPath);
        }
    }

    /**
     * Unloads a launchd plist file.
     *
     * @param plistPath the path to the plist file
     * @throws LaunchdException if the unload fails
     */
    public void unload(Path plistPath) {
        Objects.requireNonNull(plistPath, "plistPath must not be null");
        LOG.info("Unloading launchd plist: {}", plistPath);

        int exitCode = exec("launchctl", "unload", plistPath.toString());
        if (exitCode != 0) {
            throw new LaunchdException("Failed to unload plist: " + plistPath);
        }
    }

    /**
     * Checks if the service is loaded in launchd.
     *
     * @return true if the service is loaded
     */
    public boolean isLoaded() {
        try {
            ProcessBuilder pb = new ProcessBuilder("launchctl", "list");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(LABEL)) {
                        return true;
                    }
                }
            }

            process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return false;
        } catch (IOException | InterruptedException e) {
            LOG.warn("Failed to check if service is loaded: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Starts the launchd service.
     *
     * @throws LaunchdException if the start fails
     */
    public void start() {
        LOG.info("Starting launchd service: {}", LABEL);
        int exitCode = exec("launchctl", "start", LABEL);
        if (exitCode != 0) {
            throw new LaunchdException("Failed to start service: " + LABEL);
        }
    }

    /**
     * Stops the launchd service.
     *
     * @throws LaunchdException if the stop fails
     */
    public void stop() {
        LOG.info("Stopping launchd service: {}", LABEL);
        int exitCode = exec("launchctl", "stop", LABEL);
        if (exitCode != 0) {
            throw new LaunchdException("Failed to stop service: " + LABEL);
        }
    }

    /**
     * Checks if the current process is running under launchd.
     *
     * @return true if running under launchd
     */
    public static boolean isUnderLaunchd() {
        // Check for launchd environment variable
        if (System.getenv("__CFBundleIdentifier") != null) {
            return true;
        }

        // Check if parent process is launchd
        return ProcessHandle.current().parent()
            .map(p -> p.info().command().orElse(""))
            .map(c -> c.contains("launchd"))
            .orElse(false);
    }

    /**
     * Generates the plist content for the launchd service.
     *
     * @param barnBinaryPath the path to the barn binary
     * @param barnDir the barn data directory
     * @return the plist XML content
     */
    public String generatePlist(Path barnBinaryPath, Path barnDir) {
        Objects.requireNonNull(barnBinaryPath, "barnBinaryPath must not be null");
        Objects.requireNonNull(barnDir, "barnDir must not be null");

        Path logsDir = barnDir.resolve("logs");

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
              "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>%s</string>

                <key>ProgramArguments</key>
                <array>
                    <string>%s</string>
                    <string>service</string>
                    <string>start</string>
                    <string>--foreground</string>
                    <string>--barn-dir</string>
                    <string>%s</string>
                </array>

                <key>RunAtLoad</key>
                <true/>

                <key>KeepAlive</key>
                <true/>

                <key>StandardOutPath</key>
                <string>%s/barn.log</string>

                <key>StandardErrorPath</key>
                <string>%s/barn.log</string>

                <key>WorkingDirectory</key>
                <string>%s</string>
            </dict>
            </plist>
            """.formatted(
                LABEL,
                barnBinaryPath.toString(),
                barnDir.toString(),
                logsDir.toString(),
                logsDir.toString(),
                barnDir.toString()
            );
    }

    /**
     * Writes a plist file to the specified path.
     *
     * @param plistPath the destination path
     * @param content the plist content
     * @throws IOException if the file cannot be written
     */
    public void writePlist(Path plistPath, String content) throws IOException {
        Objects.requireNonNull(plistPath, "plistPath must not be null");
        Objects.requireNonNull(content, "content must not be null");

        Path parent = plistPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.writeString(plistPath, content);
        LOG.info("Wrote plist file: {}", plistPath);
    }

    /**
     * Deletes a plist file.
     *
     * @param plistPath the path to the plist file
     * @return true if the file was deleted or didn't exist
     */
    public boolean deletePlist(Path plistPath) {
        Objects.requireNonNull(plistPath, "plistPath must not be null");

        try {
            boolean deleted = Files.deleteIfExists(plistPath);
            if (deleted) {
                LOG.info("Deleted plist file: {}", plistPath);
            }
            return true;
        } catch (IOException e) {
            LOG.error("Failed to delete plist file: {}", plistPath, e);
            return false;
        }
    }

    private int exec(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output to prevent blocking
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.debug("launchctl: {}", line);
                }
            }

            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new LaunchdException("Command timed out: " + String.join(" ", command));
            }

            return process.exitValue();
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to execute command: {}", String.join(" ", command), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new LaunchdException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    /**
     * Exception thrown when a launchd operation fails.
     */
    public static class LaunchdException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new LaunchdException.
         *
         * @param message the error message
         */
        public LaunchdException(String message) {
            super(message);
        }

        /**
         * Creates a new LaunchdException with a cause.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public LaunchdException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
