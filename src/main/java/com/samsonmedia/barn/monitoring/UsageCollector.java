package com.samsonmedia.barn.monitoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects resource usage metrics for a process.
 *
 * <p>Uses OS-specific commands to gather CPU, memory, disk, and GPU usage.
 */
public class UsageCollector {

    private static final Logger LOG = LoggerFactory.getLogger(UsageCollector.class);
    private static final int COMMAND_TIMEOUT_SECONDS = 5;

    private final boolean isWindows;
    private final boolean isMac;
    private final boolean isLinux;

    /**
     * Creates a new UsageCollector.
     */
    public UsageCollector() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        this.isWindows = os.contains("win");
        this.isMac = os.contains("mac");
        this.isLinux = os.contains("linux");
    }

    /**
     * Collects usage metrics for a process.
     *
     * @param pid the process ID to monitor
     * @param workDir the working directory to measure disk usage
     * @return the collected usage record
     */
    public UsageRecord collect(long pid, Path workDir) {
        Objects.requireNonNull(workDir, "workDir must not be null");

        Instant timestamp = Instant.now();
        double cpuPercent = collectCpuUsage(pid);
        long memoryBytes = collectMemoryUsage(pid);
        long diskBytes = collectDiskUsage(workDir);
        Double gpuPercent = collectGpuUsage();
        Long gpuMemoryBytes = collectGpuMemory();

        return new UsageRecord(timestamp, cpuPercent, memoryBytes, diskBytes, gpuPercent, gpuMemoryBytes);
    }

    /**
     * Collects CPU usage percentage for a process.
     *
     * @param pid the process ID
     * @return CPU usage as a percentage (0-100+)
     */
    double collectCpuUsage(long pid) {
        try {
            if (isWindows) {
                return collectCpuWindows(pid);
            } else if (isMac) {
                return collectCpuMac(pid);
            } else if (isLinux) {
                return collectCpuLinux(pid);
            }
        } catch (Exception e) {
            LOG.debug("Failed to collect CPU usage for PID {}: {}", pid, e.getMessage());
        }
        return 0.0;
    }

    /**
     * Collects memory usage for a process.
     *
     * @param pid the process ID
     * @return memory usage in bytes
     */
    long collectMemoryUsage(long pid) {
        try {
            if (isWindows) {
                return collectMemoryWindows(pid);
            } else if (isMac) {
                return collectMemoryMac(pid);
            } else if (isLinux) {
                return collectMemoryLinux(pid);
            }
        } catch (Exception e) {
            LOG.debug("Failed to collect memory usage for PID {}: {}", pid, e.getMessage());
        }
        return 0L;
    }

    /**
     * Collects disk usage for a directory.
     *
     * @param path the directory path
     * @return disk usage in bytes
     */
    long collectDiskUsage(Path path) {
        if (!Files.exists(path)) {
            return 0L;
        }

        try {
            return calculateDirectorySize(path);
        } catch (Exception e) {
            LOG.debug("Failed to collect disk usage for {}: {}", path, e.getMessage());
            return 0L;
        }
    }

    /**
     * Collects GPU usage percentage.
     *
     * @return GPU usage as a percentage, or null if not available
     */
    Double collectGpuUsage() {
        try {
            Optional<String> result = runCommand("nvidia-smi", "--query-gpu=utilization.gpu",
                "--format=csv,noheader,nounits");
            if (result.isPresent()) {
                return Double.parseDouble(result.get().trim());
            }
        } catch (Exception e) {
            LOG.trace("GPU usage not available: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Collects GPU memory usage.
     *
     * @return GPU memory usage in bytes, or null if not available
     */
    Long collectGpuMemory() {
        try {
            Optional<String> result = runCommand("nvidia-smi", "--query-gpu=memory.used",
                "--format=csv,noheader,nounits");
            if (result.isPresent()) {
                // nvidia-smi returns MiB
                long mib = Long.parseLong(result.get().trim());
                return mib * 1024 * 1024;
            }
        } catch (Exception e) {
            LOG.trace("GPU memory not available: {}", e.getMessage());
        }
        return null;
    }

    private double collectCpuWindows(long pid) throws IOException, InterruptedException {
        // Use WMIC to get CPU usage
        Optional<String> result = runCommand("wmic", "path", "Win32_PerfFormattedData_PerfProc_Process",
            "where", "IDProcess=" + pid, "get", "PercentProcessorTime", "/value");
        if (result.isPresent()) {
            String output = result.get();
            for (String line : output.split("\n")) {
                if (line.startsWith("PercentProcessorTime=")) {
                    return Double.parseDouble(line.split("=")[1].trim());
                }
            }
        }
        return 0.0;
    }

    private double collectCpuMac(long pid) throws IOException, InterruptedException {
        // Use ps to get CPU percentage
        Optional<String> result = runCommand("ps", "-p", String.valueOf(pid), "-o", "%cpu=");
        if (result.isPresent()) {
            return Double.parseDouble(result.get().trim());
        }
        return 0.0;
    }

    private double collectCpuLinux(long pid) throws IOException, InterruptedException {
        // Use ps to get CPU percentage
        Optional<String> result = runCommand("ps", "-p", String.valueOf(pid), "-o", "%cpu=");
        if (result.isPresent()) {
            return Double.parseDouble(result.get().trim());
        }
        return 0.0;
    }

    private long collectMemoryWindows(long pid) throws IOException, InterruptedException {
        // Use WMIC to get working set size
        Optional<String> result = runCommand("wmic", "process", "where", "ProcessId=" + pid,
            "get", "WorkingSetSize", "/value");
        if (result.isPresent()) {
            String output = result.get();
            for (String line : output.split("\n")) {
                if (line.startsWith("WorkingSetSize=")) {
                    return Long.parseLong(line.split("=")[1].trim());
                }
            }
        }
        return 0L;
    }

    private long collectMemoryMac(long pid) throws IOException, InterruptedException {
        // Use ps to get RSS (resident set size) in KB
        Optional<String> result = runCommand("ps", "-p", String.valueOf(pid), "-o", "rss=");
        if (result.isPresent()) {
            long rssKb = Long.parseLong(result.get().trim());
            return rssKb * 1024; // Convert KB to bytes
        }
        return 0L;
    }

    private long collectMemoryLinux(long pid) throws IOException, InterruptedException {
        // Use ps to get RSS (resident set size) in KB
        Optional<String> result = runCommand("ps", "-p", String.valueOf(pid), "-o", "rss=");
        if (result.isPresent()) {
            long rssKb = Long.parseLong(result.get().trim());
            return rssKb * 1024; // Convert KB to bytes
        }
        return 0L;
    }

    private long calculateDirectorySize(Path path) throws IOException {
        if (!Files.exists(path)) {
            return 0L;
        }

        if (Files.isRegularFile(path)) {
            return Files.size(path);
        }

        try (Stream<Path> walk = Files.walk(path)) {
            return walk
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();
        }
    }

    private Optional<String> runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return Optional.empty();
        }

        if (process.exitValue() != 0) {
            return Optional.empty();
        }

        return Optional.of(output.toString());
    }
}
