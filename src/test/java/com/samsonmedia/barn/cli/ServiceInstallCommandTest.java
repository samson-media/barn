package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Tests for ServiceInstallCommand.
 */
class ServiceInstallCommandTest {

    @TempDir
    private Path tempDir;

    private StringWriter stdout;
    private StringWriter stderr;
    private CommandLine cmd;

    @BeforeEach
    void setUp() {
        stdout = new StringWriter();
        stderr = new StringWriter();
        cmd = new CommandLine(new BarnCommand());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Nested
    class InstallCommand {

        @Test
        void install_showsInHelp() {
            int exitCode = cmd.execute("service", "--help");
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("install");
        }

        @Test
        void install_withHelp_shouldShowUsage() {
            int exitCode = cmd.execute("service", "install", "--help");
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("Install Barn as a system service");
            assertThat(stdout.toString()).contains("--binary");
            assertThat(stdout.toString()).contains("--barn-dir");
        }

        @Test
        void install_withBinaryOption_shouldAcceptPath() {
            int exitCode = cmd.execute("service", "install", "--help");
            assertThat(stdout.toString()).contains("--binary");
        }

        @Test
        void install_withBarnDirOption_shouldAcceptPath() {
            int exitCode = cmd.execute("service", "install", "--help");
            assertThat(stdout.toString()).contains("--barn-dir");
        }
    }

    @Nested
    class UninstallCommand {

        @Test
        void uninstall_showsInHelp() {
            int exitCode = cmd.execute("service", "--help");
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("uninstall");
        }

        @Test
        void uninstall_withHelp_shouldShowUsage() {
            int exitCode = cmd.execute("service", "uninstall", "--help");
            assertThat(exitCode).isEqualTo(0);
            assertThat(stdout.toString()).contains("Uninstall Barn system service");
            assertThat(stdout.toString()).contains("--force");
        }
    }
}
