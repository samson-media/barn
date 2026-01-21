package com.samsonmedia.barn;

import com.samsonmedia.barn.cli.BarnCommand;

import picocli.CommandLine;

/**
 * Main entry point for the Barn application.
 *
 * <p>This class initializes the picocli command line parser and delegates
 * command execution to the appropriate subcommand handlers.
 */
public final class Main {

    private Main() {
        // Utility class - prevent instantiation
    }

    /**
     * Main entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        int exitCode = run(args);
        System.exit(exitCode);
    }

    /**
     * Executes the command line and returns the exit code.
     *
     * <p>This method is separated from {@link #main(String[])} to enable testing
     * without calling {@link System#exit(int)}.
     *
     * @param args command line arguments
     * @return the exit code (0 for success, non-zero for failure)
     */
    public static int run(String[] args) {
        return new CommandLine(new BarnCommand()).execute(args);
    }
}
