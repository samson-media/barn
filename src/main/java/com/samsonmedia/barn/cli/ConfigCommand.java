package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command for configuration management.
 *
 * <p>Placeholder implementation - full implementation in ticket #21.
 */
@Command(
    name = "config",
    mixinStandardHelpOptions = true,
    description = "Configuration management"
)
public class ConfigCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
