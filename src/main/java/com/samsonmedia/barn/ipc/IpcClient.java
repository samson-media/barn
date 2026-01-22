package com.samsonmedia.barn.ipc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samsonmedia.barn.logging.BarnLogger;

/**
 * IPC client for communicating with the barn service.
 *
 * <p>Uses Unix domain sockets on Linux/macOS and can fall back to
 * localhost TCP on platforms that don't support Unix sockets.
 */
public class IpcClient implements AutoCloseable {

    private static final BarnLogger LOG = BarnLogger.getLogger(IpcClient.class);
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final Path socketPath;
    private final ObjectMapper objectMapper;
    private SocketChannel channel;

    /**
     * Creates an IPC client for the given socket path.
     *
     * @param socketPath the Unix socket path
     */
    public IpcClient(Path socketPath) {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath must not be null");
        this.objectMapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Sends a request and waits for a response.
     *
     * @param <T> the expected response payload type
     * @param type the request type
     * @param payload the request payload
     * @param responseType the response payload class
     * @return the response payload
     * @throws IpcException if the request fails
     */
    public <T> T send(String type, Object payload, Class<T> responseType) throws IpcException {
        try {
            ensureConnected();

            // Build and send request
            IpcMessage request = IpcMessage.request(type, payload);
            String requestJson = objectMapper.writeValueAsString(request);
            LOG.debug("Sending IPC request: {}", requestJson);

            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8),
                true
            );
            writer.println(requestJson);

            // Read response
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8)
            );
            String responseLine = reader.readLine();
            if (responseLine == null) {
                throw new IpcException(IpcErrorCode.CONNECTION_FAILED, "Connection closed by server");
            }
            LOG.debug("Received IPC response: {}", responseLine);

            // Parse response
            IpcMessage response = objectMapper.readValue(responseLine, IpcMessage.class);

            if (response.isError()) {
                throw new IpcException(response.getErrorCode(), response.getErrorMessage());
            }

            // Convert payload to expected type
            if (response.getPayload() == null) {
                return null;
            }
            return objectMapper.convertValue(response.getPayload(), responseType);

        } catch (IOException e) {
            throw new IpcException(IpcErrorCode.CONNECTION_FAILED, "IPC communication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the service is running and accessible.
     *
     * @return true if the service is running
     */
    public boolean isServiceRunning() {
        try {
            if (!Files.exists(socketPath)) {
                return false;
            }
            ensureConnected();
            return true;
        } catch (IpcException e) {
            return false;
        }
    }

    /**
     * Ensures the client is connected to the service.
     *
     * @throws IpcException if connection fails
     */
    public void ensureConnected() throws IpcException {
        if (channel != null && channel.isConnected()) {
            return;
        }

        if (!Files.exists(socketPath)) {
            throw new IpcException(IpcErrorCode.SERVICE_NOT_RUNNING,
                "Service not running (socket not found: " + socketPath + ")");
        }

        try {
            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
            channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(address);
            LOG.debug("Connected to IPC socket: {}", socketPath);
        } catch (IOException e) {
            throw new IpcException(IpcErrorCode.CONNECTION_FAILED,
                "Failed to connect to service: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the socket path.
     *
     * @return the socket path
     */
    public Path getSocketPath() {
        return socketPath;
    }

    @Override
    public void close() {
        if (channel != null) {
            try {
                channel.close();
                LOG.debug("Closed IPC connection");
            } catch (IOException e) {
                LOG.warn("Error closing IPC connection: {}", e.getMessage());
            }
            channel = null;
        }
    }

    /**
     * Gets the default socket path for the current platform.
     *
     * @return the default socket path
     */
    public static Path getDefaultSocketPath() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // Windows - use temp directory (named pipes require JNI)
            return Path.of(System.getProperty("java.io.tmpdir"), "barn", "barn.sock");
        }
        // Unix-like systems
        return Path.of("/tmp", "barn", "barn.sock");
    }
}
