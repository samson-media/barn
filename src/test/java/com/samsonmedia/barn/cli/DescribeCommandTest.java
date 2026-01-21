package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.state.BarnDirectories;

import picocli.CommandLine;

/**
 * Tests for DescribeCommand.
 */
class DescribeCommandTest {

    @TempDir
    private Path tempDir;

    private StringWriter stdout;
    private StringWriter stderr;
    private CommandLine cmd;
    private BarnDirectories dirs;
    private JobRepository repository;
    private JobsConfig config;

    @BeforeEach
    void setUp() throws IOException {
        dirs = new BarnDirectories(tempDir);
        dirs.initialize();
        repository = new JobRepository(dirs);
        config = JobsConfig.withDefaults();

        stdout = new StringWriter();
        stderr = new StringWriter();

        DescribeCommand describeCommand = new DescribeCommand();
        describeCommand.setBarnDir(tempDir);

        cmd = new CommandLine(describeCommand);
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Nested
    class BasicDescribe {

        @Test
        void describe_existingJob_shouldShowDetails() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test-tag", config);

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains(job.id());
            assertThat(output).contains("test-tag");
        }

        @Test
        void describe_shouldShowState() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isZero();
            assertThat(stdout.toString().toLowerCase()).contains("queued");
        }

        @Test
        void describe_shouldShowCommand() throws IOException {
            Job job = repository.create(List.of("echo", "hello", "world"), "test", config);

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("echo");
            assertThat(output).contains("hello");
            assertThat(output).contains("world");
        }

        @Test
        void describe_shouldShowTimestamps() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).containsIgnoringCase("created");
        }
    }

    @Nested
    class NotFound {

        @Test
        void describe_nonExistentJob_shouldFail() {
            int exitCode = cmd.execute("--offline", "job-nonexistent");

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("not found");
        }

        @Test
        void describe_withNoJobId_shouldFail() {
            int exitCode = cmd.execute("--offline");

            assertThat(exitCode).isNotZero();
        }
    }

    @Nested
    class LogsOption {

        @Test
        void describe_withLogs_shouldIncludeLogContent() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            // Create a log file with content
            Path logsDir = dirs.getJobLogsDir(job.id());
            Files.writeString(logsDir.resolve("stdout.log"), "Log line 1\nLog line 2\n");

            int exitCode = cmd.execute("--offline", "--logs", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("Log line");
        }

        @Test
        void describe_withLogsNoContent_shouldHandleGracefully() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);

            int exitCode = cmd.execute("--offline", "--logs", job.id());

            assertThat(exitCode).isZero();
            // Should not error even if no logs
        }
    }

    @Nested
    class ManifestOption {

        @Test
        void describe_withManifest_shouldShowFullManifest() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);

            int exitCode = cmd.execute("--offline", "--manifest", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).containsIgnoringCase("manifest");
        }
    }

    @Nested
    class OutputFormats {

        @Test
        void describe_withJsonOutput_shouldProduceJson() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);

            int exitCode = cmd.execute("--offline", "--output", "JSON", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("{");
            assertThat(output).contains("\"id\"");
        }

        @Test
        void describe_withXmlOutput_shouldProduceXml() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);

            int exitCode = cmd.execute("--offline", "--output", "XML", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("<?xml");
        }
    }

    @Nested
    class ServiceMode {

        @Test
        void describe_withoutOffline_shouldIndicateServiceNeeded() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);

            int exitCode = cmd.execute(job.id());

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("service");
        }
    }
}
