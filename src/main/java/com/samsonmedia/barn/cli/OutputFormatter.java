package com.samsonmedia.barn.cli;

import java.util.List;

/**
 * Interface for formatting CLI output in different formats.
 *
 * <p>Implementations provide format-specific rendering of command output
 * including jobs, lists, and error messages.
 */
public interface OutputFormatter {

    /**
     * Formats a single object for output.
     *
     * @param value the object to format
     * @return the formatted string
     */
    String format(Object value);

    /**
     * Formats a list of objects for output.
     *
     * @param values the list of values to format
     * @return the formatted string
     */
    String formatList(List<?> values);

    /**
     * Formats an error message.
     *
     * @param message the error message
     * @param cause the optional cause (may be null)
     * @return the formatted error string
     */
    String formatError(String message, Throwable cause);

    /**
     * Creates a formatter for the specified output format.
     *
     * @param format the output format
     * @return the appropriate formatter instance
     */
    static OutputFormatter forFormat(OutputFormat format) {
        return switch (format) {
            case HUMAN -> new HumanFormatter();
            case JSON -> new JsonFormatter();
            case XML -> new XmlFormatter();
        };
    }
}
