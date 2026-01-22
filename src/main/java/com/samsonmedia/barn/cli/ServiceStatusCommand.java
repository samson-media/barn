package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.config.OperatingSystem;
import com.samsonmedia.barn.ipc.IpcClient;
import com.samsonmedia.barn.ipc.IpcException;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.platform.linux.SystemdManager;
import com.samsonmedia.barn.platform.macos.LaunchdManager;
import com.samsonmedia.barn.platform.windows.WindowsServiceManager;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to show the Barn service status.
 */
@Command(
    name = "status",
    mixinStandardHelpOptions = true,
    description = "Show service status"
)
public class ServiceStatusCommand extends BaseCommand {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceStatusCommand.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    @Option(names = {"--barn-dir"}, description = "Barn data directory", hidden = true)
    private Path barnDir;

    @Override
    public Integer call() {
        try {
            Path effectiveBarnDir = getEffectiveBarnDir();
            ServiceStatus status = getServiceStatus(effectiveBarnDir);

            outputStatus(status);
            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to get service status", e);
            return EXIT_ERROR;
        }
    }

    private ServiceStatus getServiceStatus(Path effectiveBarnDir) throws IOException {
        Path pidFile = effectiveBarnDir.resolve("barn.pid");
        String serviceManager = getServiceManager();

        if (!Files.exists(pidFile)) {
            return ServiceStatus.stopped(effectiveBarnDir, serviceManager);
        }

        String pidStr = Files.readString(pidFile).trim();
        long pid;
        try {
            pid = Long.parseLong(pidStr);
        } catch (NumberFormatException e) {
            return ServiceStatus.stopped(effectiveBarnDir, serviceManager);
        }

        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            // Stale PID file
            return ServiceStatus.stopped(effectiveBarnDir, serviceManager);
        }

        ProcessHandle proc = handle.get();
        Instant startTime = proc.info().startInstant().orElse(Instant.now());

        // Try to get detailed status via IPC
        Path socketPath = effectiveBarnDir.resolve("barn.sock");
        if (Files.exists(socketPath)) {
            try (IpcClient client = new IpcClient(socketPath)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = client.send("get_service_status", null, Map.class);
                if (payload != null) {
                    int running = getIntValue(payload, "activeJobs", 0);
                    int queued = getIntValue(payload, "queuedJobs", 0);
                    int succeeded = countJobsByState(effectiveBarnDir, JobState.SUCCEEDED);
                    int failed = countJobsByState(effectiveBarnDir, JobState.FAILED);
                    int canceled = countJobsByState(effectiveBarnDir, JobState.CANCELED);
                    int killed = countJobsByState(effectiveBarnDir, JobState.KILLED);
                    int total = countTotalJobs(effectiveBarnDir);

                    return ServiceStatus.running(pid, startTime, running, queued, succeeded, failed,
                        canceled, killed, total, effectiveBarnDir, serviceManager);
                }
            } catch (IpcException e) {
                LOG.debug("IPC failed, using basic status: {}", e.getMessage());
            }
        }

        // Fallback: Basic status from repository
        int total = countTotalJobs(effectiveBarnDir);
        int running = countJobsByState(effectiveBarnDir, JobState.RUNNING);
        int queued = countJobsByState(effectiveBarnDir, JobState.QUEUED);
        int succeeded = countJobsByState(effectiveBarnDir, JobState.SUCCEEDED);
        int failed = countJobsByState(effectiveBarnDir, JobState.FAILED);
        int canceled = countJobsByState(effectiveBarnDir, JobState.CANCELED);
        int killed = countJobsByState(effectiveBarnDir, JobState.KILLED);

        return ServiceStatus.running(pid, startTime, running, queued, succeeded, failed, canceled,
            killed, total, effectiveBarnDir, serviceManager);
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private int countTotalJobs(Path effectiveBarnDir) throws IOException {
        BarnDirectories dirs = new BarnDirectories(effectiveBarnDir);
        if (!Files.exists(dirs.getJobsDir())) {
            return 0;
        }
        JobRepository repository = new JobRepository(dirs);
        return repository.findAll().size();
    }

    private int countJobsByState(Path effectiveBarnDir, JobState state) throws IOException {
        BarnDirectories dirs = new BarnDirectories(effectiveBarnDir);
        if (!Files.exists(dirs.getJobsDir())) {
            return 0;
        }
        JobRepository repository = new JobRepository(dirs);
        return repository.findByState(state).size();
    }

    private void outputStatus(ServiceStatus status) {
        OutputFormat format = globalOptions != null
            ? globalOptions.getOutputFormat()
            : OutputFormat.HUMAN;

        switch (format) {
            case HUMAN -> outputHumanFormat(status);
            case JSON, XML -> output(status.toMap());
            default -> outputHumanFormat(status);
        }
    }

    private void outputHumanFormat(ServiceStatus status) {
        StringBuilder sb = new StringBuilder();

        sb.append("Barn Service Status\n");
        sb.append("==================\n");

        String runningStatus = status.running ? "running" : "stopped";
        if (status.serviceManager != null) {
            runningStatus += " (" + status.serviceManager + " managed)";
        }
        sb.append(String.format("Status:     %s%n", runningStatus));

        if (status.running) {
            sb.append(String.format("PID:        %d%n", status.pid));
            sb.append(String.format("Uptime:     %s%n", formatUptime(status.uptimeSeconds)));
            sb.append(String.format("Started:    %s%n", DATE_FORMAT.format(status.startedAt)));
            sb.append("\n");
            sb.append("Jobs:\n");
            sb.append(String.format("  Running:    %d%n", status.runningJobs));
            sb.append(String.format("  Queued:     %d%n", status.queuedJobs));
            sb.append(String.format("  Succeeded:  %d%n", status.succeededJobs));
            sb.append(String.format("  Failed:     %d%n", status.failedJobs));
            sb.append(String.format("  Canceled:   %d%n", status.canceledJobs));
            sb.append(String.format("  Killed:     %d%n", status.killedJobs));
            sb.append(String.format("  Total:      %d%n", status.totalJobs));
        }

        sb.append("\n");
        sb.append(String.format("Data Dir:   %s%n", status.dataDir));

        getOut().println(sb.toString().trim());
    }

    private String formatUptime(long seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + " hours, " + minutes + " minutes";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + " days, " + hours + " hours";
        }
    }

    private Path getEffectiveBarnDir() {
        if (barnDir != null) {
            return barnDir;
        }
        return ConfigDefaults.getDefaultBaseDir();
    }

    private String getServiceManager() {
        OperatingSystem os = OperatingSystem.current();

        if (os == OperatingSystem.MACOS) {
            LaunchdManager launchd = new LaunchdManager();
            if (launchd.isLoaded()) {
                return "launchd";
            }
        } else if (os == OperatingSystem.LINUX) {
            if (SystemdManager.hasSystemd()) {
                SystemdManager systemd = new SystemdManager();
                if (systemd.isActive(false) || systemd.isActive(true)) {
                    return "systemd";
                }
            }
        } else if (os == OperatingSystem.WINDOWS) {
            if (WindowsServiceManager.hasWindowsServices()) {
                WindowsServiceManager winService = new WindowsServiceManager();
                if (winService.isRunning()) {
                    return "Windows SCM";
                }
            }
        }

        return null;
    }

    /**
     * Service status data.
     */
    private static final class ServiceStatus {
        final boolean running;
        final long pid;
        final Instant startedAt;
        final long uptimeSeconds;
        final int runningJobs;
        final int queuedJobs;
        final int succeededJobs;
        final int failedJobs;
        final int canceledJobs;
        final int killedJobs;
        final int totalJobs;
        final Path dataDir;
        final String serviceManager;

        private ServiceStatus(boolean running, long pid, Instant startedAt, long uptimeSeconds,
                int runningJobs, int queuedJobs, int succeededJobs, int failedJobs, int canceledJobs,
                int killedJobs, int totalJobs, Path dataDir, String serviceManager) {
            this.running = running;
            this.pid = pid;
            this.startedAt = startedAt;
            this.uptimeSeconds = uptimeSeconds;
            this.runningJobs = runningJobs;
            this.queuedJobs = queuedJobs;
            this.succeededJobs = succeededJobs;
            this.failedJobs = failedJobs;
            this.canceledJobs = canceledJobs;
            this.killedJobs = killedJobs;
            this.totalJobs = totalJobs;
            this.dataDir = dataDir;
            this.serviceManager = serviceManager;
        }

        static ServiceStatus stopped(Path dataDir, String serviceManager) {
            return new ServiceStatus(false, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, dataDir, serviceManager);
        }

        static ServiceStatus running(long pid, Instant startedAt, int runningJobs, int queuedJobs,
                int succeededJobs, int failedJobs, int canceledJobs, int killedJobs, int totalJobs,
                Path dataDir, String serviceManager) {
            long uptime = Duration.between(startedAt, Instant.now()).getSeconds();
            return new ServiceStatus(true, pid, startedAt, uptime, runningJobs, queuedJobs,
                succeededJobs, failedJobs, canceledJobs, killedJobs, totalJobs, dataDir, serviceManager);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", running ? "running" : "stopped");
            map.put("running", running);

            if (running) {
                map.put("pid", pid);
                map.put("uptime_seconds", uptimeSeconds);
                map.put("started_at", startedAt != null ? startedAt.toString() : null);

                Map<String, Object> jobs = new LinkedHashMap<>();
                jobs.put("running", runningJobs);
                jobs.put("queued", queuedJobs);
                jobs.put("succeeded", succeededJobs);
                jobs.put("failed", failedJobs);
                jobs.put("canceled", canceledJobs);
                jobs.put("killed", killedJobs);
                jobs.put("total", totalJobs);
                map.put("jobs", jobs);
            }

            map.put("data_dir", dataDir.toString());
            if (serviceManager != null) {
                map.put("managed_by", serviceManager);
            }

            return map;
        }
    }
}
