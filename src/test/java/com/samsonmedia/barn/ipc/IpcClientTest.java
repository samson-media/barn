package com.samsonmedia.barn.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for IpcClient.
 */
class IpcClientTest {

    @TempDir
    private Path tempDir;

    @Nested
    class Construction {

        @Test
        void constructor_shouldStoreSocketPath() {
            Path socketPath = tempDir.resolve("test.sock");
            var client = new IpcClient(socketPath);

            assertThat(client.getSocketPath()).isEqualTo(socketPath);
        }

        @Test
        void constructor_withNullPath_shouldThrow() {
            assertThatThrownBy(() -> new IpcClient(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ServiceRunningCheck {

        @Test
        void isServiceRunning_withNoSocket_shouldReturnFalse() {
            Path socketPath = tempDir.resolve("nonexistent.sock");
            var client = new IpcClient(socketPath);

            assertThat(client.isServiceRunning()).isFalse();
        }
    }

    @Nested
    class Connection {

        @Test
        void ensureConnected_withNoSocket_shouldThrow() {
            Path socketPath = tempDir.resolve("nonexistent.sock");
            var client = new IpcClient(socketPath);

            assertThatThrownBy(client::ensureConnected)
                .isInstanceOf(IpcException.class)
                .hasFieldOrPropertyWithValue("code", IpcErrorCode.SERVICE_NOT_RUNNING);
        }
    }

    @Nested
    class DefaultSocketPath {

        @Test
        void getDefaultSocketPath_shouldReturnNonNull() {
            Path path = IpcClient.getDefaultSocketPath();

            assertThat(path).isNotNull();
            assertThat(path.toString()).contains("barn");
        }
    }

    @Nested
    class Close {

        @Test
        void close_withoutConnection_shouldNotThrow() {
            Path socketPath = tempDir.resolve("test.sock");
            var client = new IpcClient(socketPath);

            // Should not throw even if never connected
            client.close();
        }

        @Test
        void close_multipleTimes_shouldNotThrow() {
            Path socketPath = tempDir.resolve("test.sock");
            var client = new IpcClient(socketPath);

            client.close();
            client.close(); // Second close should be safe
        }
    }
}
