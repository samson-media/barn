package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.ipc.IpcClient;
import com.samsonmedia.barn.ipc.IpcException;
import com.samsonmedia.barn.logging.BarnLogger;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to reload the Barn service configuration.
 */
@Command(
    name = "reload",
    mixinStandardHelpOptions = true,
    description = "Reload configuration without stopping jobs"
)
public class ServiceReloadCommand extends BaseCommand {

    private static final BarnLogger LOG = BarnLogger.getLogger(ServiceReloadCommand.class);

    @Option(names = {"--barn-dir"}, description = "Barn data directory", hidden = true)
    private Path barnDir;

    @Override
    public Integer call() {
        try {
            Path effectiveBarnDir = getEffectiveBarnDir();

            // Check if running
            if (!isServiceRunning(effectiveBarnDir)) {
                outputError("Barn service is not running");
                return EXIT_ERROR;
            }

            // Send reload via IPC
            Path socketPath = effectiveBarnDir.resolve("barn.sock");
            if (!Files.exists(socketPath)) {
                // Fallback: Send SIGHUP signal
                return sendSighup(effectiveBarnDir);
            }

            try (IpcClient client = new IpcClient(socketPath)) {
                client.send("reload", null, String.class);
                getOut().println("Configuration reloaded");
                return EXIT_SUCCESS;

            } catch (IpcException e) {
                LOG.warn("IPC failed, trying SIGHUP: {}", e.getMessage());
                return sendSighup(effectiveBarnDir);
            }

        } catch (IOException e) {
            outputError("Failed to reload configuration", e);
            return EXIT_ERROR;
        }
    }

    private int sendSighup(Path effectiveBarnDir) throws IOException {
        Path pidFile = effectiveBarnDir.resolve("barn.pid");
        if (!Files.exists(pidFile)) {
            outputError("Cannot send reload signal - no PID file");
            return EXIT_ERROR;
        }

        String pidStr = Files.readString(pidFile).trim();
        long pid = Long.parseLong(pidStr);

        // On Unix, we can send SIGHUP. On Windows, this won't work.
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) {
            outputError("Reload via signal not supported on Windows. IPC socket required.");
            return EXIT_ERROR;
        }

        ProcessBuilder pb = new ProcessBuilder("kill", "-HUP", String.valueOf(pid));
        Process process = pb.start();

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                outputError("Failed to send reload signal");
                return EXIT_ERROR;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outputError("Interrupted while sending reload signal");
            return EXIT_ERROR;
        }

        getOut().println("Configuration reloaded");
        return EXIT_SUCCESS;
    }

    private boolean isServiceRunning(Path effectiveBarnDir) {
        Path pidFile = effectiveBarnDir.resolve("barn.pid");
        if (!Files.exists(pidFile)) {
            return false;
        }

        try {
            String pidStr = Files.readString(pidFile).trim();
            long pid = Long.parseLong(pidStr);
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            return handle.map(ProcessHandle::isAlive).orElse(false);
        } catch (IOException | NumberFormatException e) {
            return false;
        }
    }

    private Path getEffectiveBarnDir() {
        if (barnDir != null) {
            return barnDir;
        }
        return ConfigDefaults.getDefaultBaseDir();
    }
}
