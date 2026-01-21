package com.samsonmedia.barn.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for IpcServer.
 */
class IpcServerTest {

    @TempDir
    private Path tempDir;

    private Path socketPath;
    private IpcServer server;

    @BeforeEach
    void setUp() {
        socketPath = tempDir.resolve("test.sock");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Nested
    class Construction {

        @Test
        void constructor_shouldStoreSocketPath() {
            server = new IpcServer(socketPath, (type, payload) -> null);

            assertThat(server.getSocketPath()).isEqualTo(socketPath);
        }

        @Test
        void constructor_withNullPath_shouldThrow() {
            assertThatThrownBy(() -> new IpcServer(null, (type, payload) -> null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withNullHandler_shouldThrow() {
            assertThatThrownBy(() -> new IpcServer(socketPath, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class StartStop {

        @Test
        void start_shouldCreateSocketFile() throws IOException {
            server = new IpcServer(socketPath, (type, payload) -> null);

            server.start();

            assertThat(Files.exists(socketPath)).isTrue();
            assertThat(server.isRunning()).isTrue();
        }

        @Test
        void start_whenAlreadyRunning_shouldThrow() throws IOException {
            server = new IpcServer(socketPath, (type, payload) -> null);
            server.start();

            assertThatThrownBy(() -> server.start())
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void stop_shouldRemoveSocketFile() throws IOException {
            server = new IpcServer(socketPath, (type, payload) -> null);
            server.start();

            server.stop();

            assertThat(Files.exists(socketPath)).isFalse();
            assertThat(server.isRunning()).isFalse();
        }

        @Test
        void stop_whenNotRunning_shouldNotThrow() {
            server = new IpcServer(socketPath, (type, payload) -> null);

            // Should not throw
            server.stop();
            assertThat(server.isRunning()).isFalse();
        }

        @Test
        void stop_multipleTimes_shouldNotThrow() throws IOException {
            server = new IpcServer(socketPath, (type, payload) -> null);
            server.start();

            server.stop();
            server.stop(); // Second stop should be safe
        }
    }

    @Nested
    class IsRunning {

        @Test
        void isRunning_beforeStart_shouldReturnFalse() {
            server = new IpcServer(socketPath, (type, payload) -> null);

            assertThat(server.isRunning()).isFalse();
        }

        @Test
        void isRunning_afterStart_shouldReturnTrue() throws IOException {
            server = new IpcServer(socketPath, (type, payload) -> null);
            server.start();

            assertThat(server.isRunning()).isTrue();
        }

        @Test
        void isRunning_afterStop_shouldReturnFalse() throws IOException {
            server = new IpcServer(socketPath, (type, payload) -> null);
            server.start();
            server.stop();

            assertThat(server.isRunning()).isFalse();
        }
    }
}
