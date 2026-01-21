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
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;

import picocli.CommandLine;

/**
 * Tests for KillCommand.
 */
class KillCommandTest {

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

        KillCommand killCommand = new KillCommand();
        killCommand.setBarnDir(tempDir);

        cmd = new CommandLine(killCommand);
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Nested
    class NotFound {

        @Test
        void kill_nonExistentJob_shouldFail() {
            int exitCode = cmd.execute("--offline", "job-nonexistent");

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("not found");
        }

        @Test
        void kill_withNoJobId_shouldFail() {
            int exitCode = cmd.execute("--offline");

            assertThat(exitCode).isNotZero();
        }
    }

    @Nested
    class NotRunning {

        @Test
        void kill_queuedJob_shouldFail() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("not running");
        }

        @Test
        void kill_succeededJob_shouldFail() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.markStarted(job.id(), 12345L);
            repository.markCompleted(job.id(), 0, null);

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("not running");
        }

        @Test
        void kill_failedJob_shouldFail() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.markStarted(job.id(), 12345L);
            repository.markCompleted(job.id(), 1, "Error");

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("not running");
        }

        @Test
        void kill_canceledJob_shouldFail() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.markStarted(job.id(), 12345L);
            repository.markCanceled(job.id());

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("not running");
        }
    }

    @Nested
    class KillRunningJob {

        @Test
        void kill_runningJobWithNoPid_shouldUpdateState() throws IOException {
            // A running job might have no PID recorded yet (edge case)
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.updateState(job.id(), JobState.RUNNING);

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isZero();
            // Verify state changed to CANCELED
            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.CANCELED);
        }

        @Test
        void kill_runningJobWithDeadPid_shouldUpdateState() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            // Use a PID that doesn't exist (high value unlikely to be real)
            repository.markStarted(job.id(), 999999999L);

            int exitCode = cmd.execute("--offline", job.id());

            assertThat(exitCode).isZero();
            // Verify state changed to CANCELED even if process not found
            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.CANCELED);
        }
    }

    @Nested
    class QuietMode {

        @Test
        void kill_withQuiet_shouldProduceNoOutput() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.updateState(job.id(), JobState.RUNNING);

            int exitCode = cmd.execute("--offline", "--quiet", job.id());

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).isEmpty();
        }

        @Test
        void kill_withQuietError_shouldStillShowError() throws IOException {
            int exitCode = cmd.execute("--offline", "--quiet", "job-nonexistent");

            assertThat(exitCode).isNotZero();
            // Errors still shown even in quiet mode
            assertThat(stderr.toString()).containsIgnoringCase("not found");
        }
    }

    @Nested
    class OutputFormats {

        @Test
        void kill_withJsonOutput_shouldProduceJson() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.updateState(job.id(), JobState.RUNNING);

            int exitCode = cmd.execute("--offline", "--output", "JSON", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("{");
            assertThat(output).contains("\"id\"");
        }

        @Test
        void kill_withXmlOutput_shouldProduceXml() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.updateState(job.id(), JobState.RUNNING);

            int exitCode = cmd.execute("--offline", "--output", "XML", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("<?xml");
        }

        @Test
        void kill_withHumanOutput_shouldShowMessage() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.updateState(job.id(), JobState.RUNNING);

            int exitCode = cmd.execute("--offline", "--output", "HUMAN", job.id());

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).containsIgnoringCase("killed");
        }
    }

    @Nested
    class ForceOption {

        @Test
        void kill_withForce_shouldUpdateState() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.updateState(job.id(), JobState.RUNNING);

            int exitCode = cmd.execute("--offline", "--force", job.id());

            assertThat(exitCode).isZero();
            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.CANCELED);
        }
    }

    @Nested
    class ServiceMode {

        @Test
        void kill_withoutOffline_shouldIndicateServiceNeeded() throws IOException {
            Job job = repository.create(List.of("echo", "hello"), "test", config);
            repository.updateState(job.id(), JobState.RUNNING);

            int exitCode = cmd.execute(job.id());

            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("service");
        }
    }
}
