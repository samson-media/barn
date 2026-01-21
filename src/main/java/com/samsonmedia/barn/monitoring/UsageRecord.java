package com.samsonmedia.barn.monitoring;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single usage measurement for a process.
 *
 * @param timestamp when the measurement was taken
 * @param cpuPercent CPU usage as a percentage (0-100+)
 * @param memoryBytes memory usage in bytes
 * @param diskBytes disk usage in bytes (typically of work directory)
 * @param gpuPercent GPU usage as a percentage (null if not available)
 * @param gpuMemoryBytes GPU memory usage in bytes (null if not available)
 */
public record UsageRecord(
    Instant timestamp,
    double cpuPercent,
    long memoryBytes,
    long diskBytes,
    Double gpuPercent,
    Long gpuMemoryBytes
) {

    /**
     * Creates a UsageRecord with validation.
     */
    public UsageRecord {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (cpuPercent < 0) {
            throw new IllegalArgumentException("cpuPercent must be non-negative");
        }
        if (memoryBytes < 0) {
            throw new IllegalArgumentException("memoryBytes must be non-negative");
        }
        if (diskBytes < 0) {
            throw new IllegalArgumentException("diskBytes must be non-negative");
        }
        if (gpuPercent != null && gpuPercent < 0) {
            throw new IllegalArgumentException("gpuPercent must be non-negative");
        }
        if (gpuMemoryBytes != null && gpuMemoryBytes < 0) {
            throw new IllegalArgumentException("gpuMemoryBytes must be non-negative");
        }
    }

    /**
     * Creates a UsageRecord without GPU information.
     *
     * @param timestamp when the measurement was taken
     * @param cpuPercent CPU usage as a percentage
     * @param memoryBytes memory usage in bytes
     * @param diskBytes disk usage in bytes
     * @return a new UsageRecord
     */
    public static UsageRecord withoutGpu(Instant timestamp, double cpuPercent,
                                          long memoryBytes, long diskBytes) {
        return new UsageRecord(timestamp, cpuPercent, memoryBytes, diskBytes, null, null);
    }

    /**
     * Converts this record to a CSV line.
     *
     * @return CSV formatted string
     */
    public String toCsvLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp.toString());
        sb.append(',').append(String.format("%.2f", cpuPercent));
        sb.append(',').append(memoryBytes);
        sb.append(',').append(diskBytes);
        sb.append(',').append(gpuPercent != null ? String.format("%.2f", gpuPercent) : "");
        sb.append(',').append(gpuMemoryBytes != null ? gpuMemoryBytes : "");
        return sb.toString();
    }

    /**
     * Returns the CSV header line.
     *
     * @return CSV header
     */
    public static String csvHeader() {
        return "timestamp,cpu_percent,memory_bytes,disk_bytes,gpu_percent,gpu_memory_bytes";
    }

    /**
     * Parses a UsageRecord from a CSV line.
     *
     * @param line the CSV line
     * @return the parsed UsageRecord
     * @throws IllegalArgumentException if the line is invalid
     */
    public static UsageRecord fromCsvLine(String line) {
        Objects.requireNonNull(line, "line must not be null");
        String[] parts = line.split(",", -1);
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid CSV line: " + line);
        }

        Instant timestamp = Instant.parse(parts[0]);
        double cpuPercent = Double.parseDouble(parts[1]);
        long memoryBytes = Long.parseLong(parts[2]);
        long diskBytes = Long.parseLong(parts[3]);

        Double gpuPercent = null;
        Long gpuMemoryBytes = null;

        if (parts.length > 4 && !parts[4].isEmpty()) {
            gpuPercent = Double.parseDouble(parts[4]);
        }
        if (parts.length > 5 && !parts[5].isEmpty()) {
            gpuMemoryBytes = Long.parseLong(parts[5]);
        }

        return new UsageRecord(timestamp, cpuPercent, memoryBytes, diskBytes, gpuPercent, gpuMemoryBytes);
    }

    /**
     * Formats memory size for human display.
     *
     * @param bytes the size in bytes
     * @return human-readable size string
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }
}
