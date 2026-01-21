package com.samsonmedia.barn.ipc;

/**
 * Sealed interface for IPC responses from service to CLI.
 *
 * @param <T> the payload type for success responses
 */
public sealed interface IpcResponse<T> {

    /**
     * Successful response with payload.
     *
     * @param <T> the payload type
     * @param payload the response data
     */
    record Ok<T>(T payload) implements IpcResponse<T> { }

    /**
     * Error response.
     *
     * @param <T> unused type parameter for compatibility
     * @param code the error code
     * @param message the error message
     */
    record Error<T>(String code, String message) implements IpcResponse<T> { }

    /**
     * Creates a successful response with the given payload.
     *
     * @param <T> the payload type
     * @param payload the response data
     * @return a successful response
     */
    static <T> IpcResponse<T> ok(T payload) {
        return new Ok<>(payload);
    }

    /**
     * Creates an error response.
     *
     * @param <T> unused type parameter
     * @param code the error code
     * @param message the error message
     * @return an error response
     */
    static <T> IpcResponse<T> error(String code, String message) {
        return new Error<>(code, message);
    }
}
