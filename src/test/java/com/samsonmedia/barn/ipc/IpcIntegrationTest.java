package com.samsonmedia.barn.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for IPC client-server communication.
 */
class IpcIntegrationTest {

    @TempDir
    private Path tempDir;

    private Path socketPath;
    private IpcServer server;
    private IpcClient client;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        socketPath = tempDir.resolve("test.sock");

        // Create a simple echo handler
        server = new IpcServer(socketPath, (type, payload) -> {
            if ("echo".equals(type)) {
                return payload;
            }
            if ("error".equals(type)) {
                throw new IpcException("TEST_ERROR", "Test error message");
            }
            if ("slow".equals(type)) {
                // Simulate slow operation
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Map.of("result", "slow_done");
            }
            return Map.of("type", type, "received", true);
        });

        server.start();

        // Give server time to start accepting connections
        Thread.sleep(100);

        client = new IpcClient(socketPath);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Nested
    class BasicCommunication {

        @Test
        void send_shouldReceiveResponse() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.send("test", null, Map.class);

            assertThat(response).containsEntry("type", "test");
            assertThat(response).containsEntry("received", true);
        }

        @Test
        void send_withPayload_shouldEchoPayload() throws Exception {
            Map<String, String> payload = Map.of("key", "value");

            @SuppressWarnings("unchecked")
            Map<String, String> response = client.send("echo", payload, Map.class);

            assertThat(response).containsEntry("key", "value");
        }

        @Test
        void send_errorRequest_shouldThrowIpcException() {
            assertThatThrownBy(() -> client.send("error", null, Map.class))
                .isInstanceOf(IpcException.class)
                .hasFieldOrPropertyWithValue("code", "TEST_ERROR")
                .hasMessage("Test error message");
        }
    }

    @Nested
    class ConnectionState {

        @Test
        void isServiceRunning_withRunningServer_shouldReturnTrue() {
            assertThat(client.isServiceRunning()).isTrue();
        }

        @Test
        void isServiceRunning_afterServerStop_shouldReturnFalse() {
            server.stop();

            assertThat(client.isServiceRunning()).isFalse();
        }

        @Test
        void close_shouldDisconnect() throws Exception {
            // First request should work
            client.send("test", null, Map.class);

            // Close and recreate client
            client.close();

            // Should be able to reconnect with new client
            client = new IpcClient(socketPath);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.send("test", null, Map.class);
            assertThat(response).containsEntry("received", true);
        }
    }

    @Nested
    class ConcurrentRequests {

        @Test
        void multipleConcurrentRequests_shouldAllSucceed() throws Exception {
            int numRequests = 5;
            CountDownLatch latch = new CountDownLatch(numRequests);
            boolean[] results = new boolean[numRequests];

            for (int i = 0; i < numRequests; i++) {
                final int index = i;
                new Thread(() -> {
                    try (IpcClient threadClient = new IpcClient(socketPath)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = threadClient.send("test", null, Map.class);
                        results[index] = response.containsKey("received");
                    } catch (Exception e) {
                        results[index] = false;
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await(10, TimeUnit.SECONDS);

            for (boolean result : results) {
                assertThat(result).isTrue();
            }
        }
    }
}
