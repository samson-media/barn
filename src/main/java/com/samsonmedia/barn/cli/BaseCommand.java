package com.samsonmedia.barn.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Base class for all CLI commands.
 *
 * <p>Provides common functionality including global options,
 * output formatting, and error handling.
 */
public abstract class BaseCommand implements Callable<Integer> {

    /** Exit code for successful execution. */
    protected static final int EXIT_SUCCESS = 0;

    /** Exit code for general errors. */
    protected static final int EXIT_ERROR = 1;

    /** Exit code for command-line usage errors. */
    protected static final int EXIT_USAGE = 2;

    @Mixin
    protected GlobalOptions globalOptions;

    @Spec
    protected CommandSpec spec;

    /**
     * Gets the output formatter based on global options.
     *
     * @return the appropriate formatter
     */
    protected OutputFormatter getFormatter() {
        if (globalOptions == null) {
            return OutputFormatter.forFormat(OutputFormat.HUMAN);
        }
        return OutputFormatter.forFormat(globalOptions.getOutputFormat());
    }

    /**
     * Outputs a single value to stdout using the current formatter.
     *
     * @param value the value to output
     */
    protected void output(Object value) {
        getOut().println(getFormatter().format(value));
    }

    /**
     * Outputs a list of values to stdout using the current formatter.
     *
     * @param values the values to output
     */
    protected void outputList(List<?> values) {
        getOut().println(getFormatter().formatList(values));
    }

    /**
     * Outputs an error message to stderr using the current formatter.
     *
     * @param message the error message
     */
    protected void outputError(String message) {
        outputError(message, null);
    }

    /**
     * Outputs an error message with cause to stderr using the current formatter.
     *
     * @param message the error message
     * @param cause the optional cause
     */
    protected void outputError(String message, Throwable cause) {
        getErr().println(getFormatter().formatError(message, cause));
    }

    /**
     * Gets the stdout writer.
     *
     * @return the stdout PrintWriter
     */
    protected PrintWriter getOut() {
        return spec.commandLine().getOut();
    }

    /**
     * Gets the stderr writer.
     *
     * @return the stderr PrintWriter
     */
    protected PrintWriter getErr() {
        return spec.commandLine().getErr();
    }

    /**
     * Checks if running in offline mode.
     *
     * @return true if offline mode is enabled
     */
    protected boolean isOffline() {
        return globalOptions != null && globalOptions.isOffline();
    }
}
