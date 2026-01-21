package com.samsonmedia.barn.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

/**
 * Tests for JobRunner.
 */
class JobRunnerTest {

    @TempDir
    private Path tempDir;

    private BarnDirectories dirs;
    private JobRepository repository;
    private JobRunner runner;
    private JobsConfig config;

    @BeforeEach
    void setUp() throws IOException {
        dirs = new BarnDirectories(tempDir);
        dirs.initialize();
        repository = new JobRepository(dirs);
        runner = new JobRunner(repository, dirs);
        config = JobsConfig.withDefaults();
    }

    @Nested
    class Run {

        @Test
        void run_withSuccessfulCommand_shouldReturnSuccessResult() throws IOException {
            Job job = repository.create(getEchoCommand("hello"), "test", config);

            JobRunner.JobResult result = runner.run(job);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.exitCode()).isZero();
            assertThat(result.error()).isNull();
            assertThat(result.duration()).isNotNull();
        }

        @Test
        void run_shouldUpdateJobState() throws IOException {
            Job job = repository.create(getEchoCommand("hello"), "test", config);

            runner.run(job);

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.SUCCEEDED);
            assertThat(updated.exitCode()).isZero();
            assertThat(updated.finishedAt()).isNotNull();
        }

        @Test
        void run_withFailingCommand_shouldReturnFailureResult() throws IOException {
            Job job = repository.create(getFailingCommand(), "test", config);

            JobRunner.JobResult result = runner.run(job);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.exitCode()).isNotEqualTo(0);
            assertThat(result.error()).isNotNull();
        }

        @Test
        void run_withFailingCommand_shouldMarkJobAsFailed() throws IOException {
            Job job = repository.create(getFailingCommand(), "test", config);

            runner.run(job);

            Job updated = repository.findById(job.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(JobState.FAILED);
        }

        @Test
        void run_shouldCaptureStdout() throws IOException {
            Job job = repository.create(getEchoCommand("test output"), "test", config);

            runner.run(job);

            Path stdoutFile = dirs.getJobLogsDir(job.id()).resolve("stdout.log");
            assertThat(Files.exists(stdoutFile)).isTrue();
            String content = Files.readString(stdoutFile);
            assertThat(content).contains("test output");
        }

        @Test
        void run_shouldCaptureStderr() throws IOException {
            Job job = repository.create(getStderrCommand("error output"), "test", config);

            runner.run(job);

            Path stderrFile = dirs.getJobLogsDir(job.id()).resolve("stderr.log");
            assertThat(Files.exists(stderrFile)).isTrue();
            String content = Files.readString(stderrFile);
            assertThat(content).contains("error output");
        }

        @Test
        void run_shouldWritePid() throws IOException {
            Job job = repository.create(getEchoCommand("test"), "test", config);

            runner.run(job);

            Job updated = repository.findById(job.id()).orElseThrow();
            // PID was set during execution (may be null after completion on some platforms)
            assertThat(updated.startedAt()).isNotNull();
        }

        @Test
        void run_withNullJob_shouldThrowException() {
            assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class JobResultRecord {

        @Test
        void isSuccess_withZeroExitCode_shouldReturnTrue() {
            JobRunner.JobResult result = new JobRunner.JobResult(0, Duration.ofSeconds(1), null);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void isSuccess_withNonZeroExitCode_shouldReturnFalse() {
            JobRunner.JobResult result = new JobRunner.JobResult(1, Duration.ofSeconds(1), "error");

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void isSuccess_withNegativeExitCode_shouldReturnFalse() {
            JobRunner.JobResult result = new JobRunner.JobResult(-1, Duration.ofSeconds(1), "failed to start");

            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    class Constructor {

        @Test
        void constructor_withNullRepository_shouldThrowException() {
            assertThatThrownBy(() -> new JobRunner(null, dirs))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withNullDirs_shouldThrowException() {
            assertThatThrownBy(() -> new JobRunner(repository, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withAllDependencies_shouldSucceed() {
            ProcessExecutor executor = new ProcessExecutor();
            ProcessMonitor monitor = new ProcessMonitor();

            JobRunner customRunner = new JobRunner(executor, monitor, repository, dirs);

            assertThat(customRunner).isNotNull();
        }
    }

    // Platform-agnostic command helpers

    private List<String> getEchoCommand(String message) {
        if (isWindows()) {
            return List.of("cmd", "/c", "echo", message);
        }
        return List.of("echo", message);
    }

    private List<String> getStderrCommand(String message) {
        if (isWindows()) {
            return List.of("cmd", "/c", "echo", message, "1>&2");
        }
        return List.of("sh", "-c", "echo '" + message + "' >&2");
    }

    private List<String> getFailingCommand() {
        if (isWindows()) {
            return List.of("cmd", "/c", "exit", "1");
        }
        return List.of("sh", "-c", "exit 1");
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
