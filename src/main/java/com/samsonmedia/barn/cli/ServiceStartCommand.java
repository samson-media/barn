package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samsonmedia.barn.config.Config;
import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.config.ConfigLoader;
import com.samsonmedia.barn.service.BarnService;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to start the Barn service.
 */
@Command(
    name = "start",
    mixinStandardHelpOptions = true,
    description = "Start the Barn service"
)
public class ServiceStartCommand extends BaseCommand {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceStartCommand.class);

    @Option(names = {"--foreground", "-f"}, description = "Run in foreground (don't daemonize)")
    private boolean foreground;

    @Option(names = {"--foreground-test"}, hidden = true, description = "Test mode for foreground")
    private boolean foregroundTest;

    @Option(names = {"--dry-run"}, hidden = true, description = "Don't actually start, just validate")
    private boolean dryRun;

    @Option(names = {"--barn-dir"}, description = "Barn data directory", hidden = true)
    private Path barnDir;

    @Override
    public Integer call() {
        try {
            Path effectiveBarnDir = getEffectiveBarnDir();

            // Check if already running
            if (isServiceRunning(effectiveBarnDir)) {
                outputError("Barn service is already running");
                return EXIT_ERROR;
            }

            if (dryRun) {
                getOut().println("Barn service would be started (dry run)");
                return EXIT_SUCCESS;
            }

            if (foreground || foregroundTest) {
                return startForeground(effectiveBarnDir);
            } else {
                return startDaemon(effectiveBarnDir);
            }

        } catch (IOException e) {
            outputError("Failed to start service", e);
            return EXIT_ERROR;
        }
    }

    private int startForeground(Path effectiveBarnDir) throws IOException {
        writePidFile(effectiveBarnDir);
        getOut().println("Barn service started (PID: " + ProcessHandle.current().pid() + ")");

        if (foregroundTest) {
            // In test mode, just return immediately without blocking
            return EXIT_SUCCESS;
        }

        Config config = loadConfig(effectiveBarnDir);
        final BarnService service = new BarnService(config);

        // Set up shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received");
            service.stop();
            deletePidFile(effectiveBarnDir);
        }));

        try {
            service.start();
            service.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Service interrupted");
        }

        return EXIT_SUCCESS;
    }

    private int startDaemon(Path effectiveBarnDir) throws IOException {
        // For daemonizing, we re-exec with --foreground
        // This is a simplified approach; production would use proper daemon techniques
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

        Process process = pb.start();
        long pid = process.pid();

        getOut().println("Barn service started (PID: " + pid + ")");

        return EXIT_SUCCESS;
    }

    private Config loadConfig(Path effectiveBarnDir) throws IOException {
        if (globalOptions != null && globalOptions.getConfigPath().isPresent()) {
            Path configPath = globalOptions.getConfigPath().get();
            if (Files.exists(configPath)) {
                return ConfigLoader.load(configPath);
            }
        }

        // Check for default config file locations
        Path defaultConfig = effectiveBarnDir.resolve("barn.conf");
        if (Files.exists(defaultConfig)) {
            return ConfigLoader.load(defaultConfig);
        }

        return Config.withDefaults();
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

    private void writePidFile(Path effectiveBarnDir) throws IOException {
        Files.createDirectories(effectiveBarnDir);
        Path pidFile = effectiveBarnDir.resolve("barn.pid");
        Files.writeString(pidFile, String.valueOf(ProcessHandle.current().pid()));
    }

    private void deletePidFile(Path effectiveBarnDir) {
        try {
            Path pidFile = effectiveBarnDir.resolve("barn.pid");
            Files.deleteIfExists(pidFile);
        } catch (IOException e) {
            LOG.warn("Failed to delete PID file: {}", e.getMessage());
        }
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
