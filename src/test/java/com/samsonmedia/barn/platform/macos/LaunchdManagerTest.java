package com.samsonmedia.barn.platform.macos;

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
 * Tests for LaunchdManager.
 */
class LaunchdManagerTest {

    private LaunchdManager manager;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        manager = new LaunchdManager();
    }

    @Nested
    class GetLabel {

        @Test
        void getLabel_shouldReturnCorrectLabel() {
            assertThat(manager.getLabel()).isEqualTo("com.samsonmedia.barn");
        }
    }

    @Nested
    class GetPaths {

        @Test
        void getSystemDaemonPath_shouldReturnCorrectPath() {
            Path path = manager.getSystemDaemonPath();
            assertThat(path.toString()).isEqualTo("/Library/LaunchDaemons/com.samsonmedia.barn.plist");
        }

        @Test
        void getUserAgentPath_shouldReturnPathInUserLibrary() {
            Path path = manager.getUserAgentPath();
            assertThat(path.toString()).contains("Library/LaunchAgents");
            assertThat(path.toString()).endsWith("com.samsonmedia.barn.plist");
        }
    }

    @Nested
    class GeneratePlist {

        @Test
        void generatePlist_shouldContainLabel() {
            String plist = manager.generatePlist(Path.of("/usr/local/bin/barn"), tempDir);
            assertThat(plist).contains("<string>com.samsonmedia.barn</string>");
        }

        @Test
        void generatePlist_shouldContainBinaryPath() {
            Path binary = Path.of("/usr/local/bin/barn");
            String plist = manager.generatePlist(binary, tempDir);
            assertThat(plist).contains("<string>/usr/local/bin/barn</string>");
        }

        @Test
        void generatePlist_shouldContainServiceCommand() {
            String plist = manager.generatePlist(Path.of("/usr/local/bin/barn"), tempDir);
            assertThat(plist).contains("<string>service</string>");
            assertThat(plist).contains("<string>start</string>");
            assertThat(plist).contains("<string>--foreground</string>");
        }

        @Test
        void generatePlist_shouldContainBarnDir() {
            String plist = manager.generatePlist(Path.of("/usr/local/bin/barn"), tempDir);
            assertThat(plist).contains("<string>--barn-dir</string>");
            assertThat(plist).contains("<string>" + tempDir.toString() + "</string>");
        }

        @Test
        void generatePlist_shouldContainLogPaths() {
            String plist = manager.generatePlist(Path.of("/usr/local/bin/barn"), tempDir);
            Path logsDir = tempDir.resolve("logs");
            assertThat(plist).contains(logsDir.toString() + "/barn.log");
        }

        @Test
        void generatePlist_shouldContainRunAtLoad() {
            String plist = manager.generatePlist(Path.of("/usr/local/bin/barn"), tempDir);
            assertThat(plist).contains("<key>RunAtLoad</key>");
            assertThat(plist).contains("<true/>");
        }

        @Test
        void generatePlist_shouldContainKeepAlive() {
            String plist = manager.generatePlist(Path.of("/usr/local/bin/barn"), tempDir);
            assertThat(plist).contains("<key>KeepAlive</key>");
        }

        @Test
        void generatePlist_shouldBeValidXml() {
            String plist = manager.generatePlist(Path.of("/usr/local/bin/barn"), tempDir);
            assertThat(plist).startsWith("<?xml version=\"1.0\"");
            assertThat(plist).contains("<!DOCTYPE plist");
            assertThat(plist).contains("<plist version=\"1.0\">");
            assertThat(plist).contains("</plist>");
        }
    }

    @Nested
    class WritePlist {

        @Test
        void writePlist_shouldCreateFile() throws IOException {
            Path plistPath = tempDir.resolve("test.plist");
            String content = "<plist>test</plist>";

            manager.writePlist(plistPath, content);

            assertThat(Files.exists(plistPath)).isTrue();
            assertThat(Files.readString(plistPath)).isEqualTo(content);
        }

        @Test
        void writePlist_shouldCreateParentDirectories() throws IOException {
            Path plistPath = tempDir.resolve("nested/dir/test.plist");
            String content = "<plist>test</plist>";

            manager.writePlist(plistPath, content);

            assertThat(Files.exists(plistPath)).isTrue();
        }

        @Test
        void writePlist_withNullPath_shouldThrow() {
            assertThatThrownBy(() -> manager.writePlist(null, "content"))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void writePlist_withNullContent_shouldThrow() {
            assertThatThrownBy(() -> manager.writePlist(tempDir.resolve("test.plist"), null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class DeletePlist {

        @Test
        void deletePlist_shouldRemoveFile() throws IOException {
            Path plistPath = tempDir.resolve("test.plist");
            Files.writeString(plistPath, "content");

            boolean result = manager.deletePlist(plistPath);

            assertThat(result).isTrue();
            assertThat(Files.exists(plistPath)).isFalse();
        }

        @Test
        void deletePlist_withNonExistentFile_shouldReturnTrue() {
            Path plistPath = tempDir.resolve("nonexistent.plist");

            boolean result = manager.deletePlist(plistPath);

            assertThat(result).isTrue();
        }

        @Test
        void deletePlist_withNullPath_shouldThrow() {
            assertThatThrownBy(() -> manager.deletePlist(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class IsUnderLaunchd {

        @Test
        void isUnderLaunchd_shouldReturnBoolean() {
            // This test just verifies the method doesn't throw
            boolean result = LaunchdManager.isUnderLaunchd();
            assertThat(result).isIn(true, false);
        }
    }

    @Nested
    class LaunchdExceptionTest {

        @Test
        void constructor_withMessage_shouldSetMessage() {
            var exception = new LaunchdManager.LaunchdException("test message");
            assertThat(exception.getMessage()).isEqualTo("test message");
        }

        @Test
        void constructor_withCause_shouldSetCause() {
            var cause = new RuntimeException("cause");
            var exception = new LaunchdManager.LaunchdException("test message", cause);
            assertThat(exception.getMessage()).isEqualTo("test message");
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }
}
