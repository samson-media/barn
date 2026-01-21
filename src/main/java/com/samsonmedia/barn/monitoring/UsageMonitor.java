package com.samsonmedia.barn.monitoring;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors resource usage for a process over time.
 *
 * <p>Collects usage metrics at regular intervals and logs them to a CSV file.
 */
public class UsageMonitor implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(UsageMonitor.class);

    /** Default collection interval in seconds. */
    public static final int DEFAULT_INTERVAL_SECONDS = 5;

    private final UsageCollector collector;
    private final UsageLogger logger;
    private final long pid;
    private final Path workDir;
    private final int intervalSeconds;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private ScheduledFuture<?> scheduledTask;

    /**
     * Creates a new UsageMonitor.
     *
     * @param pid the process ID to monitor
     * @param workDir the working directory to measure disk usage
     * @param logsDir the directory to write usage logs
     */
    public UsageMonitor(long pid, Path workDir, Path logsDir) {
        this(pid, workDir, logsDir, DEFAULT_INTERVAL_SECONDS);
    }

    /**
     * Creates a new UsageMonitor with custom interval.
     *
     * @param pid the process ID to monitor
     * @param workDir the working directory to measure disk usage
     * @param logsDir the directory to write usage logs
     * @param intervalSeconds collection interval in seconds
     */
    public UsageMonitor(long pid, Path workDir, Path logsDir, int intervalSeconds) {
        this(new UsageCollector(), UsageLogger.forJobLogsDir(logsDir), pid, workDir, intervalSeconds);
    }

    /**
     * Creates a new UsageMonitor with custom components.
     *
     * @param collector the usage collector
     * @param logger the usage logger
     * @param pid the process ID to monitor
     * @param workDir the working directory to measure disk usage
     * @param intervalSeconds collection interval in seconds
     */
    public UsageMonitor(UsageCollector collector, UsageLogger logger, long pid,
                        Path workDir, int intervalSeconds) {
        this.collector = Objects.requireNonNull(collector, "collector must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.workDir = Objects.requireNonNull(workDir, "workDir must not be null");

        if (pid <= 0) {
            throw new IllegalArgumentException("pid must be positive");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }

        this.pid = pid;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "usage-monitor-" + pid);
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }

    /**
     * Starts the usage monitor.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            LOG.debug("Starting usage monitor for PID {} with interval {}s", pid, intervalSeconds);

            // Collect immediately, then at intervals
            collectAndLog();

            scheduledTask = scheduler.scheduleAtFixedRate(
                this::collectAndLog,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
            );
        }
    }

    /**
     * Stops the usage monitor.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOG.debug("Stopping usage monitor for PID {}", pid);

            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }

            // Collect one final sample
            collectAndLog();
        }
    }

    /**
     * Checks if the monitor is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the log file path.
     *
     * @return the log file path
     */
    public Path getLogFile() {
        return logger.getLogFile();
    }

    @Override
    public void close() throws IOException {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.close();
    }

    private void collectAndLog() {
        try {
            UsageRecord record = collector.collect(pid, workDir);
            logger.log(record);
            LOG.trace("Logged usage: CPU={}%, Memory={}, Disk={}",
                String.format("%.1f", record.cpuPercent()),
                UsageRecord.formatBytes(record.memoryBytes()),
                UsageRecord.formatBytes(record.diskBytes()));
        } catch (Exception e) {
            LOG.warn("Failed to collect/log usage for PID {}: {}", pid, e.getMessage());
        }
    }
}
