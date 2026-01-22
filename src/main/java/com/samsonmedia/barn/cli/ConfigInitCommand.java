package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.samsonmedia.barn.config.CleanupConfig;
import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.config.ServiceConfig;
import com.samsonmedia.barn.config.StorageConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to create a default configuration file.
 */
@Command(
    name = "init",
    mixinStandardHelpOptions = true,
    description = "Create default configuration file"
)
public class ConfigInitCommand extends BaseCommand {

    @Option(names = {"--path", "-p"}, description = "Output path for config file")
    private Path outputPath;

    @Option(names = {"--force", "-f"}, description = "Overwrite existing file")
    private boolean force;

    @Override
    public Integer call() {
        Path targetPath = outputPath != null
            ? outputPath
            : ConfigDefaults.getUserConfigPath();

        if (Files.exists(targetPath) && !force) {
            outputError("Configuration file already exists: " + targetPath);
            outputError("Use --force to overwrite");
            return EXIT_ERROR;
        }

        try {
            // Create parent directories if needed
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            String content = generateDefaultConfig();
            Files.writeString(targetPath, content);

            getOut().println("Configuration file created: " + targetPath);
            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to create configuration file: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private String generateDefaultConfig() {
        StringBuilder sb = new StringBuilder();

        sb.append("# Barn Configuration\n");
        sb.append("# See docs/config.md for full documentation\n");
        sb.append("\n");

        // Service section
        sb.append("[service]\n");
        sb.append("# Logging verbosity: debug, info, warn, error\n");
        sb.append(String.format("log_level = \"%s\"%n",
            ServiceConfig.DEFAULT_LOG_LEVEL.toString().toLowerCase(Locale.ROOT)));
        sb.append("\n");
        sb.append("# Maximum jobs running simultaneously\n");
        sb.append(String.format("max_concurrent_jobs = %d%n", ServiceConfig.DEFAULT_MAX_CONCURRENT_JOBS));
        sb.append("\n");
        sb.append("# Interval in seconds between heartbeat updates for running jobs\n");
        sb.append(String.format("heartbeat_interval_seconds = %d%n",
            ServiceConfig.DEFAULT_HEARTBEAT_INTERVAL_SECONDS));
        sb.append("\n");
        sb.append("# IPC socket path for CLI communication\n");
        sb.append(String.format("ipc_socket = \"%s\"%n", ConfigDefaults.getDefaultIpcSocket()));
        sb.append("\n");
        sb.append("# Time in seconds after which a job without heartbeat is considered stale\n");
        sb.append(String.format("stale_heartbeat_threshold_seconds = %d%n",
            ServiceConfig.DEFAULT_STALE_HEARTBEAT_THRESHOLD_SECONDS));
        sb.append("\n");

        // Jobs section
        sb.append("[jobs]\n");
        sb.append("# Default timeout for jobs in seconds (1 hour)\n");
        sb.append(String.format("default_timeout_seconds = %d%n", JobsConfig.DEFAULT_TIMEOUT_SECONDS));
        sb.append("\n");
        sb.append("# Maximum number of retry attempts for failed jobs\n");
        sb.append(String.format("max_retries = %d%n", JobsConfig.DEFAULT_MAX_RETRIES));
        sb.append("\n");
        sb.append("# Initial delay before first retry in seconds\n");
        sb.append(String.format("retry_delay_seconds = %d%n", JobsConfig.DEFAULT_RETRY_DELAY_SECONDS));
        sb.append("\n");
        sb.append("# Multiplier applied to delay after each retry (exponential backoff)\n");
        sb.append(String.format("retry_backoff_multiplier = %.1f%n", JobsConfig.DEFAULT_RETRY_BACKOFF_MULTIPLIER));
        sb.append("\n");
        sb.append("# Only retry on these exit codes (empty = retry all non-zero)\n");
        sb.append("retry_on_exit_codes = []\n");
        sb.append("\n");

        // Cleanup section
        sb.append("[cleanup]\n");
        sb.append("# Enable automatic cleanup of old jobs\n");
        sb.append(String.format("enabled = %b%n", CleanupConfig.DEFAULT_ENABLED));
        sb.append("\n");
        sb.append("# Maximum age in hours for completed jobs before cleanup\n");
        sb.append(String.format("max_age_hours = %d%n", CleanupConfig.DEFAULT_MAX_AGE_HOURS));
        sb.append("\n");
        sb.append("# Interval in minutes between cleanup runs\n");
        sb.append(String.format("cleanup_interval_minutes = %d%n", CleanupConfig.DEFAULT_CLEANUP_INTERVAL_MINUTES));
        sb.append("\n");
        sb.append("# Keep failed jobs longer than successful ones\n");
        sb.append(String.format("keep_failed_jobs = %b%n", CleanupConfig.DEFAULT_KEEP_FAILED_JOBS));
        sb.append("\n");
        sb.append("# Maximum age in hours for failed jobs\n");
        sb.append(String.format("keep_failed_jobs_hours = %d%n", CleanupConfig.DEFAULT_KEEP_FAILED_JOBS_HOURS));
        sb.append("\n");

        // Storage section
        sb.append("[storage]\n");
        sb.append("# Base directory for job data\n");
        sb.append(String.format("base_dir = \"%s\"%n", ConfigDefaults.getDefaultBaseDir()));
        sb.append("\n");
        sb.append("# Maximum disk usage in GB before warnings\n");
        sb.append(String.format("max_disk_usage_gb = %d%n", StorageConfig.DEFAULT_MAX_DISK_USAGE_GB));
        sb.append("\n");
        sb.append("# Preserve job working directories after completion (for debugging)\n");
        sb.append(String.format("preserve_work_dir = %b%n", StorageConfig.DEFAULT_PRESERVE_WORK_DIR));

        return sb.toString();
    }
}
