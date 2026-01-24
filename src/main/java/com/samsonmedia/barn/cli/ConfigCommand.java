package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command for configuration management.
 *
 * <p>Provides subcommands for viewing, validating, and initializing configuration.
 */
@Command(
    name = "config",
    mixinStandardHelpOptions = true,
    description = "Configuration management",
    subcommands = {
        ConfigShowCommand.class,
        ConfigValidateCommand.class,
        ConfigInitCommand.class,
        ConfigInstallCommand.class
    }
)
public class ConfigCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
