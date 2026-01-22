package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.execution.ProcessUtils;
import com.samsonmedia.barn.logging.BarnLogger;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to stop the Barn service.
 */
@Command(
    name = "stop",
    mixinStandardHelpOptions = true,
    description = "Stop the Barn service"
)
public class ServiceStopCommand extends BaseCommand {

    private static final BarnLogger LOG = BarnLogger.getLogger(ServiceStopCommand.class);
    private static final long DEFAULT_WAIT_SECONDS = 30;

    @Option(names = {"--force", "-f"}, description = "Force immediate stop (SIGKILL)")
    private boolean force;

    @Option(names = {"--barn-dir"}, description = "Barn data directory", hidden = true)
    private Path barnDir;

    @Override
    public Integer call() {
        try {
            Path effectiveBarnDir = getEffectiveBarnDir();
            Path pidFile = effectiveBarnDir.resolve("barn.pid");

            if (!Files.exists(pidFile)) {
                if (force) {
                    // Force mode - silently succeed if not running
                    getOut().println("Barn service was not running");
                    return EXIT_SUCCESS;
                }
                outputError("Barn service is not running");
                return EXIT_ERROR;
            }

            String pidStr = Files.readString(pidFile).trim();
            long pid = Long.parseLong(pidStr);

            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isEmpty() || !handle.get().isAlive()) {
                // Process not running but PID file exists - clean up
                Files.deleteIfExists(pidFile);
                if (force) {
                    getOut().println("Barn service was not running");
                    return EXIT_SUCCESS;
                }
                outputError("Barn service is not running (stale PID file removed)");
                return EXIT_ERROR;
            }

            ProcessHandle proc = handle.get();

            if (force) {
                // Force kill
                ProcessUtils.killTreeForcibly(pid);
                Files.deleteIfExists(pidFile);
                getOut().println("Barn service force stopped");
            } else {
                // Graceful shutdown - send SIGTERM
                boolean destroyed = proc.destroy();
                if (!destroyed) {
                    outputError("Failed to send termination signal");
                    return EXIT_ERROR;
                }

                // Wait for termination
                boolean terminated = waitForTermination(proc);
                if (!terminated) {
                    LOG.warn("Service did not stop gracefully, forcing");
                    ProcessUtils.killTreeForcibly(pid);
                }

                Files.deleteIfExists(pidFile);
                getOut().println("Barn service stopped");
            }

            return EXIT_SUCCESS;

        } catch (IOException | NumberFormatException e) {
            outputError("Failed to stop service", e);
            return EXIT_ERROR;
        }
    }

    private boolean waitForTermination(ProcessHandle proc) {
        long deadline = System.currentTimeMillis() + (DEFAULT_WAIT_SECONDS * 1000);

        while (proc.isAlive() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return !proc.isAlive();
    }

    private Path getEffectiveBarnDir() {
        if (barnDir != null) {
            return barnDir;
        }
        return ConfigDefaults.getDefaultBaseDir();
    }
}
