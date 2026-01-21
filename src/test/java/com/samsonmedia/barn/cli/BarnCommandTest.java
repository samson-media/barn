package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

/**
 * Tests for the root BarnCommand.
 */
class BarnCommandTest {

    private CommandLine commandLine;
    private StringWriter outWriter;
    private StringWriter errWriter;

    @BeforeEach
    void setUp() {
        commandLine = new CommandLine(new BarnCommand());
        outWriter = new StringWriter();
        errWriter = new StringWriter();
        commandLine.setOut(new PrintWriter(outWriter));
        commandLine.setErr(new PrintWriter(errWriter));
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
        assertThat(output).contains("0.1.0");
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
    void runSubcommand_shouldShowNotImplementedMessage() {
        // Arrange & Act
        int exitCode = commandLine.execute("run");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("Run command not yet implemented");
    }

    @Test
    void statusSubcommand_shouldShowNotImplementedMessage() {
        // Arrange & Act
        int exitCode = commandLine.execute("status");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("Status command not yet implemented");
    }

    @Test
    void describeSubcommand_shouldShowNotImplementedMessage() {
        // Arrange & Act
        int exitCode = commandLine.execute("describe");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("Describe command not yet implemented");
    }

    @Test
    void killSubcommand_shouldShowNotImplementedMessage() {
        // Arrange & Act
        int exitCode = commandLine.execute("kill");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("Kill command not yet implemented");
    }

    @Test
    void cleanSubcommand_shouldShowNotImplementedMessage() {
        // Arrange & Act
        int exitCode = commandLine.execute("clean");

        // Assert
        assertThat(exitCode).isZero();
        String output = outWriter.toString();
        assertThat(output).contains("Clean command not yet implemented");
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
