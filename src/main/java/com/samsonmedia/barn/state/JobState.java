package com.samsonmedia.barn.state;

import java.util.Locale;

/**
 * Represents the current state of a job in its lifecycle.
 */
public enum JobState {
    /** Job is waiting to be executed. */
    QUEUED,

    /** Job is currently running. */
    RUNNING,

    /** Job completed successfully. */
    SUCCEEDED,

    /** Job failed to complete. */
    FAILED,

    /** Job was manually canceled. */
    CANCELED,

    /** Job was killed when the service was terminated unexpectedly. */
    KILLED;

    /**
     * Parses a job state from a string (case-insensitive).
     *
     * @param value the string value to parse
     * @return the parsed JobState
     * @throws IllegalArgumentException if the value is not a valid state
     */
    public static JobState fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Job state cannot be null or blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid job state: '" + value
                + "'. Valid values are: queued, running, succeeded, failed, canceled, killed");
        }
    }

    /**
     * Returns the lowercase string representation.
     *
     * @return the state name in lowercase
     */
    public String toLowercase() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Checks if this is a terminal state (job is finished).
     *
     * @return true if the job is in a terminal state
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == KILLED;
    }

    /**
     * Checks if this is an active state (job is not finished).
     *
     * @return true if the job is active
     */
    public boolean isActive() {
        return !isTerminal();
    }
}
