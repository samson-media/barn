package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.ipc.IpcClient;
import com.samsonmedia.barn.ipc.IpcException;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobManifest;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.state.BarnDirectories;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command to show detailed information about a specific job.
 */
@Command(
    name = "describe",
    mixinStandardHelpOptions = true,
    description = "Show detailed job information"
)
public class DescribeCommand extends BaseCommand {

    private static final Logger LOG = LoggerFactory.getLogger(DescribeCommand.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private static final int DEFAULT_LOG_LINES = 20;

    @Parameters(index = "0", description = "Job ID")
    private String jobId;

    @Option(names = {"--logs"}, description = "Include log file contents")
    private boolean includeLogs;

    @Option(names = {"--manifest"}, description = "Show full manifest")
    private boolean showManifest;

    // For testing
    private Path barnDir;

    /**
     * Sets the barn directory (for testing).
     *
     * @param barnDir the barn directory path
     */
    public void setBarnDir(Path barnDir) {
        this.barnDir = barnDir;
    }

    @Override
    public Integer call() {
        if (isOffline()) {
            return runOffline();
        }
        return runWithService();
    }

    private int runOffline() {
        try {
            BarnDirectories dirs = getBarnDirectories();
            JobRepository repository = new JobRepository(dirs);

            Optional<Job> jobOpt = repository.findById(jobId);
            if (jobOpt.isEmpty()) {
                outputError("Job not found: " + jobId);
                return EXIT_ERROR;
            }

            Job job = jobOpt.get();
            JobManifest manifest = showManifest ? repository.loadManifest(jobId) : null;

            outputJobDetails(job, manifest, dirs);

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to describe job", e);
            return EXIT_ERROR;
        }
    }

    private int runWithService() {
        try (IpcClient client = new IpcClient(getBarnDirectories().getSocketPath())) {
            // Build request payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jobId", jobId);
            payload.put("includeLogs", includeLogs);
            payload.put("includeManifest", showManifest);

            // Send request and get response
            Object response = client.send("get_job", payload, Object.class);

            // Convert response to Job
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            Job job = mapper.convertValue(response, Job.class);

            // For IPC mode, we use default dirs for path display
            BarnDirectories dirs = getBarnDirectories();
            outputJobDetails(job, null, dirs);

            return EXIT_SUCCESS;

        } catch (IpcException e) {
            if ("SERVICE_NOT_RUNNING".equals(e.getCode())) {
                outputError("Service not running. Start with: barn service start\n"
                    + "Or use --offline to run without the service.");
            } else if ("NOT_FOUND".equals(e.getCode())) {
                outputError("Job not found: " + jobId);
            } else {
                outputError("IPC error: " + e.getMessage());
            }
            return EXIT_ERROR;
        } catch (IOException e) {
            outputError("Failed to describe job", e);
            return EXIT_ERROR;
        }
    }

    private BarnDirectories getBarnDirectories() {
        if (barnDir != null) {
            return new BarnDirectories(barnDir);
        }
        Path defaultDir = ConfigDefaults.getDefaultBaseDir();
        return new BarnDirectories(defaultDir);
    }

    private void outputJobDetails(Job job, JobManifest manifest, BarnDirectories dirs) throws IOException {
        OutputFormat format = globalOptions != null
            ? globalOptions.getOutputFormat()
            : OutputFormat.HUMAN;

        switch (format) {
            case HUMAN -> outputHumanFormat(job, manifest, dirs);
            case JSON -> outputJsonFormat(job, manifest, dirs);
            case XML -> outputXmlFormat(job, manifest, dirs);
            default -> outputHumanFormat(job, manifest, dirs);
        }
    }

    private void outputHumanFormat(Job job, JobManifest manifest, BarnDirectories dirs) throws IOException {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("Job: ").append(job.id()).append("\n");
        sb.append("================================================================================\n");

        // Basic info
        sb.append("State:      ").append(job.state().toString().toLowerCase(Locale.ROOT)).append("\n");
        if (job.tag() != null) {
            sb.append("Tag:        ").append(job.tag()).append("\n");
        }
        sb.append("Command:    ").append(String.join(" ", job.command())).append("\n");

        // Timestamps
        sb.append("\nTimestamps:\n");
        sb.append("  Created:  ").append(formatTimestamp(job.createdAt())).append("\n");
        sb.append("  Started:  ").append(formatTimestamp(job.startedAt())).append("\n");
        sb.append("  Finished: ").append(formatTimestamp(job.finishedAt())).append("\n");

        // Execution
        sb.append("\nExecution:\n");
        sb.append("  PID:        ").append(job.pid() != null ? job.pid() : "-").append("\n");
        sb.append("  Exit Code:  ").append(job.exitCode() != null ? job.exitCode() : "-").append("\n");
        if (job.heartbeat() != null) {
            sb.append("  Heartbeat:  ").append(formatTimestamp(job.heartbeat()));
            sb.append(" (").append(relativeTime(job.heartbeat())).append(")\n");
        }
        if (job.error() != null) {
            sb.append("  Error:      ").append(job.error()).append("\n");
        }

        // Retry info
        if (job.retryCount() > 0 || job.retryAt() != null) {
            sb.append("\nRetry:\n");
            sb.append("  Attempts:   ").append(job.retryCount()).append("\n");
            if (job.retryAt() != null) {
                sb.append("  Next Retry: ").append(formatTimestamp(job.retryAt())).append("\n");
            }
        }

        // Paths
        sb.append("\nFiles:\n");
        sb.append("  Work Dir:  ").append(dirs.getJobWorkDir(job.id())).append("\n");
        sb.append("  Logs Dir:  ").append(dirs.getJobLogsDir(job.id())).append("\n");

        // Manifest
        if (manifest != null) {
            sb.append("\nManifest:\n");
            sb.append("  Max Retries:     ").append(manifest.maxRetries()).append("\n");
            sb.append("  Retry Delay:     ").append(manifest.retryDelaySeconds()).append("s\n");
            sb.append("  Backoff:         ").append(manifest.retryBackoffMultiplier()).append("x\n");
        }

        // Logs
        if (includeLogs) {
            appendLogs(sb, dirs.getJobLogsDir(job.id()));
        }

        getOut().println(sb.toString().trim());
    }

    private void outputJsonFormat(Job job, JobManifest manifest, BarnDirectories dirs) throws IOException {
        Map<String, Object> response = buildResponseMap(job, manifest, dirs);
        output(response);
    }

    private void outputXmlFormat(Job job, JobManifest manifest, BarnDirectories dirs) throws IOException {
        Map<String, Object> response = buildResponseMap(job, manifest, dirs);
        output(response);
    }

    private Map<String, Object> buildResponseMap(Job job, JobManifest manifest, BarnDirectories dirs)
            throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();

        // Job details
        response.put("id", job.id());
        response.put("state", job.state().toString().toLowerCase(Locale.ROOT));
        response.put("tag", job.tag());
        response.put("command", job.command());
        response.put("createdAt", job.createdAt());
        response.put("startedAt", job.startedAt());
        response.put("finishedAt", job.finishedAt());
        response.put("pid", job.pid());
        response.put("exitCode", job.exitCode());
        response.put("error", job.error());
        response.put("heartbeat", job.heartbeat());
        response.put("retryCount", job.retryCount());
        response.put("retryAt", job.retryAt());

        // Paths
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("jobDir", dirs.getJobDir(job.id()).toString());
        paths.put("workDir", dirs.getJobWorkDir(job.id()).toString());
        paths.put("logsDir", dirs.getJobLogsDir(job.id()).toString());
        response.put("paths", paths);

        // Manifest
        if (manifest != null) {
            Map<String, Object> manifestMap = new LinkedHashMap<>();
            manifestMap.put("maxRetries", manifest.maxRetries());
            manifestMap.put("retryDelaySeconds", manifest.retryDelaySeconds());
            manifestMap.put("retryBackoffMultiplier", manifest.retryBackoffMultiplier());
            response.put("manifest", manifestMap);
        }

        // Logs
        if (includeLogs) {
            Map<String, String> logs = new LinkedHashMap<>();
            Path logsDir = dirs.getJobLogsDir(job.id());
            logs.put("stdout", readLogFile(logsDir.resolve("stdout.log")));
            logs.put("stderr", readLogFile(logsDir.resolve("stderr.log")));
            response.put("logs", logs);
        }

        return response;
    }

    private void appendLogs(StringBuilder sb, Path logsDir) throws IOException {
        appendLogFile(sb, logsDir.resolve("stdout.log"), "stdout.log");
        appendLogFile(sb, logsDir.resolve("stderr.log"), "stderr.log");
    }

    private void appendLogFile(StringBuilder sb, Path logFile, String name) throws IOException {
        if (!Files.exists(logFile)) {
            return;
        }

        List<String> lines = tailFile(logFile, DEFAULT_LOG_LINES);
        if (lines.isEmpty()) {
            return;
        }

        sb.append("\n--- ").append(name).append(" (last ").append(lines.size()).append(" lines) ---\n");
        for (String line : lines) {
            sb.append(line).append("\n");
        }
    }

    private String readLogFile(Path logFile) throws IOException {
        if (!Files.exists(logFile)) {
            return "";
        }

        List<String> lines = tailFile(logFile, DEFAULT_LOG_LINES);
        return String.join("\n", lines);
    }

    private List<String> tailFile(Path file, int maxLines) throws IOException {
        try (Stream<String> stream = Files.lines(file)) {
            List<String> allLines = stream.collect(Collectors.toList());
            int start = Math.max(0, allLines.size() - maxLines);
            return allLines.subList(start, allLines.size());
        }
    }

    private String formatTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return DATE_FORMAT.format(timestamp);
    }

    private String relativeTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        Duration elapsed = Duration.between(instant, Instant.now());
        long seconds = elapsed.getSeconds();

        if (seconds < 0) {
            return "in the future";
        }
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m ago";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h ago";
        }
        return (seconds / 86400) + "d ago";
    }
}
