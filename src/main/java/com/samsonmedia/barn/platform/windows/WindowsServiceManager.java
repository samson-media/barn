package com.samsonmedia.barn.platform.windows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Windows service operations using sc.exe.
 *
 * <p>Provides methods for installing, starting, and stopping Windows services
 * using the built-in sc.exe command.
 */
public class WindowsServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(WindowsServiceManager.class);
    private static final String SERVICE_NAME = "barn";
    private static final String DISPLAY_NAME = "Barn Job Daemon";
    private static final long COMMAND_TIMEOUT_SECONDS = 30;

    /**
     * Gets the service name.
     *
     * @return the service name
     */
    public String getServiceName() {
        return SERVICE_NAME;
    }

    /**
     * Gets the display name for the service.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    /**
     * Checks if the current process is running with administrator privileges.
     *
     * @return true if running as administrator
     */
    public static boolean isAdmin() {
        try {
            ProcessBuilder pb = new ProcessBuilder("net", "session");
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Checks if this system supports Windows services.
     *
     * @return true if Windows services are supported
     */
    public static boolean hasWindowsServices() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("windows");
    }

    /**
     * Checks if the service is running under the Windows Service Control Manager.
     *
     * @return true if running as a Windows service
     */
    public static boolean isUnderServiceManager() {
        // Check for environment variable set by Windows SCM
        return System.getenv("SERVICE_NAME") != null
            || System.getProperty("winsw.id") != null;
    }

    /**
     * Installs the barn service.
     *
     * @param binaryPath the path to the barn binary
     * @param barnDir the barn data directory
     * @throws WindowsServiceException if installation fails
     */
    public void install(String binaryPath, String barnDir) {
        Objects.requireNonNull(binaryPath, "binaryPath must not be null");
        Objects.requireNonNull(barnDir, "barnDir must not be null");

        LOG.info("Installing Windows service: {}", SERVICE_NAME);

        String binPath = String.format("\"%s\" service start --foreground --barn-dir \"%s\"",
            binaryPath, barnDir);

        // Create the service
        exec("sc", "create", SERVICE_NAME,
            "binPath=" + binPath,
            "DisplayName=" + DISPLAY_NAME,
            "start=auto");

        // Set description
        exec("sc", "description", SERVICE_NAME,
            "Cross-platform job daemon for media processing");

        // Configure failure recovery (restart after 5 seconds)
        exec("sc", "failure", SERVICE_NAME,
            "reset=86400",
            "actions=restart/5000/restart/5000/restart/5000");

        LOG.info("Windows service installed successfully: {}", SERVICE_NAME);
    }

    /**
     * Uninstalls the barn service.
     *
     * @throws WindowsServiceException if uninstallation fails
     */
    public void uninstall() {
        LOG.info("Uninstalling Windows service: {}", SERVICE_NAME);
        exec("sc", "delete", SERVICE_NAME);
        LOG.info("Windows service uninstalled successfully: {}", SERVICE_NAME);
    }

    /**
     * Starts the barn service.
     *
     * @throws WindowsServiceException if the service fails to start
     */
    public void start() {
        LOG.info("Starting Windows service: {}", SERVICE_NAME);
        exec("sc", "start", SERVICE_NAME);
    }

    /**
     * Stops the barn service.
     *
     * @throws WindowsServiceException if the service fails to stop
     */
    public void stop() {
        LOG.info("Stopping Windows service: {}", SERVICE_NAME);
        exec("sc", "stop", SERVICE_NAME);
    }

    /**
     * Checks if the barn service is installed.
     *
     * @return true if the service is installed
     */
    public boolean isInstalled() {
        try {
            int exitCode = execWithExitCode("sc", "query", SERVICE_NAME);
            return exitCode == 0;
        } catch (WindowsServiceException e) {
            return false;
        }
    }

    /**
     * Checks if the barn service is running.
     *
     * @return true if the service is running
     */
    public boolean isRunning() {
        try {
            String output = execWithOutput("sc", "query", SERVICE_NAME);
            return output.contains("RUNNING");
        } catch (WindowsServiceException e) {
            return false;
        }
    }

    /**
     * Gets the current state of the service.
     *
     * @return the service state, or null if the service is not installed
     */
    public ServiceState getState() {
        try {
            String output = execWithOutput("sc", "query", SERVICE_NAME);
            if (output.contains("RUNNING")) {
                return ServiceState.RUNNING;
            } else if (output.contains("STOPPED")) {
                return ServiceState.STOPPED;
            } else if (output.contains("START_PENDING")) {
                return ServiceState.START_PENDING;
            } else if (output.contains("STOP_PENDING")) {
                return ServiceState.STOP_PENDING;
            } else if (output.contains("PAUSED")) {
                return ServiceState.PAUSED;
            }
            return ServiceState.UNKNOWN;
        } catch (WindowsServiceException e) {
            return null;
        }
    }

    private void exec(String... args) {
        List<String> command = new ArrayList<>();
        for (String arg : args) {
            command.add(arg);
        }

        int exitCode = execWithExitCode(command);
        if (exitCode != 0) {
            throw new WindowsServiceException("Command failed with exit code " + exitCode
                + ": " + String.join(" ", command));
        }
    }

    private int execWithExitCode(String... args) {
        List<String> command = new ArrayList<>();
        for (String arg : args) {
            command.add(arg);
        }
        return execWithExitCode(command);
    }

    private int execWithExitCode(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output to prevent blocking
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.debug("sc: {}", line);
                }
            }

            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new WindowsServiceException("Command timed out: " + String.join(" ", command));
            }

            return process.exitValue();
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to execute command: {}", String.join(" ", command), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new WindowsServiceException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    private String execWithOutput(String... args) {
        List<String> command = new ArrayList<>();
        for (String arg : args) {
            command.add(arg);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new WindowsServiceException("Command timed out: " + String.join(" ", command));
            }

            return output.toString();
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to execute command: {}", String.join(" ", command), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new WindowsServiceException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    /**
     * Windows service states.
     */
    public enum ServiceState {
        /** Service is running. */
        RUNNING,
        /** Service is stopped. */
        STOPPED,
        /** Service is starting. */
        START_PENDING,
        /** Service is stopping. */
        STOP_PENDING,
        /** Service is paused. */
        PAUSED,
        /** Unknown state. */
        UNKNOWN
    }

    /**
     * Exception thrown when a Windows service operation fails.
     */
    public static class WindowsServiceException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new WindowsServiceException.
         *
         * @param message the error message
         */
        public WindowsServiceException(String message) {
            super(message);
        }

        /**
         * Creates a new WindowsServiceException with a cause.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public WindowsServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
