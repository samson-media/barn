package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Tests for the root BarnCommand.
 */
class BarnCommandTest {

    @TempDir
    private Path tempDir;

    private CommandLine commandLine;
    private StringWriter outWriter;
    private StringWriter errWriter;

    @BeforeEach
    void setUp() throws IOException {
        commandLine = new CommandLine(new BarnCommand());
        outWriter = new StringWriter();
        errWriter = new StringWriter();
        commandLine.setOut(new PrintWriter(outWriter));
        commandLine.setErr(new PrintWriter(errWriter));

        // Initialize barn directories in temp location
        Files.createDirectories(tempDir.resolve("jobs"));
    }

    @Test
    void help_shouldDisplayCommandName() {
        // Arrange & Act
        commandLine.execute("--help");

        // Assert
        String output = outWriter.toString();
        assertThat(output).contains("barn");
    }

    @Test
    void help_shouldDisplayDescription() {
        // Arrange & Act
        commandLine.execute("--help");

        // Assert
        String output = outWriter.toString();
        assertThat(output).contains("Cross-platform job daemon");
    }

    @Test
    void help_shouldListSubcommands() {
        // Arrange & Act
        commandLine.execute("--help");

        // Assert
        String output = outWriter.toString();
        assertThat(output).contains("run");
        assertThat(output).contains("status");
        assertThat(output).contains("describe");
        assertThat(output).contains("kill");
        assertThat(output).contains("clean");
        assertThat(output).contains("service");
        assertThat(output).contains("config");
    }

    @Test
    void version_shouldDisplayVersionNumber() {
        // Arrange & Act
        commandLine.execute("--version");

        // Assert
        String output = outWriter.toString();
        assertThat(output).containsPattern("\\d+\\.\\d+\\.\\d+");
    }

    @Test
    void noArgs_shouldShowUsage() {
        // Arrange & Act
        int exitCode = commandLine.execute();

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("Usage:");
    }

    @Test
    void invalidSubcommand_shouldReturnNonZeroExitCode() {
        // Arrange & Act
        int exitCode = commandLine.execute("nonexistent");

        // Assert
        assertThat(exitCode).isNotZero();
    }

    @Test
    void runSubcommand_shouldExist() {
        // Arrange & Act
        int exitCode = commandLine.execute("run", "--help");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("run");
    }

    @Test
    void statusSubcommand_shouldExist() {
        // Arrange & Act
        int exitCode = commandLine.execute("status", "--help");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("status");
    }

    @Test
    void describeSubcommand_shouldExist() {
        // Arrange & Act
        int exitCode = commandLine.execute("describe", "--help");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("describe");
    }

    @Test
    void killSubcommand_shouldExist() {
        // Arrange & Act
        int exitCode = commandLine.execute("kill", "--help");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("kill");
    }

    @Test
    void cleanSubcommand_shouldExist() {
        // Arrange & Act
        int exitCode = commandLine.execute("clean", "--help");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("clean");
    }

    @Test
    void serviceSubcommand_shouldExist() {
        // Arrange & Act
        int exitCode = commandLine.execute("service", "--help");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("service");
    }

    @Test
    void configSubcommand_shouldExist() {
        // Arrange & Act
        int exitCode = commandLine.execute("config", "--help");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("config");
    }

    @Test
    void runSubcommand_withNoArgs_shouldShowUsageError() {
        // Arrange & Act
        int exitCode = commandLine.execute("run");

        // Assert - run with no command args shows usage help (required parameter missing)
        assertThat(exitCode).isNotZero();
        // Picocli shows usage when required parameter is missing
        String output = errWriter.toString();
        assertThat(output).contains("run");
    }

    @Test
    void statusSubcommand_withoutOffline_shouldIndicateServiceNeeded() {
        // Arrange & Act - use temp dir to ensure no service is running there
        int exitCode = commandLine.execute("status", "--barn-dir", tempDir.toString());

        // Assert - without --offline, should fail with service not running message
        assertThat(exitCode).isNotZero();
        String output = errWriter.toString();
        assertThat(output).containsIgnoringCase("service");
    }

    @Test
    void describeSubcommand_withNoJobId_shouldShowUsageError() {
        // Arrange & Act
        int exitCode = commandLine.execute("describe");

        // Assert - describe without job ID shows usage help (required parameter missing)
        assertThat(exitCode).isNotZero();
        String output = errWriter.toString();
        assertThat(output).contains("describe");
    }

    @Test
    void killSubcommand_withNoJobId_shouldShowUsageError() {
        // Arrange & Act
        int exitCode = commandLine.execute("kill");

        // Assert - kill without job ID shows usage help (required parameter missing)
        assertThat(exitCode).isNotZero();
        String output = errWriter.toString();
        assertThat(output).contains("kill");
    }

    @Test
    void cleanSubcommand_withoutOptions_shouldIndicateServiceNeeded() {
        // Arrange & Act - use temp dir to ensure no service is running there
        int exitCode = commandLine.execute("clean", "--barn-dir", tempDir.toString());

        // Assert - clean without --offline fails with service not running
        assertThat(exitCode).isNotZero();
        String output = errWriter.toString();
        assertThat(output).containsIgnoringCase("service");
    }

    @Test
    void serviceSubcommand_shouldShowUsageByDefault() {
        // Arrange & Act
        int exitCode = commandLine.execute("service");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("Usage:");
        assertThat(output).contains("service");
    }

    @Test
    void configSubcommand_shouldShowUsageByDefault() {
        // Arrange & Act
        int exitCode = commandLine.execute("config");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("Usage:");
        assertThat(output).contains("config");
    }
}
