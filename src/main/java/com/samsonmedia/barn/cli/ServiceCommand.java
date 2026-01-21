package com.samsonmedia.barn.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command to manage the Barn service.
 *
 * <p>Provides subcommands for starting, stopping, and monitoring the service.
 */
@Command(
    name = "service",
    mixinStandardHelpOptions = true,
    description = "Manage the Barn service",
    subcommands = {
        ServiceStartCommand.class,
        ServiceStopCommand.class,
        ServiceRestartCommand.class,
        ServiceReloadCommand.class,
        ServiceStatusCommand.class,
        ServiceLogsCommand.class,
        ServiceInstallCommand.class,
        ServiceUninstallCommand.class
    }
)
public class ServiceCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
