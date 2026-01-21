package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command to stop a running job.
 *
 * <p>Placeholder implementation - full implementation in ticket #12.
 */
@Command(
    name = "kill",
    mixinStandardHelpOptions = true,
    description = "Stop a running job"
)
public class KillCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("Kill command not yet implemented");
    }
}
