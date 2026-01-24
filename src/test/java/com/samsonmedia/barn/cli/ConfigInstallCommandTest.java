package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Tests for ConfigInstallCommand.
 */
class ConfigInstallCommandTest {

    @TempDir
    private Path tempDir;

    private StringWriter outWriter;
    private StringWriter errWriter;
    private CommandLine cmd;

    @BeforeEach
    void setUp() {
        outWriter = new StringWriter();
        errWriter = new StringWriter();
        cmd = new CommandLine(new BarnCommand());
        cmd.setOut(new PrintWriter(outWriter));
        cmd.setErr(new PrintWriter(errWriter));
    }

    @Nested
    class InstallWithForce {

        @Test
        void install_withForce_shouldCreateAllFiles() {
            Path targetDir = tempDir.resolve("barn-config");

            int exitCode = cmd.execute("config", "install",
                "--directory", targetDir.toString(),
                "--force");

            assertThat(exitCode).isZero();
            assertThat(Files.exists(targetDir.resolve("barn.conf"))).isTrue();
            assertThat(Files.exists(targetDir.resolve("high.load"))).isTrue();
            assertThat(Files.exists(targetDir.resolve("medium.load"))).isTrue();
            assertThat(Files.exists(targetDir.resolve("low.load"))).isTrue();
        }

        @Test
        void install_withForce_shouldCreateValidBarnConf() throws Exception {
            Path targetDir = tempDir.resolve("barn-config");

            cmd.execute("config", "install", "--directory", targetDir.toString(), "--force");

            String content = Files.readString(targetDir.resolve("barn.conf"));
            assertThat(content).contains("[service]");
            assertThat(content).contains("[load_levels]");
            assertThat(content).contains("[jobs]");
            assertThat(content).contains("[cleanup]");
            assertThat(content).contains("[storage]");
        }

        @Test
        void install_withForce_shouldCreateValidHighLoad() throws Exception {
            Path targetDir = tempDir.resolve("barn-config");

            cmd.execute("config", "install", "--directory", targetDir.toString(), "--force");

            String content = Files.readString(targetDir.resolve("high.load"));
            assertThat(content).contains("ffmpeg");
            assertThat(content).contains("x264");
        }

        @Test
        void install_withForce_shouldCreateValidLowLoad() throws Exception {
            Path targetDir = tempDir.resolve("barn-config");

            cmd.execute("config", "install", "--directory", targetDir.toString(), "--force");

            String content = Files.readString(targetDir.resolve("low.load"));
            assertThat(content).contains("curl");
            assertThat(content).contains("wget");
        }

        @Test
        void install_withForce_shouldOverwriteExistingFiles() throws Exception {
            Path targetDir = tempDir.resolve("barn-config");
            Files.createDirectories(targetDir);
            Files.writeString(targetDir.resolve("barn.conf"), "old content");

            int exitCode = cmd.execute("config", "install",
                "--directory", targetDir.toString(),
                "--force");

            assertThat(exitCode).isZero();
            String content = Files.readString(targetDir.resolve("barn.conf"));
            assertThat(content).contains("[service]");
            assertThat(content).doesNotContain("old content");
        }
    }

    @Nested
    class ShowOption {

        @Test
        void install_withShow_shouldNotWriteFiles() {
            Path targetDir = tempDir.resolve("barn-config");

            int exitCode = cmd.execute("config", "install",
                "--directory", targetDir.toString(),
                "--show");

            assertThat(exitCode).isZero();
            assertThat(Files.exists(targetDir)).isFalse();
        }

        @Test
        void install_withShow_shouldDisplayFileList() {
            Path targetDir = tempDir.resolve("barn-config");

            cmd.execute("config", "install",
                "--directory", targetDir.toString(),
                "--show");

            String output = outWriter.toString();
            assertThat(output).contains("barn.conf");
            assertThat(output).contains("high.load");
            assertThat(output).contains("medium.load");
            assertThat(output).contains("low.load");
        }
    }

    @Nested
    class HelpOption {

        @Test
        void install_withHelp_shouldShowUsage() {
            int exitCode = cmd.execute("config", "install", "--help");

            assertThat(exitCode).isZero();
            assertThat(outWriter.toString()).contains("Install default configuration files");
        }
    }
}
