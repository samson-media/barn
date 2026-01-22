package com.samsonmedia.barn.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.samsonmedia.barn.logging.BarnLogger;

/**
 * Monitors a running process and emits events.
 *
 * <p>This class provides asynchronous monitoring of process execution with
 * event callbacks for process lifecycle events.
 */
public class ProcessMonitor {

    private static final BarnLogger LOG = BarnLogger.getLogger(ProcessMonitor.class);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(10);

    private final Duration heartbeatInterval;

    /**
     * Creates a ProcessMonitor with the default heartbeat interval.
     */
    public ProcessMonitor() {
        this(DEFAULT_HEARTBEAT_INTERVAL);
    }

    /**
     * Creates a ProcessMonitor with a custom heartbeat interval.
     *
     * @param heartbeatInterval the interval between heartbeat events
     */
    public ProcessMonitor(Duration heartbeatInterval) {
        this.heartbeatInterval = Objects.requireNonNull(heartbeatInterval, "heartbeatInterval must not be null");
    }

    /**
     * Monitors a process asynchronously.
     *
     * <p>The monitor will emit events as the process executes:
     * <ul>
     *   <li>Started - when monitoring begins</li>
     *   <li>Heartbeat - periodically while running</li>
     *   <li>Completed - when the process exits normally</li>
     *   <li>Failed - if an error occurs</li>
     * </ul>
     *
     * @param process the process to monitor
     * @param eventHandler the handler to receive events
     * @return a future that completes when monitoring is finished
     */
    public CompletableFuture<ProcessEvent.Completed> monitor(
            Process process,
            Consumer<ProcessEvent> eventHandler) {

        Objects.requireNonNull(process, "process must not be null");
        Objects.requireNonNull(eventHandler, "eventHandler must not be null");

        CompletableFuture<ProcessEvent.Completed> result = new CompletableFuture<>();

        // Emit started event
        long pid = process.pid();
        emitEvent(eventHandler, new ProcessEvent.Started(pid, Instant.now()));

        // Schedule heartbeat events
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "process-monitor-" + pid);
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            () -> {
                if (process.isAlive()) {
                    emitEvent(eventHandler, new ProcessEvent.Heartbeat(pid, Instant.now()));
                }
            },
            heartbeatInterval.toMillis(),
            heartbeatInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );

        // Wait for process completion in background
        process.onExit().whenComplete((p, error) -> {
            scheduler.shutdown();

            if (error != null) {
                ProcessEvent.Failed failed = new ProcessEvent.Failed(pid, error, Instant.now());
                emitEvent(eventHandler, failed);
                result.completeExceptionally(error);
            } else {
                int exitCode = p.exitValue();
                ProcessEvent.Completed completed = new ProcessEvent.Completed(pid, exitCode, Instant.now());
                emitEvent(eventHandler, completed);
                result.complete(completed);
            }
        });

        return result;
    }

    /**
     * Monitors a process synchronously (blocking).
     *
     * @param process the process to monitor
     * @param eventHandler the handler to receive events
     * @return the completion event
     * @throws InterruptedException if waiting was interrupted
     */
    public ProcessEvent.Completed monitorBlocking(
            Process process,
            Consumer<ProcessEvent> eventHandler) throws InterruptedException {

        try {
            return monitor(process, eventHandler).get();
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("Process monitoring failed", e.getCause());
        }
    }

    private void emitEvent(Consumer<ProcessEvent> handler, ProcessEvent event) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            LOG.error("Error in event handler for {}: {}", event.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Process lifecycle events.
     */
    public sealed interface ProcessEvent {

        /**
         * Emitted when process monitoring starts.
         *
         * @param pid the process ID
         * @param timestamp when the process started
         */
        record Started(long pid, Instant timestamp) implements ProcessEvent { }

        /**
         * Emitted periodically while the process is running.
         *
         * @param pid the process ID
         * @param timestamp when the heartbeat was emitted
         */
        record Heartbeat(long pid, Instant timestamp) implements ProcessEvent { }

        /**
         * Emitted when the process completes.
         *
         * @param pid the process ID
         * @param exitCode the process exit code
         * @param timestamp when the process completed
         */
        record Completed(long pid, int exitCode, Instant timestamp) implements ProcessEvent {
            /**
             * Returns true if the process completed successfully (exit code 0).
             *
             * @return true if exit code is 0
             */
            public boolean isSuccess() {
                return exitCode == 0;
            }
        }

        /**
         * Emitted when process monitoring encounters an error.
         *
         * @param pid the process ID
         * @param error the error that occurred
         * @param timestamp when the error occurred
         */
        record Failed(long pid, Throwable error, Instant timestamp) implements ProcessEvent { }
    }

    /**
     * Output type for process output events.
     */
    public enum OutputType {
        STDOUT,
        STDERR
    }
}
