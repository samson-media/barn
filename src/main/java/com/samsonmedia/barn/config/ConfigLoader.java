package com.samsonmedia.barn.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.samsonmedia.barn.logging.BarnLogger;

/**
 * Loads and validates Barn configuration from files and environment variables.
 *
 * <p>Configuration is loaded in the following priority order (later overrides earlier):
 * <ol>
 *   <li>Default values</li>
 *   <li>System config file (e.g., /etc/barn/barn.conf)</li>
 *   <li>User config file (e.g., ~/.config/barn/barn.conf)</li>
 *   <li>Explicit config file (via --config flag)</li>
 *   <li>Environment variables (BARN_SECTION_KEY format)</li>
 * </ol>
 */
public final class ConfigLoader {

    private static final BarnLogger LOG = BarnLogger.getLogger(ConfigLoader.class);
    private static final String ENV_PREFIX = "BARN_";

    private final Map<String, String> environment;

    /**
     * Creates a ConfigLoader using the system environment.
     */
    public ConfigLoader() {
        this(System.getenv());
    }

    /**
     * Creates a ConfigLoader with a custom environment (for testing).
     *
     * @param environment the environment variables to use
     */
    public ConfigLoader(Map<String, String> environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    /**
     * Loads configuration using default search paths.
     *
     * @return the loaded configuration
     * @throws ConfigException if configuration is invalid
     */
    public Config load() {
        return load(Optional.empty());
    }

    /**
     * Static convenience method to load configuration from a file.
     *
     * @param path the path to the config file
     * @return the loaded configuration
     * @throws ConfigException if the file cannot be read or is invalid
     */
    public static Config load(Path path) {
        return new ConfigLoader().loadFromFile(path);
    }

    /**
     * Loads configuration, optionally with an explicit config file.
     *
     * @param explicitPath optional explicit config file path
     * @return the loaded configuration
     * @throws ConfigException if configuration is invalid
     */
    public Config load(Optional<Path> explicitPath) {
        Objects.requireNonNull(explicitPath, "explicitPath must not be null");

        Map<String, Map<String, Object>> merged = new HashMap<>();

        // Load from search paths
        for (Path path : ConfigDefaults.getConfigSearchPaths()) {
            loadIfExists(path).ifPresent(data -> mergeInto(merged, data));
        }

        // Load from explicit path if provided
        explicitPath.flatMap(this::loadIfExists).ifPresent(data -> mergeInto(merged, data));

        // Apply environment overrides
        applyEnvironmentOverrides(merged);

        return buildConfig(merged);
    }

    /**
     * Loads configuration from a specific file.
     *
     * @param path the path to the config file
     * @return the loaded configuration
     * @throws ConfigException if the file cannot be read or is invalid
     */
    public Config loadFromFile(Path path) {
        Objects.requireNonNull(path, "path must not be null");

        Map<String, Map<String, Object>> data = loadFile(path);
        applyEnvironmentOverrides(data);
        return buildConfig(data);
    }

    private Optional<Map<String, Map<String, Object>>> loadIfExists(Path path) {
        if (!Files.exists(path)) {
            LOG.debug("Config file not found: {}", path);
            return Optional.empty();
        }
        try {
            return Optional.of(loadFile(path));
        } catch (ConfigException e) {
            LOG.warn("Failed to load config file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Map<String, Object>> loadFile(Path path) {
        LOG.debug("Loading config from: {}", path);
        try {
            String content = Files.readString(path);
            return TomlParser.parse(content);
        } catch (IOException e) {
            throw new ConfigException("Failed to read config file: " + path, e);
        } catch (TomlParser.TomlParseException e) {
            throw new ConfigException("Failed to parse config file: " + path + " - " + e.getMessage(), e);
        }
    }

    private void mergeInto(Map<String, Map<String, Object>> target,
                          Map<String, Map<String, Object>> source) {
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                  .putAll(entry.getValue());
        }
    }

    private void applyEnvironmentOverrides(Map<String, Map<String, Object>> data) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (!key.startsWith(ENV_PREFIX) || key.equals(ENV_PREFIX)) {
                continue;
            }

            String remainder = key.substring(ENV_PREFIX.length());
            int underscoreIndex = remainder.indexOf('_');
            if (underscoreIndex <= 0) {
                continue;
            }

            String section = remainder.substring(0, underscoreIndex).toLowerCase(Locale.ROOT);
            String configKey = remainder.substring(underscoreIndex + 1).toLowerCase(Locale.ROOT);

            LOG.debug("Applying environment override: {}={}", key, value);
            data.computeIfAbsent(section, k -> new HashMap<>())
                .put(configKey, parseEnvValue(value));
        }
    }

    private Object parseEnvValue(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Not a double
        }
        return value;
    }

    private Config buildConfig(Map<String, Map<String, Object>> data) {
        // Merge both "load_levels" (from TOML) and "loadlevels" (from env vars)
        // Env vars (loadlevels) take precedence over config file (load_levels)
        Map<String, Object> loadLevelData = new HashMap<>(data.getOrDefault("load_levels", Map.of()));
        loadLevelData.putAll(data.getOrDefault("loadlevels", Map.of()));

        return new Config(
            buildServiceConfig(data.getOrDefault("service", Map.of())),
            buildJobsConfig(data.getOrDefault("jobs", Map.of())),
            buildCleanupConfig(data.getOrDefault("cleanup", Map.of())),
            buildStorageConfig(data.getOrDefault("storage", Map.of())),
            buildLoadLevelConfig(loadLevelData)
        );
    }

    private ServiceConfig buildServiceConfig(Map<String, Object> data) {
        return new ServiceConfig(
            getOrDefault(data, "log_level", ServiceConfig.DEFAULT_LOG_LEVEL,
                v -> LogLevel.fromString(v.toString())),
            getOrDefault(data, "max_concurrent_jobs", ServiceConfig.DEFAULT_MAX_CONCURRENT_JOBS,
                this::toInt),
            getOrDefault(data, "heartbeat_interval_seconds", ServiceConfig.DEFAULT_HEARTBEAT_INTERVAL_SECONDS,
                this::toInt),
            getOrDefault(data, "ipc_socket", ConfigDefaults.getDefaultIpcSocket(),
                v -> Path.of(v.toString())),
            getOrDefault(data, "stale_heartbeat_threshold_seconds",
                ServiceConfig.DEFAULT_STALE_HEARTBEAT_THRESHOLD_SECONDS, this::toInt)
        );
    }

    private JobsConfig buildJobsConfig(Map<String, Object> data) {
        return new JobsConfig(
            getOrDefault(data, "default_timeout_seconds", JobsConfig.DEFAULT_TIMEOUT_SECONDS,
                this::toInt),
            getOrDefault(data, "max_retries", JobsConfig.DEFAULT_MAX_RETRIES,
                this::toInt),
            getOrDefault(data, "retry_delay_seconds", JobsConfig.DEFAULT_RETRY_DELAY_SECONDS,
                this::toInt),
            getOrDefault(data, "retry_backoff_multiplier", JobsConfig.DEFAULT_RETRY_BACKOFF_MULTIPLIER,
                this::toDouble),
            getOrDefault(data, "retry_on_exit_codes", List.of(),
                this::toIntList)
        );
    }

    private CleanupConfig buildCleanupConfig(Map<String, Object> data) {
        return new CleanupConfig(
            getOrDefault(data, "enabled", CleanupConfig.DEFAULT_ENABLED,
                this::toBoolean),
            getOrDefault(data, "max_age_hours", CleanupConfig.DEFAULT_MAX_AGE_HOURS,
                this::toInt),
            getOrDefault(data, "cleanup_interval_minutes", CleanupConfig.DEFAULT_CLEANUP_INTERVAL_MINUTES,
                this::toInt),
            getOrDefault(data, "keep_failed_jobs", CleanupConfig.DEFAULT_KEEP_FAILED_JOBS,
                this::toBoolean),
            getOrDefault(data, "keep_failed_jobs_hours", CleanupConfig.DEFAULT_KEEP_FAILED_JOBS_HOURS,
                this::toInt)
        );
    }

    private StorageConfig buildStorageConfig(Map<String, Object> data) {
        return new StorageConfig(
            getOrDefault(data, "base_dir", ConfigDefaults.getDefaultBaseDir(),
                v -> Path.of(v.toString())),
            getOrDefault(data, "max_disk_usage_gb", StorageConfig.DEFAULT_MAX_DISK_USAGE_GB,
                this::toInt),
            getOrDefault(data, "preserve_work_dir", StorageConfig.DEFAULT_PRESERVE_WORK_DIR,
                this::toBoolean)
        );
    }

    private LoadLevelConfig buildLoadLevelConfig(Map<String, Object> data) {
        return new LoadLevelConfig(
            getOrDefault(data, "max_high_jobs", LoadLevelConfig.DEFAULT_MAX_HIGH_JOBS,
                this::toInt),
            getOrDefault(data, "max_medium_jobs", LoadLevelConfig.DEFAULT_MAX_MEDIUM_JOBS,
                this::toInt),
            getOrDefault(data, "max_low_jobs", LoadLevelConfig.DEFAULT_MAX_LOW_JOBS,
                this::toInt)
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrDefault(Map<String, Object> data, String key, T defaultValue,
                               Function<Object, T> converter) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            if (defaultValue != null && defaultValue.getClass().isInstance(value)) {
                return (T) value;
            }
            return converter.apply(value);
        } catch (Exception e) {
            throw new ConfigException("Invalid value for '" + key + "': " + value, e);
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

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<Integer> toIntList(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Integer> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(toInt(item));
            }
            return result;
        }
        throw new ConfigException("Expected list but got: " + value.getClass().getSimpleName());
    }

    /**
     * Exception thrown when configuration loading or validation fails.
     */
    public static class ConfigException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new ConfigException.
         *
         * @param message the error message
         */
        public ConfigException(String message) {
            super(message);
        }

        /**
         * Creates a new ConfigException with a cause.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
