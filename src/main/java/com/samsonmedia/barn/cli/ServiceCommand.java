package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command to manage the Barn service.
 *
 * <p>Placeholder implementation - full implementation in ticket #16.
 */
@Command(
    name = "service",
    mixinStandardHelpOptions = true,
    description = "Manage the Barn service"
)
public class ServiceCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
