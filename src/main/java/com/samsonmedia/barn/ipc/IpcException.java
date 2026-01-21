package com.samsonmedia.barn.ipc;

import java.util.Objects;

/**
 * Exception thrown when IPC operations fail.
 */
public class IpcException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String code;

    /**
     * Creates an IPC exception with the given error code and message.
     *
     * @param code the error code
     * @param message the error message
     */
    public IpcException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * Creates an IPC exception with the given error code, message, and cause.
     *
     * @param code the error code
     * @param message the error message
     * @param cause the underlying cause
     */
    public IpcException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    public String getCode() {
        return code;
    }
}
