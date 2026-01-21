package com.samsonmedia.barn.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for StateFiles.
 */
class StateFilesTest {

    @TempDir
    private Path tempDir;

    private Path jobDir;
    private StateFiles stateFiles;

    @BeforeEach
    void setUp() throws IOException {
        jobDir = tempDir.resolve("job-123");
        Files.createDirectories(jobDir);
        stateFiles = new StateFiles(jobDir);
    }

    @Nested
    class StateTests {

        @Test
        void writeState_shouldWritePlainText() throws IOException {
            stateFiles.writeState(JobState.RUNNING);

            String content = Files.readString(jobDir.resolve("state"));
            assertThat(content).isEqualTo("running");
        }

        @Test
        void readState_withExistingFile_shouldReturnState() throws IOException {
            Files.writeString(jobDir.resolve("state"), "queued");

            Optional<JobState> result = stateFiles.readState();

            assertThat(result).hasValue(JobState.QUEUED);
        }

        @Test
        void readState_withNonexistentFile_shouldReturnEmpty() throws IOException {
            Optional<JobState> result = stateFiles.readState();

            assertThat(result).isEmpty();
        }

        @Test
        void writeAndReadState_shouldRoundTrip() throws IOException {
            stateFiles.writeState(JobState.FAILED);

            Optional<JobState> result = stateFiles.readState();

            assertThat(result).hasValue(JobState.FAILED);
        }
    }

    @Nested
    class TagTests {

        @Test
        void writeAndReadTag_shouldRoundTrip() throws IOException {
            stateFiles.writeTag("my-tag");

            Optional<String> result = stateFiles.readTag();

            assertThat(result).hasValue("my-tag");
        }

        @Test
        void readTag_withNonexistentFile_shouldReturnEmpty() throws IOException {
            Optional<String> result = stateFiles.readTag();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class TimestampTests {

        private final Instant testTimestamp = Instant.parse("2026-01-21T18:42:10Z");

        @Test
        void writeCreatedAt_shouldWriteIso8601() throws IOException {
            stateFiles.writeCreatedAt(testTimestamp);

            String content = Files.readString(jobDir.resolve("created_at"));
            assertThat(content).isEqualTo("2026-01-21T18:42:10Z");
        }

        @Test
        void readCreatedAt_shouldParseIso8601() throws IOException {
            Files.writeString(jobDir.resolve("created_at"), "2026-01-21T18:42:10Z");

            Optional<Instant> result = stateFiles.readCreatedAt();

            assertThat(result).hasValue(testTimestamp);
        }

        @Test
        void writeAndReadStartedAt_shouldRoundTrip() throws IOException {
            stateFiles.writeStartedAt(testTimestamp);

            Optional<Instant> result = stateFiles.readStartedAt();

            assertThat(result).hasValue(testTimestamp);
        }

        @Test
        void writeAndReadFinishedAt_shouldRoundTrip() throws IOException {
            stateFiles.writeFinishedAt(testTimestamp);

            Optional<Instant> result = stateFiles.readFinishedAt();

            assertThat(result).hasValue(testTimestamp);
        }

        @Test
        void writeAndReadHeartbeat_shouldRoundTrip() throws IOException {
            stateFiles.writeHeartbeat(testTimestamp);

            Optional<Instant> result = stateFiles.readHeartbeat();

            assertThat(result).hasValue(testTimestamp);
        }

        @Test
        void writeAndReadRetryAt_shouldRoundTrip() throws IOException {
            stateFiles.writeRetryAt(testTimestamp);

            Optional<Instant> result = stateFiles.readRetryAt();

            assertThat(result).hasValue(testTimestamp);
        }

        @Test
        void readTimestamp_withInvalidFormat_shouldReturnEmpty() throws IOException {
            Files.writeString(jobDir.resolve("created_at"), "not a timestamp");

            Optional<Instant> result = stateFiles.readCreatedAt();

            assertThat(result).isEmpty();
        }

        @Test
        void readTimestamp_withNonexistentFile_shouldReturnEmpty() throws IOException {
            Optional<Instant> result = stateFiles.readCreatedAt();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class ExitCodeTests {

        @Test
        void writeExitCode_withInteger_shouldWrite() throws IOException {
            stateFiles.writeExitCode(0);

            String content = Files.readString(jobDir.resolve("exit_code"));
            assertThat(content).isEqualTo("0");
        }

        @Test
        void writeExitCode_withSymbolic_shouldWrite() throws IOException {
            stateFiles.writeExitCode("orphaned_process");

            String content = Files.readString(jobDir.resolve("exit_code"));
            assertThat(content).isEqualTo("orphaned_process");
        }

        @Test
        void readExitCode_withInteger_shouldReturnInt() throws IOException {
            Files.writeString(jobDir.resolve("exit_code"), "42");

            OptionalInt result = stateFiles.readExitCode();

            assertThat(result).hasValue(42);
        }

        @Test
        void readExitCode_withSymbolic_shouldReturnEmpty() throws IOException {
            Files.writeString(jobDir.resolve("exit_code"), "orphaned_process");

            OptionalInt result = stateFiles.readExitCode();

            assertThat(result).isEmpty();
        }

        @Test
        void readExitCodeString_withSymbolic_shouldReturnString() throws IOException {
            Files.writeString(jobDir.resolve("exit_code"), "orphaned_process");

            Optional<String> result = stateFiles.readExitCodeString();

            assertThat(result).hasValue("orphaned_process");
        }
    }

    @Nested
    class ErrorTests {

        @Test
        void writeAndReadError_shouldRoundTrip() throws IOException {
            stateFiles.writeError("Something went wrong");

            Optional<String> result = stateFiles.readError();

            assertThat(result).hasValue("Something went wrong");
        }

        @Test
        void readError_withNonexistentFile_shouldReturnEmpty() throws IOException {
            Optional<String> result = stateFiles.readError();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class PidTests {

        @Test
        void writeAndReadPid_shouldRoundTrip() throws IOException {
            stateFiles.writePid(12345L);

            OptionalLong result = stateFiles.readPid();

            assertThat(result).hasValue(12345L);
        }

        @Test
        void readPid_withNonNumeric_shouldReturnEmpty() throws IOException {
            Files.writeString(jobDir.resolve("pid"), "not a number");

            OptionalLong result = stateFiles.readPid();

            assertThat(result).isEmpty();
        }

        @Test
        void readPid_withNonexistentFile_shouldReturnEmpty() throws IOException {
            OptionalLong result = stateFiles.readPid();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class RetryCountTests {

        @Test
        void writeAndReadRetryCount_shouldRoundTrip() throws IOException {
            stateFiles.writeRetryCount(2);

            OptionalInt result = stateFiles.readRetryCount();

            assertThat(result).hasValue(2);
        }

        @Test
        void readRetryCount_withNonexistentFile_shouldReturnEmpty() throws IOException {
            OptionalInt result = stateFiles.readRetryCount();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class RetryHistoryTests {

        @Test
        void appendRetryHistory_shouldAddEntry() throws IOException {
            stateFiles.appendRetryHistory("Attempt 1: exit_code=1");

            Optional<String> result = stateFiles.readRetryHistory();

            assertThat(result).hasValue("Attempt 1: exit_code=1");
        }

        @Test
        void appendRetryHistory_shouldAppendToExisting() throws IOException {
            stateFiles.appendRetryHistory("Attempt 1: exit_code=1");
            stateFiles.appendRetryHistory("Attempt 2: exit_code=2");

            Optional<String> result = stateFiles.readRetryHistory();

            assertThat(result).hasValue("Attempt 1: exit_code=1\nAttempt 2: exit_code=2");
        }
    }

    @Nested
    class StageTests {

        @Test
        void writeAndReadStage_shouldRoundTrip() throws IOException {
            stateFiles.writeStage("transcode");

            Optional<String> result = stateFiles.readStage();

            assertThat(result).hasValue("transcode");
        }

        @Test
        void readStage_withNonexistentFile_shouldReturnEmpty() throws IOException {
            Optional<String> result = stateFiles.readStage();

            assertThat(result).isEmpty();
        }
    }

    @Test
    void getJobDir_shouldReturnJobDir() {
        assertThat(stateFiles.getJobDir()).isEqualTo(jobDir);
    }

    @Test
    void constructor_withNullJobDir_shouldThrowException() {
        assertThatThrownBy(() -> new StateFiles(null))
            .isInstanceOf(NullPointerException.class);
    }
}
