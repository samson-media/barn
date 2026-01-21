package com.samsonmedia.barn.platform.linux;

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
 * Tests for SystemdManager.
 */
class SystemdManagerTest {

    private SystemdManager manager;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        manager = new SystemdManager();
    }

    @Nested
    class GetPaths {

        @Test
        void getSystemServicePath_shouldReturnCorrectPath() {
            Path path = manager.getSystemServicePath();
            assertThat(path.toString()).isEqualTo("/etc/systemd/system/barn.service");
        }

        @Test
        void getUserServicePath_shouldReturnPathInUserConfig() {
            Path path = manager.getUserServicePath();
            assertThat(path.toString()).contains(".config/systemd/user");
            assertThat(path.toString()).endsWith("barn.service");
        }
    }

    @Nested
    class HasSystemd {

        @Test
        void hasSystemd_shouldReturnBoolean() {
            // This test just verifies the method doesn't throw
            boolean result = SystemdManager.hasSystemd();
            assertThat(result).isIn(true, false);
        }
    }

    @Nested
    class IsUnderSystemd {

        @Test
        void isUnderSystemd_shouldReturnBoolean() {
            // This test just verifies the method doesn't throw
            boolean result = SystemdManager.isUnderSystemd();
            assertThat(result).isIn(true, false);
        }
    }

    @Nested
    class GenerateUnitFile {

        @Test
        void generateUnitFile_system_shouldContainDescription() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("Description=Barn Job Daemon");
        }

        @Test
        void generateUnitFile_user_shouldContainDescription() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, true);
            assertThat(unit).contains("Description=Barn Job Daemon (User)");
        }

        @Test
        void generateUnitFile_shouldContainBinaryPath() {
            Path binary = Path.of("/usr/local/bin/barn");
            String unit = manager.generateUnitFile(binary, tempDir, false);
            assertThat(unit).contains("ExecStart=/usr/local/bin/barn");
        }

        @Test
        void generateUnitFile_shouldContainServiceCommand() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("service start --foreground");
        }

        @Test
        void generateUnitFile_shouldContainBarnDir() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("--barn-dir " + tempDir.toString());
        }

        @Test
        void generateUnitFile_system_shouldContainSecurityHardening() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("NoNewPrivileges=true");
            assertThat(unit).contains("ProtectSystem=strict");
            assertThat(unit).contains("ProtectHome=read-only");
        }

        @Test
        void generateUnitFile_user_shouldNotContainSecurityHardening() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, true);
            assertThat(unit).doesNotContain("NoNewPrivileges");
            assertThat(unit).doesNotContain("ProtectSystem");
        }

        @Test
        void generateUnitFile_shouldContainRestart() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("Restart=on-failure");
            assertThat(unit).contains("RestartSec=5");
        }

        @Test
        void generateUnitFile_system_shouldContainJournalLogging() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("StandardOutput=journal");
            assertThat(unit).contains("StandardError=journal");
            assertThat(unit).contains("SyslogIdentifier=barn");
        }

        @Test
        void generateUnitFile_system_shouldContainMultiUserTarget() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("WantedBy=multi-user.target");
        }

        @Test
        void generateUnitFile_user_shouldContainDefaultTarget() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, true);
            assertThat(unit).contains("WantedBy=default.target");
        }

        @Test
        void generateUnitFile_shouldContainExecStop() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("ExecStop=/usr/local/bin/barn service stop");
        }

        @Test
        void generateUnitFile_withNullBinaryPath_shouldThrow() {
            assertThatThrownBy(() -> manager.generateUnitFile(null, tempDir, false))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void generateUnitFile_withNullBarnDir_shouldThrow() {
            assertThatThrownBy(() -> manager.generateUnitFile(Path.of("/usr/local/bin/barn"), null, false))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void generateUnitFile_shouldContainDocumentation() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("Documentation=https://github.com/samson-media/barn");
        }

        @Test
        void generateUnitFile_shouldContainUnitServiceInstallSections() {
            String unit = manager.generateUnitFile(Path.of("/usr/local/bin/barn"), tempDir, false);
            assertThat(unit).contains("[Unit]");
            assertThat(unit).contains("[Service]");
            assertThat(unit).contains("[Install]");
        }
    }

    @Nested
    class WriteUnitFile {

        @Test
        void writeUnitFile_shouldCreateFile() throws IOException {
            Path unitPath = tempDir.resolve("test.service");
            String content = "[Unit]\nDescription=Test";

            manager.writeUnitFile(unitPath, content);

            assertThat(Files.exists(unitPath)).isTrue();
            assertThat(Files.readString(unitPath)).isEqualTo(content);
        }

        @Test
        void writeUnitFile_shouldCreateParentDirectories() throws IOException {
            Path unitPath = tempDir.resolve("nested/dir/test.service");
            String content = "[Unit]\nDescription=Test";

            manager.writeUnitFile(unitPath, content);

            assertThat(Files.exists(unitPath)).isTrue();
        }

        @Test
        void writeUnitFile_withNullPath_shouldThrow() {
            assertThatThrownBy(() -> manager.writeUnitFile(null, "content"))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void writeUnitFile_withNullContent_shouldThrow() {
            assertThatThrownBy(() -> manager.writeUnitFile(tempDir.resolve("test.service"), null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class DeleteUnitFile {

        @Test
        void deleteUnitFile_shouldRemoveFile() throws IOException {
            Path unitPath = tempDir.resolve("test.service");
            Files.writeString(unitPath, "content");

            boolean result = manager.deleteUnitFile(unitPath);

            assertThat(result).isTrue();
            assertThat(Files.exists(unitPath)).isFalse();
        }

        @Test
        void deleteUnitFile_withNonExistentFile_shouldReturnTrue() {
            Path unitPath = tempDir.resolve("nonexistent.service");

            boolean result = manager.deleteUnitFile(unitPath);

            assertThat(result).isTrue();
        }

        @Test
        void deleteUnitFile_withNullPath_shouldThrow() {
            assertThatThrownBy(() -> manager.deleteUnitFile(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class SystemdExceptionTest {

        @Test
        void constructor_withMessage_shouldSetMessage() {
            var exception = new SystemdManager.SystemdException("test message");
            assertThat(exception.getMessage()).isEqualTo("test message");
        }

        @Test
        void constructor_withCause_shouldSetCause() {
            var cause = new RuntimeException("cause");
            var exception = new SystemdManager.SystemdException("test message", cause);
            assertThat(exception.getMessage()).isEqualTo("test message");
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }
}
