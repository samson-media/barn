package com.samsonmedia.barn.ipc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * IPC server for receiving requests from CLI clients.
 *
 * <p>Uses Unix domain sockets on Linux/macOS.
 */
public class IpcServer {

    private static final Logger LOG = LoggerFactory.getLogger(IpcServer.class);

    private final Path socketPath;
    private final RequestHandler handler;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running;
    private final ExecutorService executor;
    private ServerSocketChannel serverChannel;

    /**
     * Creates an IPC server.
     *
     * @param socketPath the Unix socket path
     * @param handler the request handler
     */
    public IpcServer(Path socketPath, RequestHandler handler) {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath must not be null");
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
        this.objectMapper = createObjectMapper();
        this.running = new AtomicBoolean(false);
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ipc-handler");
            t.setDaemon(true);
            return t;
        });
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Starts the IPC server.
     *
     * @throws IOException if starting fails
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Server already running");
        }

        // Clean up existing socket file
        Files.deleteIfExists(socketPath);

        // Create parent directories
        Files.createDirectories(socketPath.getParent());

        // Create and bind the server socket
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
        serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverChannel.bind(address);

        LOG.info("IPC server started on {}", socketPath);

        // Start accept loop in separate thread
        executor.submit(this::acceptLoop);
    }

    /**
     * Stops the IPC server.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        LOG.info("Stopping IPC server");

        // Close server socket to interrupt accept
        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (IOException e) {
                LOG.warn("Error closing server socket: {}", e.getMessage());
            }
        }

        // Shutdown executor
        executor.shutdownNow();

        // Clean up socket file
        try {
            Files.deleteIfExists(socketPath);
        } catch (IOException e) {
            LOG.warn("Error deleting socket file: {}", e.getMessage());
        }

        LOG.info("IPC server stopped");
    }

    /**
     * Checks if the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the socket path.
     *
     * @return the socket path
     */
    public Path getSocketPath() {
        return socketPath;
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                SocketChannel clientChannel = serverChannel.accept();
                LOG.debug("Accepted IPC connection");
                executor.submit(() -> handleConnection(clientChannel));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection: {}", e.getMessage());
                }
                // If not running, this is expected during shutdown
            }
        }
    }

    private void handleConnection(SocketChannel clientChannel) {
        try (clientChannel) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(Channels.newInputStream(clientChannel), StandardCharsets.UTF_8)
            );
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(Channels.newOutputStream(clientChannel), StandardCharsets.UTF_8),
                true
            );

            String line;
            while ((line = reader.readLine()) != null) {
                LOG.debug("Received IPC request: {}", line);

                IpcMessage response;
                try {
                    IpcMessage request = objectMapper.readValue(line, IpcMessage.class);
                    response = handleRequest(request);
                } catch (Exception e) {
                    LOG.error("Error handling request: {}", e.getMessage(), e);
                    response = IpcMessage.errorResponse(IpcErrorCode.INTERNAL_ERROR, e.getMessage());
                }

                String responseJson = objectMapper.writeValueAsString(response);
                LOG.debug("Sending IPC response: {}", responseJson);
                writer.println(responseJson);
            }
        } catch (IOException e) {
            LOG.debug("Connection closed: {}", e.getMessage());
        }
    }

    private IpcMessage handleRequest(IpcMessage request) {
        String type = request.getType();
        if (type == null) {
            return IpcMessage.errorResponse(IpcErrorCode.INVALID_REQUEST, "Missing request type");
        }

        try {
            Object result = handler.handle(type, request.getPayload());
            return IpcMessage.okResponse(result);
        } catch (IpcException e) {
            return IpcMessage.errorResponse(e.getCode(), e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error handling request: {}", e.getMessage(), e);
            return IpcMessage.errorResponse(IpcErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Interface for handling IPC requests.
     */
    @FunctionalInterface
    public interface RequestHandler {

        /**
         * Handles an IPC request.
         *
         * @param type the request type
         * @param payload the request payload (may be null)
         * @return the response payload
         * @throws IpcException if the request fails
         */
        Object handle(String type, Object payload) throws IpcException;
    }
}
