package com.samsonmedia.barn.platform.linux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Linux systemd service operations.
 *
 * <p>Provides methods for installing, enabling, starting, and stopping
 * systemd services using the systemctl command.
 */
public class SystemdManager {

    private static final Logger LOG = LoggerFactory.getLogger(SystemdManager.class);
    private static final String SERVICE_NAME = "barn.service";
    private static final long COMMAND_TIMEOUT_SECONDS = 30;

    /**
     * Gets the path for the system service unit file.
     *
     * @return the system service path
     */
    public Path getSystemServicePath() {
        return Path.of("/etc/systemd/system", SERVICE_NAME);
    }

    /**
     * Gets the path for the user service unit file.
     *
     * @return the user service path
     */
    public Path getUserServicePath() {
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, ".config", "systemd", "user", SERVICE_NAME);
    }

    /**
     * Checks if systemd is available on this system.
     *
     * @return true if systemd is available
     */
    public static boolean hasSystemd() {
        return Files.exists(Path.of("/run/systemd/system"));
    }

    /**
     * Checks if the current process is running under systemd.
     *
     * @return true if running under systemd
     */
    public static boolean isUnderSystemd() {
        return System.getenv("INVOCATION_ID") != null
            || Files.exists(Path.of("/run/systemd/system"));
    }

    /**
     * Generates the systemd unit file content.
     *
     * @param barnBinaryPath the path to the barn binary
     * @param barnDir the barn data directory
     * @param userMode whether this is a user service
     * @return the unit file content
     */
    public String generateUnitFile(Path barnBinaryPath, Path barnDir, boolean userMode) {
        Objects.requireNonNull(barnBinaryPath, "barnBinaryPath must not be null");
        Objects.requireNonNull(barnDir, "barnDir must not be null");

        if (userMode) {
            return generateUserUnitFile(barnBinaryPath, barnDir);
        } else {
            return generateSystemUnitFile(barnBinaryPath, barnDir);
        }
    }

    private String generateSystemUnitFile(Path barnBinaryPath, Path barnDir) {
        return """
            [Unit]
            Description=Barn Job Daemon
            Documentation=https://github.com/samson-media/barn
            After=network.target

            [Service]
            Type=simple
            ExecStart=%s service start --foreground --barn-dir %s
            ExecStop=%s service stop --barn-dir %s
            ExecReload=/bin/kill -HUP $MAINPID
            Restart=on-failure
            RestartSec=5

            # Security hardening
            NoNewPrivileges=true
            ProtectSystem=strict
            ProtectHome=read-only
            PrivateTmp=false
            ReadWritePaths=%s

            # Logging
            StandardOutput=journal
            StandardError=journal
            SyslogIdentifier=barn

            [Install]
            WantedBy=multi-user.target
            """.formatted(
                barnBinaryPath.toString(),
                barnDir.toString(),
                barnBinaryPath.toString(),
                barnDir.toString(),
                barnDir.toString()
            );
    }

    private String generateUserUnitFile(Path barnBinaryPath, Path barnDir) {
        return """
            [Unit]
            Description=Barn Job Daemon (User)
            Documentation=https://github.com/samson-media/barn

            [Service]
            Type=simple
            ExecStart=%s service start --foreground --barn-dir %s
            ExecStop=%s service stop --barn-dir %s
            Restart=on-failure
            RestartSec=5

            [Install]
            WantedBy=default.target
            """.formatted(
                barnBinaryPath.toString(),
                barnDir.toString(),
                barnBinaryPath.toString(),
                barnDir.toString()
            );
    }

    /**
     * Writes a unit file to the specified path.
     *
     * @param unitPath the destination path
     * @param content the unit file content
     * @throws IOException if the file cannot be written
     */
    public void writeUnitFile(Path unitPath, String content) throws IOException {
        Objects.requireNonNull(unitPath, "unitPath must not be null");
        Objects.requireNonNull(content, "content must not be null");

        Path parent = unitPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.writeString(unitPath, content);
        LOG.info("Wrote unit file: {}", unitPath);
    }

    /**
     * Deletes a unit file.
     *
     * @param unitPath the path to the unit file
     * @return true if the file was deleted or didn't exist
     */
    public boolean deleteUnitFile(Path unitPath) {
        Objects.requireNonNull(unitPath, "unitPath must not be null");

        try {
            boolean deleted = Files.deleteIfExists(unitPath);
            if (deleted) {
                LOG.info("Deleted unit file: {}", unitPath);
            }
            return true;
        } catch (IOException e) {
            LOG.error("Failed to delete unit file: {}", unitPath, e);
            return false;
        }
    }

    /**
     * Reloads the systemd daemon configuration.
     *
     * @param userMode whether to reload user services
     * @throws SystemdException if the reload fails
     */
    public void daemonReload(boolean userMode) {
        LOG.info("Reloading systemd daemon (userMode={})", userMode);
        systemctl(userMode, "daemon-reload");
    }

    /**
     * Enables the barn service.
     *
     * @param userMode whether this is a user service
     * @throws SystemdException if the enable fails
     */
    public void enable(boolean userMode) {
        LOG.info("Enabling barn service (userMode={})", userMode);
        systemctl(userMode, "enable", "barn");
    }

    /**
     * Disables the barn service.
     *
     * @param userMode whether this is a user service
     * @throws SystemdException if the disable fails
     */
    public void disable(boolean userMode) {
        LOG.info("Disabling barn service (userMode={})", userMode);
        systemctl(userMode, "disable", "barn");
    }

    /**
     * Starts the barn service.
     *
     * @param userMode whether this is a user service
     * @throws SystemdException if the start fails
     */
    public void start(boolean userMode) {
        LOG.info("Starting barn service (userMode={})", userMode);
        systemctl(userMode, "start", "barn");
    }

    /**
     * Stops the barn service.
     *
     * @param userMode whether this is a user service
     * @throws SystemdException if the stop fails
     */
    public void stop(boolean userMode) {
        LOG.info("Stopping barn service (userMode={})", userMode);
        systemctl(userMode, "stop", "barn");
    }

    /**
     * Checks if the barn service is enabled.
     *
     * @param userMode whether this is a user service
     * @return true if the service is enabled
     */
    public boolean isEnabled(boolean userMode) {
        try {
            int exitCode = systemctlWithExitCode(userMode, "is-enabled", "barn");
            return exitCode == 0;
        } catch (SystemdException e) {
            return false;
        }
    }

    /**
     * Checks if the barn service is active (running).
     *
     * @param userMode whether this is a user service
     * @return true if the service is active
     */
    public boolean isActive(boolean userMode) {
        try {
            int exitCode = systemctlWithExitCode(userMode, "is-active", "barn");
            return exitCode == 0;
        } catch (SystemdException e) {
            return false;
        }
    }

    /**
     * Gets the journal logs for the barn service.
     *
     * @param userMode whether this is a user service
     * @param lines number of lines to retrieve
     * @return the log output
     * @throws SystemdException if the command fails
     */
    public String getLogs(boolean userMode, int lines) {
        List<String> command = new ArrayList<>();
        command.add("journalctl");
        if (userMode) {
            command.add("--user");
        }
        command.add("-u");
        command.add("barn");
        command.add("-n");
        command.add(String.valueOf(lines));
        command.add("--no-pager");

        return execWithOutput(command);
    }

    private void systemctl(boolean userMode, String... args) {
        List<String> command = buildSystemctlCommand(userMode, args);
        int exitCode = exec(command);
        if (exitCode != 0) {
            throw new SystemdException("systemctl command failed: " + String.join(" ", command));
        }
    }

    private int systemctlWithExitCode(boolean userMode, String... args) {
        List<String> command = buildSystemctlCommand(userMode, args);
        return exec(command);
    }

    private List<String> buildSystemctlCommand(boolean userMode, String... args) {
        List<String> command = new ArrayList<>();
        command.add("systemctl");
        if (userMode) {
            command.add("--user");
        }
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    private int exec(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output to prevent blocking
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.debug("systemctl: {}", line);
                }
            }

            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new SystemdException("Command timed out: " + String.join(" ", command));
            }

            return process.exitValue();
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to execute command: {}", String.join(" ", command), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SystemdException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    private String execWithOutput(List<String> command) {
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
                throw new SystemdException("Command timed out: " + String.join(" ", command));
            }

            return output.toString();
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to execute command: {}", String.join(" ", command), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SystemdException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    /**
     * Exception thrown when a systemd operation fails.
     */
    public static class SystemdException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new SystemdException.
         *
         * @param message the error message
         */
        public SystemdException(String message) {
            super(message);
        }

        /**
         * Creates a new SystemdException with a cause.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public SystemdException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
