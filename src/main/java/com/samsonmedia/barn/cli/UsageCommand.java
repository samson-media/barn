package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.ipc.IpcClient;
import com.samsonmedia.barn.ipc.IpcException;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.monitoring.UsageLogger;
import com.samsonmedia.barn.monitoring.UsageRecord;
import com.samsonmedia.barn.state.BarnDirectories;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command to show resource usage for a job.
 */
@Command(
    name = "usage",
    mixinStandardHelpOptions = true,
    description = "Show resource usage for a job"
)
public class UsageCommand extends BaseCommand {

    private static final Logger LOG = LoggerFactory.getLogger(UsageCommand.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private static final int DEFAULT_LIMIT = 50;

    @Parameters(index = "0", description = "Job ID")
    private String jobId;

    @Option(names = {"--limit", "-n"}, description = "Limit number of records (default: ${DEFAULT-VALUE})")
    private int limit = DEFAULT_LIMIT;

    @Option(names = {"--csv"}, description = "Output raw CSV data")
    private boolean rawCsv;

    @Option(names = {"--summary"}, description = "Show summary statistics only")
    private boolean summaryOnly;

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

            Path usageFile = dirs.getJobLogsDir(jobId).resolve(UsageLogger.USAGE_LOG_FILENAME);
            if (!Files.exists(usageFile)) {
                outputError("No usage data available for job: " + jobId);
                return EXIT_ERROR;
            }

            List<UsageRecord> records = loadUsageRecords(usageFile);
            if (records.isEmpty()) {
                outputError("No usage data available for job: " + jobId);
                return EXIT_ERROR;
            }

            if (rawCsv) {
                outputRawCsv(usageFile);
            } else {
                outputUsage(records);
            }

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to read usage data", e);
            return EXIT_ERROR;
        }
    }

    private int runWithService() {
        try (IpcClient client = new IpcClient(getBarnDirectories().getSocketPath())) {
            // Verify job exists via IPC
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jobId", jobId);
            client.send("get_job", payload, Object.class);

            // Job exists, read usage data from filesystem
            BarnDirectories dirs = getBarnDirectories();
            Path usageFile = dirs.getJobLogsDir(jobId).resolve(UsageLogger.USAGE_LOG_FILENAME);

            if (!Files.exists(usageFile)) {
                outputError("No usage data available for job: " + jobId);
                return EXIT_ERROR;
            }

            List<UsageRecord> records = loadUsageRecords(usageFile);
            if (records.isEmpty()) {
                outputError("No usage data available for job: " + jobId);
                return EXIT_ERROR;
            }

            if (rawCsv) {
                outputRawCsv(usageFile);
            } else {
                outputUsage(records);
            }

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
            outputError("Failed to read usage data", e);
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

    private List<UsageRecord> loadUsageRecords(Path usageFile) throws IOException {
        List<UsageRecord> records = new ArrayList<>();

        try (Stream<String> lines = Files.lines(usageFile)) {
            List<String> lineList = lines.collect(Collectors.toList());

            // Skip header
            for (int i = 1; i < lineList.size(); i++) {
                try {
                    records.add(UsageRecord.fromCsvLine(lineList.get(i)));
                } catch (Exception e) {
                    LOG.debug("Skipping invalid line: {}", lineList.get(i));
                }
            }
        }

        return records;
    }

    private void outputRawCsv(Path usageFile) throws IOException {
        String content = Files.readString(usageFile);
        getOut().print(content);
    }

    private void outputUsage(List<UsageRecord> records) {
        OutputFormat format = globalOptions != null
            ? globalOptions.getOutputFormat()
            : OutputFormat.HUMAN;

        // Apply limit (take last N records)
        if (limit > 0 && records.size() > limit) {
            records = records.subList(records.size() - limit, records.size());
        }

        switch (format) {
            case HUMAN -> outputHumanFormat(records);
            case JSON -> outputJsonFormat(records);
            case XML -> outputXmlFormat(records);
            default -> outputHumanFormat(records);
        }
    }

    private void outputHumanFormat(List<UsageRecord> records) {
        StringBuilder sb = new StringBuilder();

        sb.append("Resource Usage for Job: ").append(jobId).append("\n");
        sb.append("================================================================================\n\n");

        // Summary
        UsageSummary summary = calculateSummary(records);
        sb.append("Summary (").append(records.size()).append(" samples):\n");
        sb.append(String.format("  CPU:     avg=%.1f%%  max=%.1f%%  min=%.1f%%%n",
            summary.avgCpu, summary.maxCpu, summary.minCpu));
        sb.append(String.format("  Memory:  avg=%s  max=%s  min=%s%n",
            UsageRecord.formatBytes((long) summary.avgMemory),
            UsageRecord.formatBytes(summary.maxMemory),
            UsageRecord.formatBytes(summary.minMemory)));
        sb.append(String.format("  Disk:    max=%s%n",
            UsageRecord.formatBytes(summary.maxDisk)));

        if (summary.hasGpu) {
            sb.append(String.format("  GPU:     avg=%.1f%%  max=%.1f%%%n",
                summary.avgGpu, summary.maxGpu));
            sb.append(String.format("  GPU Mem: max=%s%n",
                UsageRecord.formatBytes(summary.maxGpuMemory)));
        }

        if (!summaryOnly) {
            sb.append("\nRecent Samples:\n");
            sb.append(String.format("%-25s  %7s  %12s  %12s", "TIMESTAMP", "CPU %", "MEMORY", "DISK"));

            if (summary.hasGpu) {
                sb.append(String.format("  %7s  %12s", "GPU %", "GPU MEM"));
            }
            sb.append("\n");

            for (UsageRecord record : records) {
                sb.append(String.format("%-25s  %7.1f  %12s  %12s",
                    DATE_FORMAT.format(record.timestamp()),
                    record.cpuPercent(),
                    UsageRecord.formatBytes(record.memoryBytes()),
                    UsageRecord.formatBytes(record.diskBytes())));

                if (summary.hasGpu && record.gpuPercent() != null) {
                    sb.append(String.format("  %7.1f  %12s",
                        record.gpuPercent(),
                        UsageRecord.formatBytes(record.gpuMemoryBytes() != null ? record.gpuMemoryBytes() : 0)));
                }
                sb.append("\n");
            }
        }

        getOut().println(sb.toString().trim());
    }

    private void outputJsonFormat(List<UsageRecord> records) {
        Map<String, Object> response = buildResponseMap(records);
        output(response);
    }

    private void outputXmlFormat(List<UsageRecord> records) {
        Map<String, Object> response = buildResponseMap(records);
        output(response);
    }

    private Map<String, Object> buildResponseMap(List<UsageRecord> records) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", jobId);

        UsageSummary summary = calculateSummary(records);
        Map<String, Object> summaryMap = new LinkedHashMap<>();
        summaryMap.put("sampleCount", records.size());
        summaryMap.put("cpu", Map.of(
            "avg", summary.avgCpu,
            "max", summary.maxCpu,
            "min", summary.minCpu
        ));
        summaryMap.put("memory", Map.of(
            "avgBytes", (long) summary.avgMemory,
            "maxBytes", summary.maxMemory,
            "minBytes", summary.minMemory
        ));
        summaryMap.put("disk", Map.of("maxBytes", summary.maxDisk));

        if (summary.hasGpu) {
            summaryMap.put("gpu", Map.of(
                "avg", summary.avgGpu,
                "max", summary.maxGpu
            ));
            summaryMap.put("gpuMemory", Map.of("maxBytes", summary.maxGpuMemory));
        }
        response.put("summary", summaryMap);

        if (!summaryOnly) {
            List<Map<String, Object>> samples = new ArrayList<>();
            for (UsageRecord record : records) {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("timestamp", record.timestamp().toString());
                sample.put("cpuPercent", record.cpuPercent());
                sample.put("memoryBytes", record.memoryBytes());
                sample.put("diskBytes", record.diskBytes());
                if (record.gpuPercent() != null) {
                    sample.put("gpuPercent", record.gpuPercent());
                }
                if (record.gpuMemoryBytes() != null) {
                    sample.put("gpuMemoryBytes", record.gpuMemoryBytes());
                }
                samples.add(sample);
            }
            response.put("samples", samples);
        }

        return response;
    }

    private UsageSummary calculateSummary(List<UsageRecord> records) {
        if (records.isEmpty()) {
            return new UsageSummary(0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0);
        }

        double totalCpu = 0;
        double totalMemory = 0;
        double totalGpu = 0;
        int gpuCount = 0;

        double maxCpu = 0;
        double minCpu = Double.MAX_VALUE;
        long maxMemory = 0;
        long minMemory = Long.MAX_VALUE;
        long maxDisk = 0;
        double maxGpu = 0;
        long maxGpuMemory = 0;

        for (UsageRecord record : records) {
            totalCpu += record.cpuPercent();
            totalMemory += record.memoryBytes();

            maxCpu = Math.max(maxCpu, record.cpuPercent());
            minCpu = Math.min(minCpu, record.cpuPercent());
            maxMemory = Math.max(maxMemory, record.memoryBytes());
            minMemory = Math.min(minMemory, record.memoryBytes());
            maxDisk = Math.max(maxDisk, record.diskBytes());

            if (record.gpuPercent() != null) {
                totalGpu += record.gpuPercent();
                maxGpu = Math.max(maxGpu, record.gpuPercent());
                gpuCount++;
            }
            if (record.gpuMemoryBytes() != null) {
                maxGpuMemory = Math.max(maxGpuMemory, record.gpuMemoryBytes());
            }
        }

        double avgCpu = totalCpu / records.size();
        double avgMemory = totalMemory / records.size();
        boolean hasGpu = gpuCount > 0;
        double avgGpu = hasGpu ? totalGpu / gpuCount : 0;

        return new UsageSummary(
            avgCpu, maxCpu, minCpu,
            avgMemory, maxMemory, minMemory,
            maxDisk, hasGpu, avgGpu, maxGpu, maxGpuMemory
        );
    }

    private record UsageSummary(
        double avgCpu,
        double maxCpu,
        double minCpu,
        double avgMemory,
        long maxMemory,
        long minMemory,
        long maxDisk,
        boolean hasGpu,
        double avgGpu,
        double maxGpu,
        long maxGpuMemory
    ) { }
}
