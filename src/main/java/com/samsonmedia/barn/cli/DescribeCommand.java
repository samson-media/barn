package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command to show detailed information about a specific job.
 *
 * <p>Placeholder implementation - full implementation in ticket #11.
 */
@Command(
    name = "describe",
    mixinStandardHelpOptions = true,
    description = "Show detailed job information"
)
public class DescribeCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("Describe command not yet implemented");
    }
}
