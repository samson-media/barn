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
 * Command to restart the Barn service.
 */
@Command(
    name = "restart",
    mixinStandardHelpOptions = true,
    description = "Restart the Barn service"
)
public class ServiceRestartCommand extends BaseCommand {

    private static final BarnLogger LOG = BarnLogger.getLogger(ServiceRestartCommand.class);
    private static final long DEFAULT_WAIT_SECONDS = 30;

    @Option(names = {"--force", "-f"}, description = "Force immediate restart")
    private boolean force;

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

            // Stop the service
            int stopResult = stopService(effectiveBarnDir);
            if (stopResult != EXIT_SUCCESS) {
                outputError("Failed to stop service");
                return EXIT_ERROR;
            }

            // Wait a bit for cleanup
            Thread.sleep(500);

            // Start the service
            int startResult = startService(effectiveBarnDir);
            if (startResult != EXIT_SUCCESS) {
                outputError("Failed to start service after stop");
                return EXIT_ERROR;
            }

            getOut().println("Barn service restarted");
            return EXIT_SUCCESS;

        } catch (IOException | InterruptedException e) {
            outputError("Failed to restart service", e);
            return EXIT_ERROR;
        }
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

    private int stopService(Path effectiveBarnDir) throws IOException {
        Path pidFile = effectiveBarnDir.resolve("barn.pid");
        String pidStr = Files.readString(pidFile).trim();
        long pid = Long.parseLong(pidStr);

        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            Files.deleteIfExists(pidFile);
            return EXIT_SUCCESS;
        }

        ProcessHandle proc = handle.get();

        if (force) {
            ProcessUtils.killTreeForcibly(pid);
        } else {
            proc.destroy();
            if (!waitForTermination(proc)) {
                ProcessUtils.killTreeForcibly(pid);
            }
        }

        Files.deleteIfExists(pidFile);
        return EXIT_SUCCESS;
    }

    private int startService(Path effectiveBarnDir) throws IOException {
        ProcessBuilder pb = buildDaemonCommand(effectiveBarnDir);

        if (globalOptions != null && globalOptions.getConfigPath().isPresent()) {
            pb.command().add("--config");
            pb.command().add(globalOptions.getConfigPath().get().toString());
        }

        Path logsDir = effectiveBarnDir.resolve("logs");
        Files.createDirectories(logsDir);
        Path logFile = logsDir.resolve("barn.log");

        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        pb.start();

        return EXIT_SUCCESS;
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

    private ProcessBuilder buildDaemonCommand(Path effectiveBarnDir) {
        // Check if running as a native image
        if (isNativeImage()) {
            // In native image, re-exec the current executable
            String executable = ProcessHandle.current().info().command().orElse("barn");
            return new ProcessBuilder(
                executable,
                "service", "start", "--foreground",
                "--barn-dir", effectiveBarnDir.toString()
            );
        } else {
            // Running in JVM, use java command
            String javaHome = System.getProperty("java.home");
            String classPath = System.getProperty("java.class.path");
            return new ProcessBuilder(
                Path.of(javaHome, "bin", "java").toString(),
                "-cp", classPath,
                "com.samsonmedia.barn.Main",
                "service", "start", "--foreground",
                "--barn-dir", effectiveBarnDir.toString()
            );
        }
    }

    private boolean isNativeImage() {
        // GraalVM sets this property when running as native image
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }
}
