package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.state.BarnDirectories;

import picocli.CommandLine;

/**
 * Tests for RunCommand.
 */
class RunCommandTest {

    @TempDir
    private Path tempDir;

    private StringWriter stdout;
    private StringWriter stderr;
    private CommandLine cmd;

    @BeforeEach
    void setUp() throws IOException {
        // Initialize barn directories for testing
        BarnDirectories dirs = new BarnDirectories(tempDir);
        dirs.initialize();

        stdout = new StringWriter();
        stderr = new StringWriter();

        RunCommand runCommand = new RunCommand();
        runCommand.setBarnDir(tempDir);

        cmd = new CommandLine(runCommand);
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Nested
    class CommandParsing {

        @Test
        void run_withSimpleCommand_shouldSucceed() {
            int exitCode = cmd.execute("--offline", "echo", "hello");

            assertThat(exitCode).isZero();
        }

        @Test
        void run_withTag_shouldSetTag() {
            int exitCode = cmd.execute("--offline", "--tag", "test-tag", "echo", "hello");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).contains("test-tag");
        }

        @Test
        void run_withShortTag_shouldSetTag() {
            int exitCode = cmd.execute("--offline", "-t", "my-tag", "echo", "hello");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).contains("my-tag");
        }

        @Test
        void run_withNoCommand_shouldFail() {
            int exitCode = cmd.execute("--offline");

            assertThat(exitCode).isNotZero();
        }
    }

    @Nested
    class OfflineMode {

        @Test
        void run_inOfflineMode_shouldRunJob() {
            int exitCode = cmd.execute("--offline", "echo", "test");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).contains("Job created:");
        }

        @Test
        void run_inOfflineMode_shouldShowJobId() {
            int exitCode = cmd.execute("--offline", "echo", "test");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).matches("(?s).*job-[a-f0-9]+.*");
        }

        @Test
        void run_inOfflineMode_shouldShowState() {
            int exitCode = cmd.execute("--offline", "echo", "test");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString().toLowerCase()).contains("queued");
        }
    }

    @Nested
    class OutputFormats {

        @Test
        void run_withJsonOutput_shouldProduceJson() {
            int exitCode = cmd.execute("--offline", "--output", "JSON", "echo", "test");

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("{");
            assertThat(output).contains("\"id\"");
            assertThat(output).contains("\"state\"");
        }

        @Test
        void run_withXmlOutput_shouldProduceXml() {
            int exitCode = cmd.execute("--offline", "--output", "XML", "echo", "test");

            assertThat(exitCode).isZero();
            String output = stdout.toString();
            assertThat(output).contains("<?xml");
            assertThat(output).contains("<job>");
        }

        @Test
        void run_withHumanOutput_shouldProduceReadableOutput() {
            int exitCode = cmd.execute("--offline", "--output", "HUMAN", "echo", "test");

            assertThat(exitCode).isZero();
            assertThat(stdout.toString()).contains("Job created:");
        }
    }

    @Nested
    class RetryOptions {

        @Test
        void run_withMaxRetries_shouldAcceptOption() {
            int exitCode = cmd.execute("--offline", "--max-retries", "5", "echo", "test");

            assertThat(exitCode).isZero();
        }

        @Test
        void run_withRetryDelay_shouldAcceptOption() {
            int exitCode = cmd.execute("--offline", "--retry-delay", "60", "echo", "test");

            assertThat(exitCode).isZero();
        }

        @Test
        void run_withTimeout_shouldAcceptOption() {
            int exitCode = cmd.execute("--offline", "--timeout", "3600", "echo", "test");

            assertThat(exitCode).isZero();
        }
    }

    @Nested
    class ServiceMode {

        @Test
        void run_withoutOffline_shouldIndicateServiceNeeded() {
            int exitCode = cmd.execute("echo", "test");

            // Without --offline and without service, should fail gracefully
            assertThat(exitCode).isNotZero();
            assertThat(stderr.toString()).containsIgnoringCase("service");
        }
    }
}
