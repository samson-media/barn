package com.samsonmedia.barn.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ProcessMonitor.
 */
class ProcessMonitorTest {

    private ProcessMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new ProcessMonitor(Duration.ofMillis(100));
    }

    @Nested
    class Monitor {

        @Test
        void monitor_shouldEmitStartedEvent() throws IOException, InterruptedException {
            Process process = startShortProcess();
            List<ProcessMonitor.ProcessEvent> events = new ArrayList<>();
            CountDownLatch startedLatch = new CountDownLatch(1);

            monitor.monitor(process, event -> {
                events.add(event);
                if (event instanceof ProcessMonitor.ProcessEvent.Started) {
                    startedLatch.countDown();
                }
            });

            assertThat(startedLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(events.get(0)).isInstanceOf(ProcessMonitor.ProcessEvent.Started.class);

            process.waitFor();
        }

        @Test
        void monitor_shouldEmitCompletedEvent() throws IOException, InterruptedException {
            Process process = startShortProcess();
            AtomicReference<ProcessMonitor.ProcessEvent.Completed> completedEvent = new AtomicReference<>();
            CountDownLatch completedLatch = new CountDownLatch(1);

            monitor.monitor(process, event -> {
                if (event instanceof ProcessMonitor.ProcessEvent.Completed completed) {
                    completedEvent.set(completed);
                    completedLatch.countDown();
                }
            });

            assertThat(completedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(completedEvent.get()).isNotNull();
            assertThat(completedEvent.get().exitCode()).isZero();
            assertThat(completedEvent.get().isSuccess()).isTrue();
        }

        @Test
        void monitor_withLongRunningProcess_shouldEmitHeartbeats() throws IOException, InterruptedException {
            Process process = startSleepProcess(2);
            List<ProcessMonitor.ProcessEvent.Heartbeat> heartbeats = new ArrayList<>();
            CountDownLatch heartbeatLatch = new CountDownLatch(2);

            monitor.monitor(process, event -> {
                if (event instanceof ProcessMonitor.ProcessEvent.Heartbeat hb) {
                    heartbeats.add(hb);
                    heartbeatLatch.countDown();
                }
            });

            try {
                assertThat(heartbeatLatch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(heartbeats.size()).isGreaterThanOrEqualTo(2);
            } finally {
                process.destroyForcibly();
                process.waitFor();
            }
        }

        @Test
        void monitor_shouldReturnFuture() throws IOException, InterruptedException {
            Process process = startShortProcess();

            CompletableFuture<ProcessMonitor.ProcessEvent.Completed> future =
                monitor.monitor(process, event -> { });

            ProcessMonitor.ProcessEvent.Completed completed = future.join();
            assertThat(completed.exitCode()).isZero();
        }

        @Test
        void monitor_withNullProcess_shouldThrowException() {
            assertThatThrownBy(() -> monitor.monitor(null, event -> { }))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void monitor_withNullHandler_shouldThrowException() throws IOException {
            Process process = startShortProcess();
            try {
                assertThatThrownBy(() -> monitor.monitor(process, null))
                    .isInstanceOf(NullPointerException.class);
            } finally {
                process.destroyForcibly();
            }
        }
    }

    @Nested
    class MonitorBlocking {

        @Test
        void monitorBlocking_shouldReturnCompletedEvent() throws IOException, InterruptedException {
            Process process = startShortProcess();

            ProcessMonitor.ProcessEvent.Completed completed = monitor.monitorBlocking(process, event -> { });

            assertThat(completed).isNotNull();
            assertThat(completed.exitCode()).isZero();
        }

        @Test
        void monitorBlocking_shouldReceiveAllEvents() throws IOException, InterruptedException {
            Process process = startShortProcess();
            List<ProcessMonitor.ProcessEvent> events = new ArrayList<>();

            monitor.monitorBlocking(process, events::add);

            assertThat(events).isNotEmpty();
            assertThat(events.get(0)).isInstanceOf(ProcessMonitor.ProcessEvent.Started.class);
            assertThat(events.get(events.size() - 1)).isInstanceOf(ProcessMonitor.ProcessEvent.Completed.class);
        }
    }

    @Nested
    class ProcessEventTypes {

        @Test
        void started_shouldContainPidAndTimestamp() throws IOException, InterruptedException {
            Process process = startShortProcess();
            AtomicReference<ProcessMonitor.ProcessEvent.Started> startedEvent = new AtomicReference<>();

            monitor.monitorBlocking(process, event -> {
                if (event instanceof ProcessMonitor.ProcessEvent.Started started) {
                    startedEvent.set(started);
                }
            });

            assertThat(startedEvent.get()).isNotNull();
            assertThat(startedEvent.get().pid()).isGreaterThan(0);
            assertThat(startedEvent.get().timestamp()).isNotNull();
        }

        @Test
        void completed_isSuccess_withZeroExitCode_shouldReturnTrue() {
            var completed = new ProcessMonitor.ProcessEvent.Completed(1234, 0, java.time.Instant.now());

            assertThat(completed.isSuccess()).isTrue();
        }

        @Test
        void completed_isSuccess_withNonZeroExitCode_shouldReturnFalse() {
            var completed = new ProcessMonitor.ProcessEvent.Completed(1234, 1, java.time.Instant.now());

            assertThat(completed.isSuccess()).isFalse();
        }
    }

    @Nested
    class EventHandlerErrors {

        @Test
        void monitor_withThrowingHandler_shouldContinue() throws IOException, InterruptedException {
            Process process = startShortProcess();
            AtomicReference<ProcessMonitor.ProcessEvent.Completed> completedEvent = new AtomicReference<>();

            monitor.monitorBlocking(process, event -> {
                if (event instanceof ProcessMonitor.ProcessEvent.Started) {
                    throw new RuntimeException("Handler error");
                }
                if (event instanceof ProcessMonitor.ProcessEvent.Completed completed) {
                    completedEvent.set(completed);
                }
            });

            // Should still receive completed event despite handler error
            assertThat(completedEvent.get()).isNotNull();
        }
    }

    // Helper methods

    private Process startShortProcess() throws IOException {
        if (isWindows()) {
            return new ProcessBuilder("cmd", "/c", "echo", "test").start();
        }
        return new ProcessBuilder("echo", "test").start();
    }

    private Process startSleepProcess(int seconds) throws IOException {
        if (isWindows()) {
            return new ProcessBuilder("cmd", "/c", "timeout", "/t", String.valueOf(seconds), "/nobreak").start();
        }
        return new ProcessBuilder("sleep", String.valueOf(seconds)).start();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
