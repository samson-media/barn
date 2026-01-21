package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.state.BarnDirectories;

import picocli.CommandLine;

/**
 * Tests for StatusCommand.
 */
class StatusCommandTest {

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

        StatusCommand statusCommand = new StatusCommand();
        statusCommand.setBarnDir(tempDir);

        cmd = new CommandLine(statusCommand);
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Nested
    class NoJobs {

        @Test
        void status_withNoJobs_shouldShowEmptyMessage() {
            int exitCode = cmd.execute("--offline");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).containsIgnoringCase("no jobs");
        }
    }

    @Nested
    class WithJobs {

        @Test
        void status_withJobs_shouldShowAllJobs() throws IOException {
            // Create some jobs
            repository.create(List.of("echo", "test1"), "tag1", config);
            repository.create(List.of("echo", "test2"), "tag2", config);

            int exitCode = cmd.execute("--offline");

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("tag1");
            assertThat(output).contains("tag2");
        }

        @Test
        void status_shouldShowJobIds() throws IOException {
            repository.create(List.of("echo", "test"), "test", config);

            int exitCode = cmd.execute("--offline");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).matches("(?s).*job-[a-f0-9]+.*");
        }

        @Test
        void status_shouldShowJobState() throws IOException {
            repository.create(List.of("echo", "test"), "test", config);

            int exitCode = cmd.execute("--offline");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString().toLowerCase()).contains("queued");
        }
    }

    @Nested
    class Filtering {

        @Test
        void status_withTagFilter_shouldOnlyShowMatchingJobs() throws IOException {
            repository.create(List.of("echo", "test1"), "alpha", config);
            repository.create(List.of("echo", "test2"), "beta", config);

            int exitCode = cmd.execute("--offline", "--tag", "alpha");

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("alpha");
            assertThat(output).doesNotContain("beta");
        }

        @Test
        void status_withStateFilter_shouldOnlyShowMatchingJobs() throws IOException {
            repository.create(List.of("echo", "test1"), "tag1", config);
            repository.create(List.of("echo", "test2"), "tag2", config);

            int exitCode = cmd.execute("--offline", "--state", "QUEUED");

            assertThat(exitCode).isZero();
            // Both jobs are queued so both should show
            assertThat(stdout.toString()).contains("tag1");
            assertThat(stdout.toString()).contains("tag2");
        }

        @Test
        void status_withLimit_shouldLimitResults() throws IOException {
            for (int i = 0; i < 5; i++) {
                repository.create(List.of("echo", "test" + i), "tag" + i, config);
            }

            int exitCode = cmd.execute("--offline", "--limit", "2");

            assertThat(exitCode).isZero();
            // Should only show 2 jobs (most recent ones)
            String output = stdout.toString();
            // Count job-id occurrences - should be exactly 2
            long jobIdCount = output.lines()
                .filter(line -> line.contains("job-"))
                .count();
            assertThat(jobIdCount).isEqualTo(2);
        }
    }

    @Nested
    class OutputFormats {

        @Test
        void status_withJsonOutput_shouldProduceJson() throws IOException {
            repository.create(List.of("echo", "test"), "test", config);

            int exitCode = cmd.execute("--offline", "--output", "JSON");

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("{");
            assertThat(output).contains("\"jobs\"");
        }

        @Test
        void status_withXmlOutput_shouldProduceXml() throws IOException {
            repository.create(List.of("echo", "test"), "test", config);

            int exitCode = cmd.execute("--offline", "--output", "XML");

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("<?xml");
        }

        @Test
        void status_withHumanOutput_shouldShowTable() throws IOException {
            repository.create(List.of("echo", "test"), "test", config);

            int exitCode = cmd.execute("--offline", "--output", "HUMAN");

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            // Should have table headers
            assertThat(output.toUpperCase()).contains("ID");
            assertThat(output.toUpperCase()).contains("STATE");
        }
    }

    @Nested
    class Summary {

        @Test
        void status_shouldShowSummary() throws IOException {
            repository.create(List.of("echo", "test1"), "tag1", config);
            repository.create(List.of("echo", "test2"), "tag2", config);

            int exitCode = cmd.execute("--offline");

            assertThat(exitCode).isZero();
            String output = stdout.toString().toLowerCase();
            assertThat(output).contains("total");
            assertThat(output).contains("2");
        }
    }

    @Nested
    class ServiceMode {

        @Test
        void status_withoutOffline_shouldIndicateServiceNeeded() {
            int exitCode = cmd.execute();

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("service");
        }
    }
}
