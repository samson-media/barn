package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command to remove completed jobs.
 *
 * <p>Placeholder implementation - full implementation in ticket #13.
 */
@Command(
    name = "clean",
    mixinStandardHelpOptions = true,
    description = "Remove completed jobs"
)
public class CleanCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("Clean command not yet implemented");
    }
}
