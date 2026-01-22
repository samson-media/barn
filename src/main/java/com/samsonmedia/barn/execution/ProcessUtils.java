package com.samsonmedia.barn.execution;

import java.util.Optional;

import com.samsonmedia.barn.logging.BarnLogger;

/**
 * Cross-platform process utilities.
 *
 * <p>Provides utility methods for process management that work consistently
 * across Windows, macOS, and Linux.
 */
public final class ProcessUtils {

    private static final BarnLogger LOG = BarnLogger.getLogger(ProcessUtils.class);

    private ProcessUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if a process with the given PID is alive.
     *
     * @param pid the process ID to check
     * @return true if the process is running, false otherwise
     */
    public static boolean isAlive(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        return handle.isPresent() && handle.get().isAlive();
    }

    /**
     * Kills a process and all its descendants.
     *
     * <p>This method attempts to gracefully terminate the process tree first,
     * then forcefully kills any remaining processes.
     *
     * @param pid the process ID to kill
     * @return true if the process was found and killed, false if not found
     */
    public static boolean killTree(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            LOG.debug("Process {} not found", pid);
            return false;
        }

        ProcessHandle process = handle.get();

        // Kill descendants first (depth-first)
        process.descendants().forEach(descendant -> {
            LOG.debug("Killing descendant process: {}", descendant.pid());
            descendant.destroy();
        });

        // Kill the main process
        LOG.debug("Killing process: {}", pid);
        boolean destroyed = process.destroy();

        if (!destroyed) {
            LOG.debug("Graceful kill failed, forcing: {}", pid);
            destroyed = process.destroyForcibly();
        }

        return destroyed;
    }

    /**
     * Forcefully kills a process and all its descendants.
     *
     * <p>This method immediately force-kills without attempting graceful shutdown.
     *
     * @param pid the process ID to kill
     * @return true if the process was found and killed, false if not found
     */
    public static boolean killTreeForcibly(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            LOG.debug("Process {} not found", pid);
            return false;
        }

        ProcessHandle process = handle.get();

        // Kill descendants first
        process.descendants().forEach(descendant -> {
            LOG.debug("Force killing descendant process: {}", descendant.pid());
            descendant.destroyForcibly();
        });

        // Force kill the main process
        LOG.debug("Force killing process: {}", pid);
        return process.destroyForcibly();
    }

    /**
     * Waits for a process to terminate.
     *
     * @param pid the process ID to wait for
     * @return the process handle's onExit future, or empty if not found
     */
    public static Optional<ProcessHandle> getHandle(long pid) {
        return ProcessHandle.of(pid);
    }

    /**
     * Gets the exit code of a terminated process.
     *
     * <p>Note: Exit codes are only available for direct child processes.
     * For processes started via ProcessBuilder, use the Process object's exitValue().
     *
     * @param process the Process object
     * @return the exit code if available
     */
    public static Optional<Integer> getExitCode(Process process) {
        if (process.isAlive()) {
            return Optional.empty();
        }
        return Optional.of(process.exitValue());
    }

    /**
     * Gets the current process's PID.
     *
     * @return the current process ID
     */
    public static long getCurrentPid() {
        return ProcessHandle.current().pid();
    }
}
