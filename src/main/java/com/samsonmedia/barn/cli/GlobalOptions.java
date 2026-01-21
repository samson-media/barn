package com.samsonmedia.barn.cli;

import java.nio.file.Path;
import java.util.Optional;

import picocli.CommandLine.Option;

/**
 * Global options shared by all CLI commands.
 *
 * <p>This class is used as a picocli mixin to provide common options
 * like output format, config file path, and offline mode.
 */
public class GlobalOptions {

    @Option(
        names = {"-o", "--output"},
        description = "Output format: human, json, xml",
        defaultValue = "HUMAN"
    )
    private OutputFormat outputFormat = OutputFormat.HUMAN;

    @Option(
        names = {"--config"},
        description = "Path to config file"
    )
    private Path configPath;

    @Option(
        names = {"--offline"},
        description = "Run without service connection (for testing)"
    )
    private boolean offline;

    /**
     * Gets the output format.
     *
     * @return the output format
     */
    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * Gets the optional config file path.
     *
     * @return the config path if specified, or empty
     */
    public Optional<Path> getConfigPath() {
        return Optional.ofNullable(configPath);
    }

    /**
     * Checks if offline mode is enabled.
     *
     * @return true if running in offline mode
     */
    public boolean isOffline() {
        return offline;
    }
}
