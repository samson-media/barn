package com.samsonmedia.barn.monitoring;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import com.samsonmedia.barn.logging.BarnLogger;

/**
 * Writes usage records to a CSV file.
 *
 * <p>Thread-safe logger that appends usage records to a file.
 */
public class UsageLogger implements Closeable {

    private static final BarnLogger LOG = BarnLogger.getLogger(UsageLogger.class);

    /** Default filename for usage logs. */
    public static final String USAGE_LOG_FILENAME = "usage.csv";

    private final Path logFile;
    private final Object writeLock = new Object();
    private BufferedWriter writer;
    private boolean headerWritten;

    /**
     * Creates a new UsageLogger.
     *
     * @param logFile the file to write usage records to
     */
    public UsageLogger(Path logFile) {
        this.logFile = Objects.requireNonNull(logFile, "logFile must not be null");
        this.headerWritten = Files.exists(logFile) && fileHasContent(logFile);
    }

    /**
     * Creates a UsageLogger for a job's logs directory.
     *
     * @param logsDir the job's logs directory
     * @return a new UsageLogger
     */
    public static UsageLogger forJobLogsDir(Path logsDir) {
        return new UsageLogger(logsDir.resolve(USAGE_LOG_FILENAME));
    }

    /**
     * Gets the log file path.
     *
     * @return the log file path
     */
    public Path getLogFile() {
        return logFile;
    }

    /**
     * Logs a usage record.
     *
     * @param record the usage record to log
     * @throws IOException if writing fails
     */
    public void log(UsageRecord record) throws IOException {
        Objects.requireNonNull(record, "record must not be null");

        synchronized (writeLock) {
            ensureWriter();

            if (!headerWritten) {
                writer.write(UsageRecord.csvHeader());
                writer.newLine();
                headerWritten = true;
            }

            writer.write(record.toCsvLine());
            writer.newLine();
            writer.flush();
        }
    }

    /**
     * Flushes any buffered data to the file.
     *
     * @throws IOException if flushing fails
     */
    public void flush() throws IOException {
        synchronized (writeLock) {
            if (writer != null) {
                writer.flush();
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (writeLock) {
            if (writer != null) {
                try {
                    writer.close();
                } finally {
                    writer = null;
                }
            }
        }
    }

    private void ensureWriter() throws IOException {
        if (writer == null) {
            Path parent = logFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            writer = Files.newBufferedWriter(logFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        }
    }

    private boolean fileHasContent(Path file) {
        try {
            return Files.size(file) > 0;
        } catch (IOException e) {
            LOG.trace("Could not check file size: {}", e.getMessage());
            return false;
        }
    }
}
