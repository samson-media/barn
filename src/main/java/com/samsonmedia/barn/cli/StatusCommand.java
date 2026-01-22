package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to show status of all jobs.
 *
 * <p>Lists jobs with optional filtering by tag, state, or limit.
 */
@Command(
    name = "status",
    mixinStandardHelpOptions = true,
    description = "Show status of all jobs"
)
public class StatusCommand extends BaseCommand {

    private static final BarnLogger LOG = BarnLogger.getLogger(StatusCommand.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    @Option(names = {"--tag", "-t"}, description = "Filter by tag")
    private String tag;

    @Option(names = {"--state", "-s"}, description = "Filter by state")
    private JobState state;

    @Option(names = {"--limit", "-n"}, description = "Limit number of results")
    private Integer limit;

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

            // Get all jobs
            List<Job> jobs = repository.findAll();

            // Apply filters
            jobs = applyFilters(jobs);

            // Sort by creation time (newest first)
            jobs = jobs.stream()
                .sorted(Comparator.comparing(Job::createdAt).reversed())
                .collect(Collectors.toList());

            // Apply limit
            if (limit != null && limit > 0 && jobs.size() > limit) {
                jobs = jobs.subList(0, limit);
            }

            // Output
            outputStatus(jobs);

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to get job status", e);
            return EXIT_ERROR;
        }
    }

    private int runWithService() {
        try (IpcClient client = new IpcClient(getBarnDirectories().getSocketPath())) {
            // Build request payload
            Map<String, Object> payload = new LinkedHashMap<>();
            if (tag != null) {
                payload.put("tag", tag);
            }
            if (state != null) {
                payload.put("state", state.name());
            }
            if (limit != null) {
                payload.put("limit", limit);
            }

            // Send request and get response
            Object response = client.send("get_status", payload, Object.class);
            LOG.debug("IPC response type: {}, value: {}",
                response != null ? response.getClass().getName() : "null", response);

            // Convert response to List<Job>
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            List<Job> jobs = mapper.convertValue(response, new TypeReference<List<Job>>() { });

            // Output
            outputStatus(jobs);

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

    private List<Job> applyFilters(List<Job> jobs) {
        return jobs.stream()
            .filter(job -> tag == null || tag.equals(job.tag()))
            .filter(job -> state == null || state == job.state())
            .collect(Collectors.toList());
    }

    private BarnDirectories getBarnDirectories() {
        if (barnDir != null) {
            return new BarnDirectories(barnDir);
        }
        Path defaultDir = ConfigDefaults.getDefaultBaseDir();
        return new BarnDirectories(defaultDir);
    }

    private void outputStatus(List<Job> jobs) {
        OutputFormat format = globalOptions != null
            ? globalOptions.getOutputFormat()
            : OutputFormat.HUMAN;

        if (jobs.isEmpty()) {
            if (format == OutputFormat.HUMAN) {
                getOut().println("No jobs found.");
            } else {
                outputStructuredEmpty(format);
            }
            return;
        }

        switch (format) {
            case HUMAN -> outputHumanFormat(jobs);
            case JSON -> outputJsonFormat(jobs);
            case XML -> outputXmlFormat(jobs);
            default -> outputHumanFormat(jobs);
        }
    }

    private void outputHumanFormat(List<Job> jobs) {
        StringBuilder sb = new StringBuilder();

        // Calculate column widths
        int idWidth = Math.max(10, jobs.stream().mapToInt(j -> j.id().length()).max().orElse(10));
        int tagWidth = Math.max(3, jobs.stream()
            .mapToInt(j -> j.tag() != null ? j.tag().length() : 1)
            .max().orElse(3));

        // Header
        sb.append(String.format("%-" + idWidth + "s  %-9s  %-" + tagWidth + "s  %-19s  %-4s  %-8s%n",
            "ID", "STATE", "TAG", "CREATED", "EXIT", "PID"));

        // Rows
        for (Job job : jobs) {
            sb.append(String.format("%-" + idWidth + "s  %-9s  %-" + tagWidth + "s  %-19s  %-4s  %-8s%n",
                job.id(),
                job.state().toString().toLowerCase(Locale.ROOT),
                job.tag() != null ? job.tag() : "-",
                formatTimestamp(job.createdAt()),
                job.exitCode() != null ? job.exitCode().toString() : "-",
                job.pid() != null ? job.pid().toString() : "-"));
        }

        // Summary
        sb.append("\n");
        sb.append(buildSummary(jobs));

        getOut().println(sb.toString().trim());
    }

    private void outputJsonFormat(List<Job> jobs) {
        // Create structured response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobs", jobs);
        response.put("summary", buildSummaryMap(jobs));

        output(response);
    }

    private void outputXmlFormat(List<Job> jobs) {
        // Use standard formatter with jobs list wrapped
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobs", jobs);
        response.put("summary", buildSummaryMap(jobs));

        output(response);
    }

    private void outputStructuredEmpty(OutputFormat format) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobs", List.of());
        response.put("summary", Map.of("total", 0));

        output(response);
    }

    private String buildSummary(List<Job> jobs) {
        Map<JobState, Long> counts = jobs.stream()
            .collect(Collectors.groupingBy(Job::state, Collectors.counting()));

        StringBuilder summary = new StringBuilder();
        summary.append("Total: ").append(jobs.size()).append(" job");
        if (jobs.size() != 1) {
            summary.append("s");
        }

        if (!counts.isEmpty()) {
            summary.append(" (");
            boolean first = true;
            for (JobState s : JobState.values()) {
                long count = counts.getOrDefault(s, 0L);
                if (count > 0) {
                    if (!first) {
                        summary.append(", ");
                    }
                    summary.append(count).append(" ").append(s.toString().toLowerCase(Locale.ROOT));
                    first = false;
                }
            }
            summary.append(")");
        }

        return summary.toString();
    }

    private Map<String, Object> buildSummaryMap(List<Job> jobs) {
        Map<JobState, Long> counts = jobs.stream()
            .collect(Collectors.groupingBy(Job::state, Collectors.counting()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", jobs.size());

        for (JobState s : JobState.values()) {
            summary.put(s.toString().toLowerCase(Locale.ROOT), counts.getOrDefault(s, 0L));
        }

        return summary;
    }

    private String formatTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return DATE_FORMAT.format(timestamp);
    }
}
