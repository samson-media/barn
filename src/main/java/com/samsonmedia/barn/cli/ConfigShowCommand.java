package com.samsonmedia.barn.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.samsonmedia.barn.config.Config;
import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.config.ConfigLoader;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to show effective configuration.
 */
@Command(
    name = "show",
    mixinStandardHelpOptions = true,
    description = "Show effective configuration"
)
public class ConfigShowCommand extends BaseCommand {

    @Option(names = {"--defaults"}, description = "Show only default values")
    private boolean showDefaults;

    @Override
    public Integer call() {
        try {
            Config config;
            Path effectiveConfigFile = null;

            if (showDefaults) {
                config = Config.withDefaults();
            } else {
                ConfigLoader loader = new ConfigLoader();
                Optional<Path> explicitPath = globalOptions != null
                    ? globalOptions.getConfigPath()
                    : Optional.empty();
                config = loader.load(explicitPath);
                effectiveConfigFile = findConfigFile(explicitPath);
            }

            OutputFormat format = globalOptions != null
                ? globalOptions.getOutputFormat()
                : OutputFormat.HUMAN;

            switch (format) {
                case HUMAN -> outputHumanFormat(config, effectiveConfigFile);
                case JSON, XML -> output(toMap(config));
                default -> outputHumanFormat(config, effectiveConfigFile);
            }

            return EXIT_SUCCESS;

        } catch (ConfigLoader.ConfigException e) {
            outputError("Failed to load configuration: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private Path findConfigFile(Optional<Path> explicitPath) {
        if (explicitPath.isPresent() && Files.exists(explicitPath.get())) {
            return explicitPath.get();
        }
        for (Path path : ConfigDefaults.getConfigSearchPaths()) {
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private void outputHumanFormat(Config config, Path configFile) {
        StringBuilder sb = new StringBuilder();

        sb.append("Barn Configuration\n");
        sb.append("==================\n");
        if (configFile != null) {
            sb.append("Config file: ").append(configFile).append("\n");
        } else if (showDefaults) {
            sb.append("Showing default values\n");
        } else {
            sb.append("Config file: (none found, using defaults)\n");
        }
        sb.append("\n");

        // Service section
        sb.append("[service]\n");
        sb.append(String.format("log_level = %s%n", config.service().logLevel().toString().toLowerCase(Locale.ROOT)));
        sb.append(String.format("max_concurrent_jobs = %d%n", config.service().maxConcurrentJobs()));
        sb.append(String.format("heartbeat_interval_seconds = %d%n", config.service().heartbeatIntervalSeconds()));
        sb.append(String.format("ipc_socket = %s%n", config.service().ipcSocket()));
        sb.append(String.format("stale_heartbeat_threshold_seconds = %d%n",
            config.service().staleHeartbeatThresholdSeconds()));
        sb.append("\n");

        // Jobs section
        sb.append("[jobs]\n");
        sb.append(String.format("default_timeout_seconds = %d%n", config.jobs().defaultTimeoutSeconds()));
        sb.append(String.format("max_retries = %d%n", config.jobs().maxRetries()));
        sb.append(String.format("retry_delay_seconds = %d%n", config.jobs().retryDelaySeconds()));
        sb.append(String.format("retry_backoff_multiplier = %.1f%n", config.jobs().retryBackoffMultiplier()));
        sb.append(String.format("retry_on_exit_codes = %s%n", config.jobs().retryOnExitCodes()));
        sb.append("\n");

        // Cleanup section
        sb.append("[cleanup]\n");
        sb.append(String.format("enabled = %b%n", config.cleanup().enabled()));
        sb.append(String.format("max_age_hours = %d%n", config.cleanup().maxAgeHours()));
        sb.append(String.format("cleanup_interval_minutes = %d%n", config.cleanup().cleanupIntervalMinutes()));
        sb.append(String.format("keep_failed_jobs = %b%n", config.cleanup().keepFailedJobs()));
        sb.append(String.format("keep_failed_jobs_hours = %d%n", config.cleanup().keepFailedJobsHours()));
        sb.append("\n");

        // Storage section
        sb.append("[storage]\n");
        sb.append(String.format("base_dir = %s%n", config.storage().baseDir()));
        sb.append(String.format("max_disk_usage_gb = %d%n", config.storage().maxDiskUsageGb()));
        sb.append(String.format("preserve_work_dir = %b%n", config.storage().preserveWorkDir()));
        sb.append("\n");

        // Load section
        sb.append("[load]\n");
        sb.append(String.format("max_high_jobs = %d%n", config.loadLevels().maxHighJobs()));
        sb.append(String.format("max_medium_jobs = %d%n", config.loadLevels().maxMediumJobs()));
        sb.append(String.format("max_low_jobs = %d%n", config.loadLevels().maxLowJobs()));

        getOut().println(sb.toString().trim());
    }

    private Map<String, Object> toMap(Config config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("service", buildServiceSection(config));
        map.put("jobs", buildJobsSection(config));
        map.put("cleanup", buildCleanupSection(config));
        map.put("storage", buildStorageSection(config));
        map.put("load", buildLoadSection(config));
        return map;
    }

    private Map<String, Object> buildServiceSection(Config config) {
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("log_level", config.service().logLevel().toString().toLowerCase(Locale.ROOT));
        service.put("max_concurrent_jobs", config.service().maxConcurrentJobs());
        service.put("heartbeat_interval_seconds", config.service().heartbeatIntervalSeconds());
        service.put("ipc_socket", config.service().ipcSocket().toString());
        service.put("stale_heartbeat_threshold_seconds", config.service().staleHeartbeatThresholdSeconds());
        return service;
    }

    private Map<String, Object> buildJobsSection(Config config) {
        Map<String, Object> jobs = new LinkedHashMap<>();
        jobs.put("default_timeout_seconds", config.jobs().defaultTimeoutSeconds());
        jobs.put("max_retries", config.jobs().maxRetries());
        jobs.put("retry_delay_seconds", config.jobs().retryDelaySeconds());
        jobs.put("retry_backoff_multiplier", config.jobs().retryBackoffMultiplier());
        jobs.put("retry_on_exit_codes", config.jobs().retryOnExitCodes());
        return jobs;
    }

    private Map<String, Object> buildCleanupSection(Config config) {
        Map<String, Object> cleanup = new LinkedHashMap<>();
        cleanup.put("enabled", config.cleanup().enabled());
        cleanup.put("max_age_hours", config.cleanup().maxAgeHours());
        cleanup.put("cleanup_interval_minutes", config.cleanup().cleanupIntervalMinutes());
        cleanup.put("keep_failed_jobs", config.cleanup().keepFailedJobs());
        cleanup.put("keep_failed_jobs_hours", config.cleanup().keepFailedJobsHours());
        return cleanup;
    }

    private Map<String, Object> buildStorageSection(Config config) {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("base_dir", config.storage().baseDir().toString());
        storage.put("max_disk_usage_gb", config.storage().maxDiskUsageGb());
        storage.put("preserve_work_dir", config.storage().preserveWorkDir());
        return storage;
    }

    private Map<String, Object> buildLoadSection(Config config) {
        Map<String, Object> load = new LinkedHashMap<>();
        load.put("max_high_jobs", config.loadLevels().maxHighJobs());
        load.put("max_medium_jobs", config.loadLevels().maxMediumJobs());
        load.put("max_low_jobs", config.loadLevels().maxLowJobs());
        return load;
    }
}
