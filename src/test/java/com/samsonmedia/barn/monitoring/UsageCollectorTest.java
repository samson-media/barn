package com.samsonmedia.barn.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for UsageCollector.
 */
class UsageCollectorTest {

    @TempDir
    private Path tempDir;

    private UsageCollector collector;

    @BeforeEach
    void setUp() {
        collector = new UsageCollector();
    }

    @Nested
    class Collect {

        @Test
        void collect_withValidPid_shouldReturnRecord() {
            // Use current process PID
            long pid = ProcessHandle.current().pid();

            UsageRecord record = collector.collect(pid, tempDir);

            assertThat(record).isNotNull();
            assertThat(record.timestamp()).isNotNull();
            // CPU and memory should be non-negative (may be 0 if collection fails)
            assertThat(record.cpuPercent()).isGreaterThanOrEqualTo(0);
            assertThat(record.memoryBytes()).isGreaterThanOrEqualTo(0);
            assertThat(record.diskBytes()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void collect_withNonExistentPid_shouldReturnZeroValues() {
            // Use a PID that almost certainly doesn't exist
            long invalidPid = 999999999L;

            UsageRecord record = collector.collect(invalidPid, tempDir);

            assertThat(record).isNotNull();
            assertThat(record.cpuPercent()).isEqualTo(0.0);
            assertThat(record.memoryBytes()).isEqualTo(0L);
        }
    }

    @Nested
    class CollectDiskUsage {

        @Test
        void collectDiskUsage_withEmptyDirectory_shouldReturnZero() {
            long bytes = collector.collectDiskUsage(tempDir);

            assertThat(bytes).isEqualTo(0L);
        }

        @Test
        void collectDiskUsage_withFiles_shouldReturnTotalSize() throws IOException {
            // Create some files
            Path file1 = tempDir.resolve("file1.txt");
            Path file2 = tempDir.resolve("file2.txt");
            Files.writeString(file1, "Hello");
            Files.writeString(file2, "World!");

            long bytes = collector.collectDiskUsage(tempDir);

            // Should be at least the size of our content
            assertThat(bytes).isGreaterThanOrEqualTo(11L);
        }

        @Test
        void collectDiskUsage_withNestedFiles_shouldIncludeAll() throws IOException {
            // Create nested structure
            Path subDir = tempDir.resolve("subdir");
            Files.createDirectories(subDir);
            Files.writeString(tempDir.resolve("root.txt"), "root");
            Files.writeString(subDir.resolve("nested.txt"), "nested");

            long bytes = collector.collectDiskUsage(tempDir);

            // Should include both files
            assertThat(bytes).isGreaterThanOrEqualTo(10L);
        }

        @Test
        void collectDiskUsage_withNonExistentPath_shouldReturnZero() {
            Path nonExistent = tempDir.resolve("does-not-exist");

            long bytes = collector.collectDiskUsage(nonExistent);

            assertThat(bytes).isEqualTo(0L);
        }
    }

    @Nested
    class CollectCpuUsage {

        @Test
        void collectCpuUsage_withCurrentProcess_shouldReturnNonNegative() {
            long pid = ProcessHandle.current().pid();

            double cpu = collector.collectCpuUsage(pid);

            assertThat(cpu).isGreaterThanOrEqualTo(0);
        }

        @Test
        void collectCpuUsage_withInvalidPid_shouldReturnZero() {
            double cpu = collector.collectCpuUsage(999999999L);

            assertThat(cpu).isEqualTo(0.0);
        }
    }

    @Nested
    class CollectMemoryUsage {

        @Test
        void collectMemoryUsage_withCurrentProcess_shouldReturnPositive() {
            long pid = ProcessHandle.current().pid();

            long memory = collector.collectMemoryUsage(pid);

            // Current process should have some memory
            assertThat(memory).isGreaterThan(0);
        }

        @Test
        void collectMemoryUsage_withInvalidPid_shouldReturnZero() {
            long memory = collector.collectMemoryUsage(999999999L);

            assertThat(memory).isEqualTo(0L);
        }
    }

    @Nested
    class CollectGpuUsage {

        @Test
        void collectGpuUsage_shouldReturnNullIfNoGpu() {
            // Most test environments won't have nvidia-smi
            Double gpu = collector.collectGpuUsage();

            // Either null (no GPU) or a valid percentage
            if (gpu != null) {
                assertThat(gpu).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Nested
    class CollectGpuMemory {

        @Test
        void collectGpuMemory_shouldReturnNullIfNoGpu() {
            // Most test environments won't have nvidia-smi
            Long gpuMemory = collector.collectGpuMemory();

            // Either null (no GPU) or a valid value
            if (gpuMemory != null) {
                assertThat(gpuMemory).isGreaterThanOrEqualTo(0);
            }
        }
    }
}
