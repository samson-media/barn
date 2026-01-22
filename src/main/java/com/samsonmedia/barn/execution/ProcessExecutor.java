package com.samsonmedia.barn.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.samsonmedia.barn.logging.BarnLogger;

/**
 * Executes processes and captures their output.
 *
 * <p>This class provides cross-platform process execution with proper output
 * capture to log files.
 */
public class ProcessExecutor {

    private static final BarnLogger LOG = BarnLogger.getLogger(ProcessExecutor.class);

    /**
     * Executes a command and returns the process.
     *
     * <p>The process output is redirected to the specified log files.
     * Output is appended if the files already exist.
     *
     * @param command the command and arguments to execute
     * @param workingDir the working directory for the process
     * @param stdoutFile the file to write stdout to
     * @param stderrFile the file to write stderr to
     * @param environment additional environment variables (may be null)
     * @return the started Process
     * @throws IOException if the process cannot be started
     */
    public Process execute(
            List<String> command,
            Path workingDir,
            Path stdoutFile,
            Path stderrFile,
            Map<String, String> environment) throws IOException {

        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(workingDir, "workingDir must not be null");
        Objects.requireNonNull(stdoutFile, "stdoutFile must not be null");
        Objects.requireNonNull(stderrFile, "stderrFile must not be null");

        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        // Ensure parent directories exist
        Files.createDirectories(stdoutFile.getParent());
        Files.createDirectories(stderrFile.getParent());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutFile.toFile()));
        builder.redirectError(ProcessBuilder.Redirect.appendTo(stderrFile.toFile()));

        if (environment != null && !environment.isEmpty()) {
            builder.environment().putAll(environment);
        }

        LOG.info("Starting process: {}", String.join(" ", command));
        LOG.debug("Working directory: {}", workingDir);
        LOG.debug("Stdout file: {}", stdoutFile);
        LOG.debug("Stderr file: {}", stderrFile);

        Process process = builder.start();
        LOG.info("Process started with PID: {}", process.pid());

        return process;
    }

    /**
     * Executes a command with default settings.
     *
     * @param command the command and arguments to execute
     * @param workingDir the working directory for the process
     * @param stdoutFile the file to write stdout to
     * @param stderrFile the file to write stderr to
     * @return the started Process
     * @throws IOException if the process cannot be started
     */
    public Process execute(
            List<String> command,
            Path workingDir,
            Path stdoutFile,
            Path stderrFile) throws IOException {
        return execute(command, workingDir, stdoutFile, stderrFile, null);
    }

    /**
     * Checks if a process is still running.
     *
     * @param pid the process ID to check
     * @return true if the process is running
     */
    public boolean isRunning(long pid) {
        return ProcessUtils.isAlive(pid);
    }

    /**
     * Kills a process.
     *
     * @param pid the process ID to kill
     * @return true if the process was killed
     */
    public boolean kill(long pid) {
        return ProcessUtils.killTree(pid);
    }

    /**
     * Waits for a process to complete.
     *
     * @param process the process to wait for
     * @param timeout the maximum time to wait
     * @return the exit code if the process completed within the timeout
     * @throws InterruptedException if the wait was interrupted
     */
    public Optional<Integer> waitFor(Process process, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(process, "process must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

        if (completed) {
            return Optional.of(process.exitValue());
        }

        return Optional.empty();
    }

    /**
     * Waits indefinitely for a process to complete.
     *
     * @param process the process to wait for
     * @return the exit code
     * @throws InterruptedException if the wait was interrupted
     */
    public int waitFor(Process process) throws InterruptedException {
        Objects.requireNonNull(process, "process must not be null");
        return process.waitFor();
    }
}
