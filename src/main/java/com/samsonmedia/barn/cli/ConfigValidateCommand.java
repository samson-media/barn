package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.samsonmedia.barn.config.LogLevel;
import com.samsonmedia.barn.config.TomlParser;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to validate a configuration file.
 */
@Command(
    name = "validate",
    mixinStandardHelpOptions = true,
    description = "Validate configuration file"
)
public class ConfigValidateCommand extends BaseCommand {

    private static final Set<String> KNOWN_SECTIONS = Set.of("service", "jobs", "cleanup", "storage");
    private static final Set<String> SERVICE_KEYS = Set.of(
        "log_level", "max_concurrent_jobs", "heartbeat_interval_seconds",
        "ipc_socket", "stale_heartbeat_threshold_seconds"
    );
    private static final Set<String> JOBS_KEYS = Set.of(
        "default_timeout_seconds", "max_retries", "retry_delay_seconds",
        "retry_backoff_multiplier", "retry_on_exit_codes"
    );
    private static final Set<String> CLEANUP_KEYS = Set.of(
        "enabled", "max_age_hours", "cleanup_interval_minutes",
        "keep_failed_jobs", "keep_failed_jobs_hours"
    );
    private static final Set<String> STORAGE_KEYS = Set.of(
        "base_dir", "max_disk_usage_gb", "preserve_work_dir"
    );

    @Option(names = {"--file", "-f"}, description = "Config file to validate", required = true)
    private Path configFile;

    @Override
    public Integer call() {
        if (!Files.exists(configFile)) {
            outputError("Configuration file not found: " + configFile);
            return EXIT_ERROR;
        }

        List<ValidationError> errors = validate(configFile);

        if (errors.isEmpty()) {
            getOut().println("Configuration is valid: " + configFile);
            return EXIT_SUCCESS;
        }

        OutputFormat format = globalOptions != null
            ? globalOptions.getOutputFormat()
            : OutputFormat.HUMAN;

        switch (format) {
            case HUMAN -> outputHumanFormat(errors);
            case JSON, XML -> output(toMap(errors));
            default -> outputHumanFormat(errors);
        }

        return EXIT_ERROR;
    }

    private List<ValidationError> validate(Path path) {
        List<ValidationError> errors = new ArrayList<>();

        try {
            String content = Files.readString(path);
            Map<String, Map<String, Object>> data = TomlParser.parse(content);

            // Check for unknown sections
            for (String section : data.keySet()) {
                if (!KNOWN_SECTIONS.contains(section)) {
                    errors.add(new ValidationError(0, section, "Unknown section: [" + section + "]"));
                }
            }

            // Validate service section
            if (data.containsKey("service")) {
                validateSection(data.get("service"), "service", SERVICE_KEYS, errors);
                validateServiceValues(data.get("service"), errors);
            }

            // Validate jobs section
            if (data.containsKey("jobs")) {
                validateSection(data.get("jobs"), "jobs", JOBS_KEYS, errors);
                validateJobsValues(data.get("jobs"), errors);
            }

            // Validate cleanup section
            if (data.containsKey("cleanup")) {
                validateSection(data.get("cleanup"), "cleanup", CLEANUP_KEYS, errors);
                validateCleanupValues(data.get("cleanup"), errors);
            }

            // Validate storage section
            if (data.containsKey("storage")) {
                validateSection(data.get("storage"), "storage", STORAGE_KEYS, errors);
                validateStorageValues(data.get("storage"), errors);
            }

        } catch (IOException e) {
            errors.add(new ValidationError(0, null, "Failed to read file: " + e.getMessage()));
        } catch (TomlParser.TomlParseException e) {
            errors.add(new ValidationError(0, null, "Parse error: " + e.getMessage()));
        }

        return errors;
    }

    private void validateSection(Map<String, Object> section, String sectionName,
                                  Set<String> knownKeys, List<ValidationError> errors) {
        for (String key : section.keySet()) {
            if (!knownKeys.contains(key)) {
                errors.add(new ValidationError(0, key,
                    "Unknown key '" + key + "' in [" + sectionName + "] section"));
            }
        }
    }

    private void validateServiceValues(Map<String, Object> service, List<ValidationError> errors) {
        if (service.containsKey("log_level")) {
            Object value = service.get("log_level");
            try {
                LogLevel.fromString(value.toString());
            } catch (IllegalArgumentException e) {
                errors.add(new ValidationError(0, "log_level",
                    "Invalid value for 'log_level': must be one of debug, info, warn, error"));
            }
        }

        validatePositiveInteger(service, "max_concurrent_jobs", errors);
        validatePositiveInteger(service, "heartbeat_interval_seconds", errors);
        validatePositiveInteger(service, "stale_heartbeat_threshold_seconds", errors);
    }

    private void validateJobsValues(Map<String, Object> jobs, List<ValidationError> errors) {
        validatePositiveInteger(jobs, "default_timeout_seconds", errors);
        validateNonNegativeInteger(jobs, "max_retries", errors);
        validateNonNegativeInteger(jobs, "retry_delay_seconds", errors);

        if (jobs.containsKey("retry_backoff_multiplier")) {
            Object value = jobs.get("retry_backoff_multiplier");
            try {
                double multiplier = toDouble(value);
                if (multiplier < 1.0) {
                    errors.add(new ValidationError(0, "retry_backoff_multiplier",
                        "Invalid value for 'retry_backoff_multiplier': must be at least 1.0"));
                }
            } catch (NumberFormatException e) {
                errors.add(new ValidationError(0, "retry_backoff_multiplier",
                    "Invalid value for 'retry_backoff_multiplier': must be a number"));
            }
        }

        if (jobs.containsKey("retry_on_exit_codes")) {
            Object value = jobs.get("retry_on_exit_codes");
            if (!(value instanceof List)) {
                errors.add(new ValidationError(0, "retry_on_exit_codes",
                    "Invalid value for 'retry_on_exit_codes': must be a list"));
            }
        }
    }

    private void validateCleanupValues(Map<String, Object> cleanup, List<ValidationError> errors) {
        validateBoolean(cleanup, "enabled", errors);
        validatePositiveInteger(cleanup, "max_age_hours", errors);
        validatePositiveInteger(cleanup, "cleanup_interval_minutes", errors);
        validateBoolean(cleanup, "keep_failed_jobs", errors);
        validatePositiveInteger(cleanup, "keep_failed_jobs_hours", errors);
    }

    private void validateStorageValues(Map<String, Object> storage, List<ValidationError> errors) {
        validatePositiveInteger(storage, "max_disk_usage_gb", errors);
        validateBoolean(storage, "preserve_work_dir", errors);
    }

    private void validatePositiveInteger(Map<String, Object> section, String key,
                                          List<ValidationError> errors) {
        if (section.containsKey(key)) {
            Object value = section.get(key);
            try {
                int intValue = toInt(value);
                if (intValue <= 0) {
                    errors.add(new ValidationError(0, key,
                        "Invalid value for '" + key + "': must be positive integer"));
                }
            } catch (NumberFormatException e) {
                errors.add(new ValidationError(0, key,
                    "Invalid value for '" + key + "': must be positive integer"));
            }
        }
    }

    private void validateNonNegativeInteger(Map<String, Object> section, String key,
                                             List<ValidationError> errors) {
        if (section.containsKey(key)) {
            Object value = section.get(key);
            try {
                int intValue = toInt(value);
                if (intValue < 0) {
                    errors.add(new ValidationError(0, key,
                        "Invalid value for '" + key + "': must be non-negative integer"));
                }
            } catch (NumberFormatException e) {
                errors.add(new ValidationError(0, key,
                    "Invalid value for '" + key + "': must be non-negative integer"));
            }
        }
    }

    private void validateBoolean(Map<String, Object> section, String key,
                                  List<ValidationError> errors) {
        if (section.containsKey(key)) {
            Object value = section.get(key);
            if (!(value instanceof Boolean)) {
                String strValue = value.toString().toLowerCase();
                if (!strValue.equals("true") && !strValue.equals("false")) {
                    errors.add(new ValidationError(0, key,
                        "Invalid value for '" + key + "': must be true or false"));
                }
            }
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private void outputHumanFormat(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration errors in ").append(configFile).append(":\n\n");

        for (ValidationError error : errors) {
            sb.append("  ");
            if (error.key != null) {
                sb.append("Key '").append(error.key).append("': ");
            }
            sb.append(error.message).append("\n");
        }

        getOut().println(sb.toString().trim());
    }

    private Map<String, Object> toMap(List<ValidationError> errors) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("valid", false);
        map.put("file", configFile.toString());

        List<Map<String, Object>> errorList = new ArrayList<>();
        for (ValidationError error : errors) {
            Map<String, Object> errorMap = new LinkedHashMap<>();
            if (error.key != null) {
                errorMap.put("key", error.key);
            }
            errorMap.put("message", error.message);
            errorList.add(errorMap);
        }
        map.put("errors", errorList);

        return map;
    }

    /**
     * Represents a validation error.
     */
    record ValidationError(int line, String key, String message) { }
}
