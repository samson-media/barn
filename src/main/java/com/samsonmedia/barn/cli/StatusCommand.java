package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command to show status of all jobs.
 *
 * <p>Placeholder implementation - full implementation in ticket #10.
 */
@Command(
    name = "status",
    mixinStandardHelpOptions = true,
    description = "Show status of all jobs"
)
public class StatusCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("Status command not yet implemented");
    }
}
