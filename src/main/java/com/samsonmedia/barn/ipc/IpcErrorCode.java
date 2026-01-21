package com.samsonmedia.barn.ipc;

/**
 * Error codes for IPC responses.
 */
public final class IpcErrorCode {

    /** Service is not running. */
    public static final String SERVICE_NOT_RUNNING = "SERVICE_NOT_RUNNING";

    /** Job ID doesn't exist. */
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";

    /** Malformed request. */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** Unexpected server error. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /** Job is not in running state. */
    public static final String JOB_NOT_RUNNING = "JOB_NOT_RUNNING";

    /** Connection to service failed. */
    public static final String CONNECTION_FAILED = "CONNECTION_FAILED";

    /** Request timed out. */
    public static final String TIMEOUT = "TIMEOUT";

    private IpcErrorCode() {
        // Constants class - prevent instantiation
    }
}
