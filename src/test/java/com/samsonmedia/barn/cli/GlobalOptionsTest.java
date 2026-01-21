package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

/**
 * Tests for GlobalOptions.
 */
class GlobalOptionsTest {

    @Nested
    class Defaults {

        @Test
        void globalOptions_shouldHaveHumanFormatByDefault() {
            GlobalOptions options = new GlobalOptions();

            assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.HUMAN);
        }

        @Test
        void globalOptions_shouldHaveNoConfigPathByDefault() {
            GlobalOptions options = new GlobalOptions();

            assertThat(options.getConfigPath()).isEmpty();
        }

        @Test
        void globalOptions_shouldNotBeOfflineByDefault() {
            GlobalOptions options = new GlobalOptions();

            assertThat(options.isOffline()).isFalse();
        }
    }

    @Nested
    class OutputFormatOption {

        @Test
        void outputFormat_withHuman_shouldSetHuman() {
            GlobalOptions options = parseOptions("-o", "HUMAN");

            assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.HUMAN);
        }

        @Test
        void outputFormat_withJson_shouldSetJson() {
            GlobalOptions options = parseOptions("-o", "JSON");

            assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.JSON);
        }

        @Test
        void outputFormat_withXml_shouldSetXml() {
            GlobalOptions options = parseOptions("-o", "XML");

            assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.XML);
        }

        @Test
        void outputFormat_withLongOption_shouldWork() {
            GlobalOptions options = parseOptions("--output", "JSON");

            assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.JSON);
        }
    }

    @Nested
    class ConfigOption {

        @Test
        void config_withPath_shouldSetConfigPath() {
            GlobalOptions options = parseOptions("--config", "/etc/barn/barn.conf");

            assertThat(options.getConfigPath()).isPresent();
            assertThat(options.getConfigPath().get()).isEqualTo(Path.of("/etc/barn/barn.conf"));
        }

        @Test
        void config_withoutPath_shouldReturnEmpty() {
            GlobalOptions options = parseOptions();

            assertThat(options.getConfigPath()).isEmpty();
        }
    }

    @Nested
    class OfflineOption {

        @Test
        void offline_withFlag_shouldBeTrue() {
            GlobalOptions options = parseOptions("--offline");

            assertThat(options.isOffline()).isTrue();
        }

        @Test
        void offline_withoutFlag_shouldBeFalse() {
            GlobalOptions options = parseOptions();

            assertThat(options.isOffline()).isFalse();
        }
    }

    @Nested
    class CombinedOptions {

        @Test
        void allOptions_shouldWorkTogether() {
            GlobalOptions options = parseOptions(
                "-o", "JSON",
                "--config", "/path/to/config",
                "--offline"
            );

            assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.JSON);
            assertThat(options.getConfigPath()).contains(Path.of("/path/to/config"));
            assertThat(options.isOffline()).isTrue();
        }
    }

    // Helper to parse options using picocli
    private GlobalOptions parseOptions(String... args) {
        TestCommand cmd = new TestCommand();
        new CommandLine(cmd).parseArgs(args);
        return cmd.getGlobalOptions();
    }

    @CommandLine.Command(name = "test")
    static class TestCommand implements Runnable {
        @CommandLine.Mixin
        private GlobalOptions globalOptions;

        GlobalOptions getGlobalOptions() {
            return globalOptions;
        }

        @Override
        public void run() {
            // No-op
        }
    }
}
