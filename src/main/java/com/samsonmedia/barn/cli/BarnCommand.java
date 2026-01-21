package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Root command for the Barn CLI.
 *
 * <p>This command serves as the entry point for all barn operations.
 * When invoked without a subcommand, it displays usage information.
 */
@Command(
    name = "barn",
    mixinStandardHelpOptions = true,
    version = "barn 0.1.0-SNAPSHOT",
    description = "Cross-platform job daemon for media processing",
    subcommands = {
        RunCommand.class,
        StatusCommand.class,
        DescribeCommand.class,
        UsageCommand.class,
        KillCommand.class,
        CleanCommand.class,
        ServiceCommand.class,
        ConfigCommand.class,
        AutocompletionCommand.class
    }
)
public class BarnCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        // When no subcommand is specified, show usage
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
