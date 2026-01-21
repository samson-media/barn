package com.samsonmedia.barn;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Main entry point.
 */
class MainTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void run_withHelpFlag_shouldReturnZeroExitCode() {
        // Arrange & Act
        int exitCode = Main.run(new String[]{"--help"});

        // Assert
        assertThat(exitCode).isZero();
    }

    @Test
    void run_withVersionFlag_shouldReturnZeroExitCode() {
        // Arrange & Act
        int exitCode = Main.run(new String[]{"--version"});

        // Assert
        assertThat(exitCode).isZero();
    }

    @Test
    void run_withNoArgs_shouldReturnZeroExitCode() {
        // Arrange & Act
        int exitCode = Main.run(new String[]{});

        // Assert
        assertThat(exitCode).isZero();
    }

    @Test
    void run_withInvalidCommand_shouldReturnNonZeroExitCode() {
        // Arrange & Act
        int exitCode = Main.run(new String[]{"nonexistent-command"});

        // Assert
        assertThat(exitCode).isNotZero();
    }

    @Test
    void run_withHelpFlag_shouldPrintUsageToStdout() {
        // Arrange & Act
        Main.run(new String[]{"--help"});

        // Assert
        String output = outContent.toString();
        assertThat(output).contains("barn");
        assertThat(output).contains("Usage:");
    }

    @Test
    void run_withVersionFlag_shouldPrintVersionToStdout() {
        // Arrange & Act
        Main.run(new String[]{"--version"});

        // Assert
        String output = outContent.toString();
        assertThat(output).contains("barn");
        assertThat(output).contains("0.1.0");
    }
}
