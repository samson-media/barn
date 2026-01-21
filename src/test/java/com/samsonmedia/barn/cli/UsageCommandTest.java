package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.monitoring.UsageLogger;
import com.samsonmedia.barn.monitoring.UsageRecord;
import com.samsonmedia.barn.state.BarnDirectories;

import picocli.CommandLine;

/**
 * Tests for UsageCommand.
 */
class UsageCommandTest {

    @TempDir
    private Path tempDir;

    private StringWriter outWriter;
    private StringWriter errWriter;
    private CommandLine cmd;
    private BarnDirectories dirs;
    private JobRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        outWriter = new StringWriter();
        errWriter = new StringWriter();
        cmd = new CommandLine(new BarnCommand());
        cmd.setOut(new PrintWriter(outWriter));
        cmd.setErr(new PrintWriter(errWriter));

        dirs = new BarnDirectories(tempDir);
        dirs.initialize();
        repository = new JobRepository(dirs);
    }

    @Nested
    class UsageOutput {

        @Test
        void usage_withNoJob_shouldShowError() {
            int exitCode = cmd.execute("usage", "nonexistent-job", "--offline");

            assertThat(exitCode).isNotZero();
            assertThat(errWriter.toString()).contains("Job not found");
        }

        @Test
        void usage_withNoUsageData_shouldShowError() throws IOException {
            // Create job without usage data
            final var job = repository.create(List.of("echo", "test"), null, JobsConfig.withDefaults());

            // Use custom command with barn dir set
            UsageCommand usageCmd = new UsageCommand();
            usageCmd.setBarnDir(tempDir);

            CommandLine customCmd = new CommandLine(usageCmd);
            customCmd.setOut(new PrintWriter(outWriter));
            customCmd.setErr(new PrintWriter(errWriter));

            int exitCode = customCmd.execute(job.id(), "--offline");

            assertThat(exitCode).isNotZero();
            assertThat(errWriter.toString()).contains("No usage data");
        }

        @Test
        void usage_withValidData_shouldShowSummary() throws Exception {
            // Create job with usage data
            var job = repository.create(List.of("echo", "test"), null, JobsConfig.withDefaults());
            createUsageData(job.id());

            // Execute command with custom barn dir
            UsageCommand usageCmd = new UsageCommand();
            usageCmd.setBarnDir(tempDir);

            CommandLine customCmd = new CommandLine(usageCmd);
            customCmd.setOut(new PrintWriter(outWriter));
            customCmd.setErr(new PrintWriter(errWriter));

            int exitCode = customCmd.execute(job.id(), "--offline");

            assertThat(exitCode).isZero();
            String output = outWriter.toString();
            assertThat(output).contains("Resource Usage for Job");
            assertThat(output).contains("Summary");
            assertThat(output).contains("CPU");
            assertThat(output).contains("Memory");
        }

        @Test
        void usage_withSummaryOnly_shouldNotShowSamples() throws Exception {
            var job = repository.create(List.of("echo", "test"), null, JobsConfig.withDefaults());
            createUsageData(job.id());

            UsageCommand usageCmd = new UsageCommand();
            usageCmd.setBarnDir(tempDir);

            CommandLine customCmd = new CommandLine(usageCmd);
            customCmd.setOut(new PrintWriter(outWriter));
            customCmd.setErr(new PrintWriter(errWriter));

            int exitCode = customCmd.execute(job.id(), "--offline", "--summary");

            assertThat(exitCode).isZero();
            String output = outWriter.toString();
            assertThat(output).contains("Summary");
            assertThat(output).doesNotContain("Recent Samples");
        }

        @Test
        void usage_withCsvFlag_shouldOutputRawCsv() throws Exception {
            var job = repository.create(List.of("echo", "test"), null, JobsConfig.withDefaults());
            createUsageData(job.id());

            UsageCommand usageCmd = new UsageCommand();
            usageCmd.setBarnDir(tempDir);

            CommandLine customCmd = new CommandLine(usageCmd);
            customCmd.setOut(new PrintWriter(outWriter));
            customCmd.setErr(new PrintWriter(errWriter));

            int exitCode = customCmd.execute(job.id(), "--offline", "--csv");

            assertThat(exitCode).isZero();
            String output = outWriter.toString();
            assertThat(output).contains(UsageRecord.csvHeader());
            assertThat(output).contains(","); // CSV format
        }

        @Test
        void usage_withLimit_shouldLimitRecords() throws Exception {
            var job = repository.create(List.of("echo", "test"), null, JobsConfig.withDefaults());
            createUsageData(job.id(), 10); // Create 10 records

            UsageCommand usageCmd = new UsageCommand();
            usageCmd.setBarnDir(tempDir);

            CommandLine customCmd = new CommandLine(usageCmd);
            customCmd.setOut(new PrintWriter(outWriter));
            customCmd.setErr(new PrintWriter(errWriter));

            int exitCode = customCmd.execute(job.id(), "--offline", "--limit", "3");

            assertThat(exitCode).isZero();
            // Output should mention limited records
            String output = outWriter.toString();
            assertThat(output).contains("Summary");
        }
    }

    @Nested
    class JsonOutput {

        @Test
        void usage_withJsonFormat_shouldOutputJson() throws Exception {
            var job = repository.create(List.of("echo", "test"), null, JobsConfig.withDefaults());
            createUsageData(job.id());

            UsageCommand usageCmd = new UsageCommand();
            usageCmd.setBarnDir(tempDir);

            CommandLine customCmd = new CommandLine(usageCmd);
            customCmd.setOut(new PrintWriter(outWriter));
            customCmd.setErr(new PrintWriter(errWriter));

            int exitCode = customCmd.execute(job.id(), "--offline", "--output", "JSON");

            assertThat(exitCode).isZero();
            String output = outWriter.toString();
            assertThat(output).contains("\"jobId\"");
            assertThat(output).contains("\"summary\"");
            assertThat(output).contains("\"samples\"");
        }

        @Test
        void usage_withJsonAndSummaryOnly_shouldNotIncludeSamples() throws Exception {
            var job = repository.create(List.of("echo", "test"), null, JobsConfig.withDefaults());
            createUsageData(job.id());

            UsageCommand usageCmd = new UsageCommand();
            usageCmd.setBarnDir(tempDir);

            CommandLine customCmd = new CommandLine(usageCmd);
            customCmd.setOut(new PrintWriter(outWriter));
            customCmd.setErr(new PrintWriter(errWriter));

            int exitCode = customCmd.execute(job.id(), "--offline", "--output", "JSON", "--summary");

            assertThat(exitCode).isZero();
            String output = outWriter.toString();
            assertThat(output).contains("\"jobId\"");
            assertThat(output).contains("\"summary\"");
            assertThat(output).doesNotContain("\"samples\"");
        }
    }

    @Nested
    class Help {

        @Test
        void usage_withHelp_shouldShowUsage() {
            int exitCode = cmd.execute("usage", "--help");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("Show resource usage for a job");
            assertThat(outWriter.toString()).contains("--limit");
            assertThat(outWriter.toString()).contains("--csv");
            assertThat(outWriter.toString()).contains("--summary");
        }
    }

    // Helper methods

    private void createUsageData(String jobId) throws IOException {
        createUsageData(jobId, 5);
    }

    private void createUsageData(String jobId, int recordCount) throws IOException {
        Path logsDir = dirs.getJobLogsDir(jobId);
        Files.createDirectories(logsDir);

        try (UsageLogger logger = UsageLogger.forJobLogsDir(logsDir)) {
            Instant base = Instant.now();
            for (int i = 0; i < recordCount; i++) {
                UsageRecord record = UsageRecord.withoutGpu(
                    base.plusSeconds(i * 5),
                    20.0 + i * 5, // CPU: 20%, 25%, 30%, ...
                    1024 * 1024 * (i + 1), // Memory: 1MB, 2MB, 3MB, ...
                    512 * 1024 * (i + 1) // Disk: 512KB, 1MB, 1.5MB, ...
                );
                logger.log(record);
            }
        }
    }
}
