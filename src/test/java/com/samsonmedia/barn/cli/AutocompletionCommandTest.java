package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

/**
 * Tests for AutocompletionCommand.
 */
class AutocompletionCommandTest {

    private CommandLine commandLine;
    private StringWriter out;
    private StringWriter err;

    @BeforeEach
    void setUp() {
        commandLine = new CommandLine(new BarnCommand());
        out = new StringWriter();
        err = new StringWriter();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));
    }

    @Nested
    class BashCompletion {

        @Test
        void shouldGenerateBashScript() {
            int exitCode = commandLine.execute("autocompletion", "--bash");

            assertThat(exitCode).isEqualTo(0);
            assertThat(out.toString()).contains("#!/usr/bin/env bash");
            assertThat(out.toString()).contains("barn Bash Completion");
            assertThat(out.toString()).contains("COMPREPLY");
        }

        @Test
        void shouldIncludeSubcommands() {
            int exitCode = commandLine.execute("autocompletion", "--bash");

            assertThat(exitCode).isEqualTo(0);
            String output = out.toString();
            assertThat(output).contains("run");
            assertThat(output).contains("status");
            assertThat(output).contains("describe");
            assertThat(output).contains("service");
        }
    }

    @Nested
    class ZshCompletion {

        @Test
        void shouldGenerateZshScript() {
            int exitCode = commandLine.execute("autocompletion", "--zsh");

            assertThat(exitCode).isEqualTo(0);
            assertThat(out.toString()).contains("#compdef barn");
            assertThat(out.toString()).contains("bashcompinit");
        }

        @Test
        void shouldIncludeBashCompletionForCompatibility() {
            int exitCode = commandLine.execute("autocompletion", "--zsh");

            assertThat(exitCode).isEqualTo(0);
            assertThat(out.toString()).contains("COMPREPLY");
        }
    }

    @Nested
    class NoShellSpecified {

        @Test
        void shouldShowUsage() {
            int exitCode = commandLine.execute("autocompletion");

            assertThat(exitCode).isEqualTo(0);
            assertThat(out.toString()).contains("Usage:");
            assertThat(out.toString()).contains("--bash");
            assertThat(out.toString()).contains("--zsh");
        }
    }
}
