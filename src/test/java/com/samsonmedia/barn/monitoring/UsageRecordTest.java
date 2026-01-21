package com.samsonmedia.barn.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for UsageRecord.
 */
class UsageRecordTest {

    @Nested
    class Constructor {

        @Test
        void constructor_withValidValues_shouldCreateRecord() {
            Instant timestamp = Instant.now();

            UsageRecord record = new UsageRecord(timestamp, 50.5, 1024 * 1024, 2048, 75.0, 512L);

            assertThat(record.timestamp()).isEqualTo(timestamp);
            assertThat(record.cpuPercent()).isEqualTo(50.5);
            assertThat(record.memoryBytes()).isEqualTo(1024 * 1024);
            assertThat(record.diskBytes()).isEqualTo(2048);
            assertThat(record.gpuPercent()).isEqualTo(75.0);
            assertThat(record.gpuMemoryBytes()).isEqualTo(512L);
        }

        @Test
        void constructor_withNullTimestamp_shouldThrowException() {
            assertThatThrownBy(() -> new UsageRecord(null, 50.0, 1024, 2048, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timestamp");
        }

        @Test
        void constructor_withNegativeCpu_shouldThrowException() {
            assertThatThrownBy(() -> new UsageRecord(Instant.now(), -1.0, 1024, 2048, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cpuPercent");
        }

        @Test
        void constructor_withNegativeMemory_shouldThrowException() {
            assertThatThrownBy(() -> new UsageRecord(Instant.now(), 50.0, -1, 2048, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryBytes");
        }

        @Test
        void constructor_withNegativeDisk_shouldThrowException() {
            assertThatThrownBy(() -> new UsageRecord(Instant.now(), 50.0, 1024, -1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diskBytes");
        }

        @Test
        void constructor_withNegativeGpuPercent_shouldThrowException() {
            assertThatThrownBy(() -> new UsageRecord(Instant.now(), 50.0, 1024, 2048, -1.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gpuPercent");
        }

        @Test
        void constructor_withNegativeGpuMemory_shouldThrowException() {
            assertThatThrownBy(() -> new UsageRecord(Instant.now(), 50.0, 1024, 2048, 75.0, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gpuMemoryBytes");
        }
    }

    @Nested
    class WithoutGpu {

        @Test
        void withoutGpu_shouldCreateRecordWithNullGpuValues() {
            Instant timestamp = Instant.now();

            UsageRecord record = UsageRecord.withoutGpu(timestamp, 50.0, 1024, 2048);

            assertThat(record.timestamp()).isEqualTo(timestamp);
            assertThat(record.cpuPercent()).isEqualTo(50.0);
            assertThat(record.memoryBytes()).isEqualTo(1024);
            assertThat(record.diskBytes()).isEqualTo(2048);
            assertThat(record.gpuPercent()).isNull();
            assertThat(record.gpuMemoryBytes()).isNull();
        }
    }

    @Nested
    class ToCsvLine {

        @Test
        void toCsvLine_withoutGpu_shouldFormatCorrectly() {
            Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
            UsageRecord record = UsageRecord.withoutGpu(timestamp, 50.5, 1048576, 2048);

            String csv = record.toCsvLine();

            assertThat(csv).isEqualTo("2024-01-15T10:30:00Z,50.50,1048576,2048,,");
        }

        @Test
        void toCsvLine_withGpu_shouldIncludeGpuValues() {
            Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
            UsageRecord record = new UsageRecord(timestamp, 50.5, 1048576, 2048, 75.25, 524288L);

            String csv = record.toCsvLine();

            assertThat(csv).isEqualTo("2024-01-15T10:30:00Z,50.50,1048576,2048,75.25,524288");
        }
    }

    @Nested
    class CsvHeader {

        @Test
        void csvHeader_shouldReturnCorrectFormat() {
            String header = UsageRecord.csvHeader();

            assertThat(header).isEqualTo("timestamp,cpu_percent,memory_bytes,disk_bytes,gpu_percent,gpu_memory_bytes");
        }
    }

    @Nested
    class FromCsvLine {

        @Test
        void fromCsvLine_withoutGpu_shouldParseCorrectly() {
            String csv = "2024-01-15T10:30:00Z,50.50,1048576,2048,,";

            UsageRecord record = UsageRecord.fromCsvLine(csv);

            assertThat(record.timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
            assertThat(record.cpuPercent()).isEqualTo(50.5);
            assertThat(record.memoryBytes()).isEqualTo(1048576);
            assertThat(record.diskBytes()).isEqualTo(2048);
            assertThat(record.gpuPercent()).isNull();
            assertThat(record.gpuMemoryBytes()).isNull();
        }

        @Test
        void fromCsvLine_withGpu_shouldParseCorrectly() {
            String csv = "2024-01-15T10:30:00Z,50.50,1048576,2048,75.25,524288";

            UsageRecord record = UsageRecord.fromCsvLine(csv);

            assertThat(record.timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
            assertThat(record.cpuPercent()).isEqualTo(50.5);
            assertThat(record.memoryBytes()).isEqualTo(1048576);
            assertThat(record.diskBytes()).isEqualTo(2048);
            assertThat(record.gpuPercent()).isEqualTo(75.25);
            assertThat(record.gpuMemoryBytes()).isEqualTo(524288L);
        }

        @Test
        void fromCsvLine_withNullLine_shouldThrowException() {
            assertThatThrownBy(() -> UsageRecord.fromCsvLine(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void fromCsvLine_withInvalidLine_shouldThrowException() {
            assertThatThrownBy(() -> UsageRecord.fromCsvLine("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class FormatBytes {

        @Test
        void formatBytes_withBytes_shouldFormatAsBytes() {
            assertThat(UsageRecord.formatBytes(512)).isEqualTo("512 B");
        }

        @Test
        void formatBytes_withKilobytes_shouldFormatAsKB() {
            assertThat(UsageRecord.formatBytes(2048)).isEqualTo("2.0 KB");
        }

        @Test
        void formatBytes_withMegabytes_shouldFormatAsMB() {
            assertThat(UsageRecord.formatBytes(2 * 1024 * 1024)).isEqualTo("2.0 MB");
        }

        @Test
        void formatBytes_withGigabytes_shouldFormatAsGB() {
            assertThat(UsageRecord.formatBytes(2L * 1024 * 1024 * 1024)).isEqualTo("2.00 GB");
        }
    }
}
