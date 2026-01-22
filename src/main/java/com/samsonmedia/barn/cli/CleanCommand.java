package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.ipc.IpcClient;
import com.samsonmedia.barn.ipc.IpcException;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;
import com.samsonmedia.barn.util.DurationParser;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to remove completed jobs.
 */
@Command(
    name = "clean",
    mixinStandardHelpOptions = true,
    description = "Remove completed jobs"
)
public class CleanCommand extends BaseCommand {

    private static final BarnLogger LOG = BarnLogger.getLogger(CleanCommand.class);

    @Option(names = {"--all"}, description = "Remove all completed jobs regardless of age")
    private boolean all;

    @Option(names = {"--older-than"}, description = "Remove jobs older than duration (e.g., 24h, 7d)")
    private String olderThan;

    @Option(names = {"--include-failed"}, description = "Also remove failed jobs")
    private boolean includeFailed;

    @Option(names = {"--dry-run"}, description = "Show what would be removed without removing")
    private boolean dryRun;

    @Option(names = {"--job-id"}, description = "Remove specific job")
    private String jobId;

    @Option(names = {"--barn-dir"}, description = "Barn data directory", hidden = true)
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

            // If specific job requested
            if (jobId != null) {
                return cleanSpecificJob(repository, dirs);
            }

            // Get all jobs
            List<Job> jobs = repository.findAll();

            // Filter to cleanable jobs
            List<Job> toClean = filterCleanableJobs(jobs);

            // Apply age filter if specified
            if (olderThan != null) {
                Duration maxAge = DurationParser.parse(olderThan);
                toClean = filterByAge(toClean, maxAge);
            }

            // If not --all and no --older-than, default to keeping jobs
            if (!all && olderThan == null) {
                // Without --all or --older-than, don't clean anything
                toClean = List.of();
            }

            // Clean or dry-run
            List<CleanedJob> cleaned = new ArrayList<>();
            long totalSize = 0;

            for (Job job : toClean) {
                long size = calculateJobSize(dirs, job.id());
                cleaned.add(new CleanedJob(job, size));
                totalSize += size;

                if (!dryRun) {
                    repository.delete(job.id());
                    LOG.info("Cleaned job: {}", job.id());
                }
            }

            // Output
            outputCleanResult(cleaned, totalSize);

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to clean jobs", e);
            return EXIT_ERROR;
        } catch (IllegalArgumentException e) {
            outputError(e.getMessage());
            return EXIT_ERROR;
        }
    }

    private int cleanSpecificJob(JobRepository repository, BarnDirectories dirs) throws IOException {
        Optional<Job> jobOpt = repository.findById(jobId);
        if (jobOpt.isEmpty()) {
            outputError("Job not found: " + jobId);
            return EXIT_ERROR;
        }

        Job job = jobOpt.get();
        if (!isCleanable(job)) {
            outputError("Cannot clean job (state: " + job.state().toLowercase() + ")");
            return EXIT_ERROR;
        }

        long size = calculateJobSize(dirs, job.id());
        List<CleanedJob> cleaned = List.of(new CleanedJob(job, size));

        if (!dryRun) {
            repository.delete(jobId);
            LOG.info("Cleaned job: {}", jobId);
        }

        outputCleanResult(cleaned, size);
        return EXIT_SUCCESS;
    }

    private int runWithService() {
        try (IpcClient client = new IpcClient(getBarnDirectories().getSocketPath())) {
            // Build request payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("all", all);
            payload.put("includeFailed", includeFailed);
            payload.put("dryRun", dryRun);
            if (olderThan != null) {
                payload.put("olderThan", olderThan);
            }
            if (jobId != null) {
                payload.put("jobId", jobId);
            }

            // Send request and get response
            Object response = client.send("clean_jobs", payload, Object.class);

            // Convert response
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) response;
            List<Job> jobs = mapper.convertValue(resultMap.get("jobs"), new TypeReference<List<Job>>() { });

            // Build cleaned job list (without size info from IPC)
            List<CleanedJob> cleaned = jobs.stream()
                .map(j -> new CleanedJob(j, 0))
                .toList();

            outputCleanResult(cleaned, 0);

            return EXIT_SUCCESS;

        } catch (IpcException e) {
            if ("SERVICE_NOT_RUNNING".equals(e.getCode())) {
                outputError("Service not running. Start with: barn service start\n"
                    + "Or use --offline to run without the service.");
            } else {
                outputError("IPC error: " + e.getMessage());
            }
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

    private List<Job> filterCleanableJobs(List<Job> jobs) {
        return jobs.stream()
            .filter(this::isCleanable)
            .toList();
    }

    private boolean isCleanable(Job job) {
        // Never clean running or queued jobs
        if (job.state() == JobState.RUNNING || job.state() == JobState.QUEUED) {
            return false;
        }

        // Failed jobs only cleaned with --include-failed
        if (job.state() == JobState.FAILED && !includeFailed) {
            return false;
        }

        // Succeeded and canceled are always cleanable
        return true;
    }

    private List<Job> filterByAge(List<Job> jobs, Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        return jobs.stream()
            .filter(job -> {
                Instant finishedAt = job.finishedAt();
                if (finishedAt == null) {
                    finishedAt = job.createdAt();
                }
                return finishedAt.isBefore(cutoff);
            })
            .toList();
    }

    private long calculateJobSize(BarnDirectories dirs, String id) throws IOException {
        Path jobDir = dirs.getJobDir(id);
        if (!Files.exists(jobDir)) {
            return 0;
        }

        try (Stream<Path> walk = Files.walk(jobDir)) {
            return walk
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
        }
    }

    private void outputCleanResult(List<CleanedJob> cleaned, long totalSize) {
        OutputFormat format = globalOptions != null
            ? globalOptions.getOutputFormat()
            : OutputFormat.HUMAN;

        switch (format) {
            case HUMAN -> outputHumanFormat(cleaned, totalSize);
            case JSON -> outputJsonFormat(cleaned, totalSize);
            case XML -> outputXmlFormat(cleaned, totalSize);
            default -> outputHumanFormat(cleaned, totalSize);
        }
    }

    private void outputHumanFormat(List<CleanedJob> cleaned, long totalSize) {
        StringBuilder sb = new StringBuilder();

        String prefix = dryRun ? "Would clean" : "Cleaned";

        if (cleaned.isEmpty()) {
            sb.append(prefix).append(" 0 jobs.");
        } else {
            sb.append(prefix).append(" ").append(cleaned.size()).append(" job");
            if (cleaned.size() != 1) {
                sb.append("s");
            }
            sb.append(":\n");

            for (CleanedJob cj : cleaned) {
                sb.append("  ").append(cj.job.id())
                    .append(" (").append(cj.job.state().toLowercase())
                    .append(", ").append(formatAge(cj.job))
                    .append(")");
                if (dryRun) {
                    sb.append(" - ").append(formatSize(cj.size));
                }
                sb.append("\n");
            }

            sb.append("\n");
            if (dryRun) {
                sb.append("Would free: ").append(formatSize(totalSize));
            } else {
                sb.append("Disk space freed: ").append(formatSize(totalSize));
            }
        }

        getOut().println(sb.toString());
    }

    private void outputJsonFormat(List<CleanedJob> cleaned, long totalSize) {
        Map<String, Object> response = buildResponseMap(cleaned, totalSize);
        output(response);
    }

    private void outputXmlFormat(List<CleanedJob> cleaned, long totalSize) {
        Map<String, Object> response = buildResponseMap(cleaned, totalSize);
        output(response);
    }

    private Map<String, Object> buildResponseMap(List<CleanedJob> cleaned, long totalSize) {
        Map<String, Object> response = new LinkedHashMap<>();

        List<Map<String, Object>> cleanedList = cleaned.stream()
            .map(cj -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", cj.job.id());
                item.put("state", cj.job.state().toLowercase());
                item.put("ageHours", calculateAgeHours(cj.job));
                item.put("sizeBytes", cj.size);
                return item;
            })
            .toList();

        response.put("cleaned", cleanedList);
        response.put("count", cleaned.size());
        response.put("bytesFreed", totalSize);
        response.put("dryRun", dryRun);

        return response;
    }

    private String formatAge(Job job) {
        Instant finished = job.finishedAt();
        if (finished == null) {
            finished = job.createdAt();
        }
        Duration age = Duration.between(finished, Instant.now());

        long hours = age.toHours();
        if (hours < 24) {
            return hours + " hours old";
        }
        long days = age.toDays();
        return days + " day" + (days != 1 ? "s" : "") + " old";
    }

    private long calculateAgeHours(Job job) {
        Instant finished = job.finishedAt();
        if (finished == null) {
            finished = job.createdAt();
        }
        return Duration.between(finished, Instant.now()).toHours();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return bytes / 1024 + " KB";
        }
        if (bytes < 1024L * 1024 * 1024) {
            return bytes / (1024 * 1024) + " MB";
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Helper record for cleaned job information.
     */
    private record CleanedJob(Job job, long size) { }
}
