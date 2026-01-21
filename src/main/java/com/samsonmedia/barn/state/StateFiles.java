package com.samsonmedia.barn.state;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Manages state files for a job directory.
 *
 * <p>State files are plain-text files containing job metadata and lifecycle information.
 * All writes are atomic to ensure consistency.
 */
public final class StateFiles {

    /** State file name. */
    public static final String STATE = "state";

    /** Tag file name. */
    public static final String TAG = "tag";

    /** Created timestamp file name. */
    public static final String CREATED_AT = "created_at";

    /** Started timestamp file name. */
    public static final String STARTED_AT = "started_at";

    /** Finished timestamp file name. */
    public static final String FINISHED_AT = "finished_at";

    /** Exit code file name. */
    public static final String EXIT_CODE = "exit_code";

    /** Error message file name. */
    public static final String ERROR = "error";

    /** Process ID file name. */
    public static final String PID = "pid";

    /** Heartbeat timestamp file name. */
    public static final String HEARTBEAT = "heartbeat";

    /** Retry count file name. */
    public static final String RETRY_COUNT = "retry_count";

    /** Retry timestamp file name. */
    public static final String RETRY_AT = "retry_at";

    /** Retry history file name. */
    public static final String RETRY_HISTORY = "retry_history";

    /** Stage file name. */
    public static final String STAGE = "stage";

    private final Path jobDir;

    /**
     * Creates a StateFiles instance for the specified job directory.
     *
     * @param jobDir the job directory path
     */
    public StateFiles(Path jobDir) {
        this.jobDir = Objects.requireNonNull(jobDir, "jobDir must not be null");
    }

    /**
     * Returns the job directory path.
     *
     * @return the job directory path
     */
    public Path getJobDir() {
        return jobDir;
    }

    // ========================================================================
    // State
    // ========================================================================

    /**
     * Writes the job state.
     *
     * @param state the job state
     * @throws IOException if writing fails
     */
    public void writeState(JobState state) throws IOException {
        Objects.requireNonNull(state, "state must not be null");
        AtomicFiles.writeAtomically(jobDir.resolve(STATE), state.toLowercase());
    }

    /**
     * Reads the job state.
     *
     * @return the job state, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<JobState> readState() throws IOException {
        return AtomicFiles.readSafely(jobDir.resolve(STATE))
            .map(JobState::fromString);
    }

    // ========================================================================
    // Tag
    // ========================================================================

    /**
     * Writes the job tag.
     *
     * @param tag the tag value
     * @throws IOException if writing fails
     */
    public void writeTag(String tag) throws IOException {
        Objects.requireNonNull(tag, "tag must not be null");
        AtomicFiles.writeAtomically(jobDir.resolve(TAG), tag);
    }

    /**
     * Reads the job tag.
     *
     * @return the tag, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<String> readTag() throws IOException {
        return AtomicFiles.readSafely(jobDir.resolve(TAG));
    }

    // ========================================================================
    // Timestamps
    // ========================================================================

    /**
     * Writes the creation timestamp.
     *
     * @param timestamp the timestamp
     * @throws IOException if writing fails
     */
    public void writeCreatedAt(Instant timestamp) throws IOException {
        writeTimestamp(CREATED_AT, timestamp);
    }

    /**
     * Reads the creation timestamp.
     *
     * @return the timestamp, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<Instant> readCreatedAt() throws IOException {
        return readTimestamp(CREATED_AT);
    }

    /**
     * Writes the start timestamp.
     *
     * @param timestamp the timestamp
     * @throws IOException if writing fails
     */
    public void writeStartedAt(Instant timestamp) throws IOException {
        writeTimestamp(STARTED_AT, timestamp);
    }

    /**
     * Reads the start timestamp.
     *
     * @return the timestamp, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<Instant> readStartedAt() throws IOException {
        return readTimestamp(STARTED_AT);
    }

    /**
     * Writes the finished timestamp.
     *
     * @param timestamp the timestamp
     * @throws IOException if writing fails
     */
    public void writeFinishedAt(Instant timestamp) throws IOException {
        writeTimestamp(FINISHED_AT, timestamp);
    }

    /**
     * Reads the finished timestamp.
     *
     * @return the timestamp, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<Instant> readFinishedAt() throws IOException {
        return readTimestamp(FINISHED_AT);
    }

    /**
     * Writes the heartbeat timestamp.
     *
     * @param timestamp the timestamp
     * @throws IOException if writing fails
     */
    public void writeHeartbeat(Instant timestamp) throws IOException {
        writeTimestamp(HEARTBEAT, timestamp);
    }

    /**
     * Reads the heartbeat timestamp.
     *
     * @return the timestamp, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<Instant> readHeartbeat() throws IOException {
        return readTimestamp(HEARTBEAT);
    }

    /**
     * Writes the retry timestamp.
     *
     * @param timestamp the timestamp
     * @throws IOException if writing fails
     */
    public void writeRetryAt(Instant timestamp) throws IOException {
        writeTimestamp(RETRY_AT, timestamp);
    }

    /**
     * Reads the retry timestamp.
     *
     * @return the timestamp, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<Instant> readRetryAt() throws IOException {
        return readTimestamp(RETRY_AT);
    }

    private void writeTimestamp(String filename, Instant timestamp) throws IOException {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        AtomicFiles.writeAtomically(jobDir.resolve(filename), timestamp.toString());
    }

    private Optional<Instant> readTimestamp(String filename) throws IOException {
        return AtomicFiles.readSafely(jobDir.resolve(filename))
            .flatMap(this::parseInstant);
    }

    private Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    // ========================================================================
    // Exit Code
    // ========================================================================

    /**
     * Writes the exit code.
     *
     * @param code the exit code
     * @throws IOException if writing fails
     */
    public void writeExitCode(int code) throws IOException {
        AtomicFiles.writeAtomically(jobDir.resolve(EXIT_CODE), String.valueOf(code));
    }

    /**
     * Writes a symbolic exit code.
     *
     * @param symbolic the symbolic exit code (e.g., "orphaned_process")
     * @throws IOException if writing fails
     */
    public void writeExitCode(String symbolic) throws IOException {
        Objects.requireNonNull(symbolic, "symbolic must not be null");
        AtomicFiles.writeAtomically(jobDir.resolve(EXIT_CODE), symbolic);
    }

    /**
     * Reads the exit code as an integer.
     *
     * @return the exit code, or empty if not found or not an integer
     * @throws IOException if reading fails
     */
    public OptionalInt readExitCode() throws IOException {
        Optional<String> value = AtomicFiles.readSafely(jobDir.resolve(EXIT_CODE));
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.get()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * Reads the exit code as a string (for symbolic codes).
     *
     * @return the exit code string, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<String> readExitCodeString() throws IOException {
        return AtomicFiles.readSafely(jobDir.resolve(EXIT_CODE));
    }

    // ========================================================================
    // Error
    // ========================================================================

    /**
     * Writes the error message.
     *
     * @param error the error message
     * @throws IOException if writing fails
     */
    public void writeError(String error) throws IOException {
        Objects.requireNonNull(error, "error must not be null");
        AtomicFiles.writeAtomically(jobDir.resolve(ERROR), error);
    }

    /**
     * Reads the error message.
     *
     * @return the error message, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<String> readError() throws IOException {
        return AtomicFiles.readSafely(jobDir.resolve(ERROR));
    }

    // ========================================================================
    // PID
    // ========================================================================

    /**
     * Writes the process ID.
     *
     * @param pid the process ID
     * @throws IOException if writing fails
     */
    public void writePid(long pid) throws IOException {
        AtomicFiles.writeAtomically(jobDir.resolve(PID), String.valueOf(pid));
    }

    /**
     * Reads the process ID.
     *
     * @return the process ID, or empty if not found
     * @throws IOException if reading fails
     */
    public OptionalLong readPid() throws IOException {
        Optional<String> value = AtomicFiles.readSafely(jobDir.resolve(PID));
        if (value.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value.get()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    // ========================================================================
    // Retry Count
    // ========================================================================

    /**
     * Writes the retry count.
     *
     * @param count the retry count
     * @throws IOException if writing fails
     */
    public void writeRetryCount(int count) throws IOException {
        AtomicFiles.writeAtomically(jobDir.resolve(RETRY_COUNT), String.valueOf(count));
    }

    /**
     * Reads the retry count.
     *
     * @return the retry count, or empty if not found
     * @throws IOException if reading fails
     */
    public OptionalInt readRetryCount() throws IOException {
        Optional<String> value = AtomicFiles.readSafely(jobDir.resolve(RETRY_COUNT));
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.get()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    // ========================================================================
    // Retry History
    // ========================================================================

    /**
     * Appends an entry to the retry history.
     *
     * @param entry the entry to append
     * @throws IOException if writing fails
     */
    public void appendRetryHistory(String entry) throws IOException {
        Objects.requireNonNull(entry, "entry must not be null");
        Path historyFile = jobDir.resolve(RETRY_HISTORY);
        String existing = AtomicFiles.readOrEmpty(historyFile);
        String newContent = existing.isEmpty() ? entry : existing + "\n" + entry;
        AtomicFiles.writeAtomically(historyFile, newContent);
    }

    /**
     * Reads the retry history.
     *
     * @return the retry history, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<String> readRetryHistory() throws IOException {
        return AtomicFiles.readSafely(jobDir.resolve(RETRY_HISTORY));
    }

    // ========================================================================
    // Stage
    // ========================================================================

    /**
     * Writes the current stage.
     *
     * @param stage the stage name
     * @throws IOException if writing fails
     */
    public void writeStage(String stage) throws IOException {
        Objects.requireNonNull(stage, "stage must not be null");
        AtomicFiles.writeAtomically(jobDir.resolve(STAGE), stage);
    }

    /**
     * Reads the current stage.
     *
     * @return the stage, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<String> readStage() throws IOException {
        return AtomicFiles.readSafely(jobDir.resolve(STAGE));
    }
}
