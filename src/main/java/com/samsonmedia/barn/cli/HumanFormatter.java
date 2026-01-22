package com.samsonmedia.barn.cli;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.state.JobState;

/**
 * Formats output in a human-readable table format.
 *
 * <p>Produces nicely aligned tables with optional ANSI color codes
 * when running in an interactive terminal.
 */
public class HumanFormatter implements OutputFormatter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private final boolean useColors;

    /**
     * Creates a HumanFormatter with automatic color detection.
     */
    public HumanFormatter() {
        this(System.console() != null);
    }

    /**
     * Creates a HumanFormatter with explicit color setting.
     *
     * @param useColors true to use ANSI colors
     */
    public HumanFormatter(boolean useColors) {
        this.useColors = useColors;
    }

    @Override
    public String format(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Job job) {
            return formatJob(job);
        }

        if (value instanceof Map<?, ?> map) {
            return formatMap(map);
        }

        return value.toString();
    }

    @Override
    public String formatList(List<?> values) {
        if (values == null || values.isEmpty()) {
            return "No results found.";
        }

        // Check if all values are jobs
        if (values.stream().allMatch(v -> v instanceof Job)) {
            return formatJobTable(values.stream().map(v -> (Job) v).toList());
        }

        // Generic list formatting
        StringBuilder sb = new StringBuilder();
        for (Object value : values) {
            sb.append(format(value)).append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public String formatError(String message, Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append(colorize("Error: ", RED, BOLD)).append(message);

        if (cause != null) {
            sb.append("\n");
            sb.append(colorize("Cause: ", YELLOW)).append(cause.getMessage());
        }

        return sb.toString();
    }

    private String formatJob(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append(colorize("Job: ", BOLD)).append(job.id()).append("\n");
        sb.append("  State:    ").append(colorizeState(job.state())).append("\n");
        sb.append("  Command:  ").append(String.join(" ", job.command())).append("\n");

        if (job.tag() != null) {
            sb.append("  Tag:      ").append(job.tag()).append("\n");
        }

        sb.append("  Created:  ").append(formatTimestamp(job.createdAt())).append("\n");

        if (job.startedAt() != null) {
            sb.append("  Started:  ").append(formatTimestamp(job.startedAt())).append("\n");
        }

        if (job.finishedAt() != null) {
            sb.append("  Finished: ").append(formatTimestamp(job.finishedAt())).append("\n");
        }

        if (job.exitCode() != null) {
            sb.append("  Exit:     ").append(colorizeExitCode(job.exitCode())).append("\n");
        }

        if (job.error() != null) {
            sb.append("  Error:    ").append(colorize(job.error(), RED)).append("\n");
        }

        if (job.pid() != null) {
            sb.append("  PID:      ").append(job.pid()).append("\n");
        }

        if (job.retryCount() > 0) {
            sb.append("  Retries:  ").append(job.retryCount()).append("\n");
        }

        return sb.toString().trim();
    }

    private String formatJobTable(List<Job> jobs) {
        // Define columns
        List<String> headers = List.of("ID", "STATE", "TAG", "CREATED", "EXIT");

        // Convert jobs to rows
        List<List<String>> rows = new ArrayList<>();
        for (Job job : jobs) {
            rows.add(List.of(
                job.id(),
                job.state().toString().toLowerCase(Locale.ROOT),
                job.tag() != null ? job.tag() : "-",
                formatTimestamp(job.createdAt()),
                job.exitCode() != null ? job.exitCode().toString() : "-"
            ));
        }

        return formatTable(headers, rows, jobs);
    }

    private String formatTable(List<String> headers, List<List<String>> rows, List<Job> jobs) {
        // Calculate column widths
        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }

        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        StringBuilder sb = new StringBuilder();

        // Header row
        for (int i = 0; i < headers.size(); i++) {
            sb.append(colorize(pad(headers.get(i), widths[i]), BOLD));
            if (i < headers.size() - 1) {
                sb.append("  ");
            }
        }
        sb.append("\n");

        // Data rows
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Job job = jobs.get(r);
            for (int i = 0; i < row.size(); i++) {
                String cell = pad(row.get(i), widths[i]);

                // Apply colors based on column
                if (i == 1) {  // STATE column
                    cell = colorizeState(job.state(), cell);
                } else if (i == 4 && !cell.trim().equals("-")) {  // EXIT column
                    cell = colorizeExitCode(job.exitCode(), cell);
                }

                sb.append(cell);
                if (i < row.size() - 1) {
                    sb.append("  ");
                }
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String formatMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        int maxKeyLength = 0;

        for (Object key : map.keySet()) {
            maxKeyLength = Math.max(maxKeyLength, key.toString().length());
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            sb.append(colorize(pad(key + ":", maxKeyLength + 1), BOLD));
            sb.append(" ").append(entry.getValue()).append("\n");
        }

        return sb.toString().trim();
    }

    private String formatTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return DATE_FORMAT.format(timestamp);
    }

    private String colorizeState(JobState state) {
        return colorizeState(state, state.toString().toLowerCase(Locale.ROOT));
    }

    private String colorizeState(JobState state, String text) {
        return switch (state) {
            case QUEUED -> colorize(text, CYAN);
            case RUNNING -> colorize(text, YELLOW);
            case SUCCEEDED -> colorize(text, GREEN);
            case FAILED, KILLED -> colorize(text, RED);
            case CANCELED -> text;
        };
    }

    private String colorizeExitCode(Integer exitCode) {
        return colorizeExitCode(exitCode, String.valueOf(exitCode));
    }

    private String colorizeExitCode(Integer exitCode, String text) {
        if (exitCode == null) {
            return text;
        }
        return exitCode == 0 ? colorize(text, GREEN) : colorize(text, RED);
    }

    private String colorize(String text, String... codes) {
        if (!useColors || codes.length == 0) {
            return text;
        }
        return String.join("", codes) + text + RESET;
    }

    private String pad(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }
}
