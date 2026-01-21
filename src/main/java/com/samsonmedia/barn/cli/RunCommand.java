package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command to start a new job.
 *
 * <p>Placeholder implementation - full implementation in ticket #9.
 */
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = "Start a new job"
)
public class RunCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("Run command not yet implemented");
    }
}
