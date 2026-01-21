package com.samsonmedia.barn.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for BarnDirectories.
 */
class BarnDirectoriesTest {

    @TempDir
    private Path tempDir;

    private BarnDirectories directories;

    @BeforeEach
    void setUp() {
        directories = new BarnDirectories(tempDir);
    }

    @Nested
    class PathResolution {

        @Test
        void getBaseDir_shouldReturnBaseDir() {
            assertThat(directories.getBaseDir()).isEqualTo(tempDir);
        }

        @Test
        void getJobsDir_shouldReturnJobsSubdir() {
            assertThat(directories.getJobsDir()).isEqualTo(tempDir.resolve("jobs"));
        }

        @Test
        void getLocksDir_shouldReturnLocksSubdir() {
            assertThat(directories.getLocksDir()).isEqualTo(tempDir.resolve("locks"));
        }

        @Test
        void getLogsDir_shouldReturnLogsSubdir() {
            assertThat(directories.getLogsDir()).isEqualTo(tempDir.resolve("logs"));
        }

        @Test
        void getJobDir_shouldReturnJobSpecificDir() {
            assertThat(directories.getJobDir("job-123"))
                .isEqualTo(tempDir.resolve("jobs/job-123"));
        }

        @Test
        void getJobWorkDir_shouldReturnWorkSubdir() {
            assertThat(directories.getJobWorkDir("job-123"))
                .isEqualTo(tempDir.resolve("jobs/job-123/work"));
        }

        @Test
        void getJobInputDir_shouldReturnInputSubdir() {
            assertThat(directories.getJobInputDir("job-123"))
                .isEqualTo(tempDir.resolve("jobs/job-123/work/input"));
        }

        @Test
        void getJobOutputDir_shouldReturnOutputSubdir() {
            assertThat(directories.getJobOutputDir("job-123"))
                .isEqualTo(tempDir.resolve("jobs/job-123/work/output"));
        }

        @Test
        void getJobLogsDir_shouldReturnLogsSubdir() {
            assertThat(directories.getJobLogsDir("job-123"))
                .isEqualTo(tempDir.resolve("jobs/job-123/logs"));
        }

        @Test
        void getJobLockFile_shouldReturnLockFile() {
            assertThat(directories.getJobLockFile("job-123"))
                .isEqualTo(tempDir.resolve("locks/job-job-123.lock"));
        }

        @Test
        void getSchedulerLockFile_shouldReturnSchedulerLock() {
            assertThat(directories.getSchedulerLockFile())
                .isEqualTo(tempDir.resolve("locks/scheduler.lock"));
        }

        @Test
        void getDaemonLogFile_shouldReturnBarnLog() {
            assertThat(directories.getDaemonLogFile())
                .isEqualTo(tempDir.resolve("logs/barn.log"));
        }
    }

    @Nested
    class DirectoryCreation {

        @Test
        void initialize_shouldCreateTopLevelDirectories() throws IOException {
            directories.initialize();

            assertThat(Files.isDirectory(directories.getJobsDir())).isTrue();
            assertThat(Files.isDirectory(directories.getLocksDir())).isTrue();
            assertThat(Files.isDirectory(directories.getLogsDir())).isTrue();
        }

        @Test
        void initialize_withExistingDirectories_shouldNotFail() throws IOException {
            directories.initialize();
            directories.initialize(); // Second call should not fail
        }

        @Test
        void createJobDirs_shouldCreateFullJobStructure() throws IOException {
            directories.initialize();

            directories.createJobDirs("job-123");

            assertThat(Files.isDirectory(directories.getJobDir("job-123"))).isTrue();
            assertThat(Files.isDirectory(directories.getJobInputDir("job-123"))).isTrue();
            assertThat(Files.isDirectory(directories.getJobOutputDir("job-123"))).isTrue();
            assertThat(Files.isDirectory(directories.getJobLogsDir("job-123"))).isTrue();
        }

        @Test
        void createJobDirs_withNullJobId_shouldThrowException() {
            assertThatThrownBy(() -> directories.createJobDirs(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class JobExistence {

        @Test
        void jobExists_withExistingJob_shouldReturnTrue() throws IOException {
            directories.initialize();
            directories.createJobDirs("job-123");

            assertThat(directories.jobExists("job-123")).isTrue();
        }

        @Test
        void jobExists_withNonexistentJob_shouldReturnFalse() throws IOException {
            directories.initialize();

            assertThat(directories.jobExists("nonexistent")).isFalse();
        }
    }

    @Nested
    class JobDeletion {

        @Test
        void deleteJobDir_shouldRemoveJobDirectory() throws IOException {
            directories.initialize();
            directories.createJobDirs("job-123");
            Files.writeString(directories.getJobDir("job-123").resolve("state"), "running");

            directories.deleteJobDir("job-123");

            assertThat(directories.jobExists("job-123")).isFalse();
        }

        @Test
        void deleteJobDir_withNonexistentJob_shouldNotFail() throws IOException {
            directories.initialize();

            directories.deleteJobDir("nonexistent"); // Should not throw
        }

        @Test
        void deleteJobDir_withNullJobId_shouldThrowException() {
            assertThatThrownBy(() -> directories.deleteJobDir(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void constructor_withNullBaseDir_shouldThrowException() {
        assertThatThrownBy(() -> new BarnDirectories(null))
            .isInstanceOf(NullPointerException.class);
    }
}
