package com.samsonmedia.barn.ipc;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wrapper class for IPC messages (both requests and responses).
 *
 * <p>The JSON format is:
 * <pre>
 * {
 *   "type": "run_job",
 *   "payload": { ... }
 * }
 * </pre>
 *
 * <p>For responses:
 * <pre>
 * {
 *   "status": "ok",
 *   "payload": { ... }
 * }
 * </pre>
 *
 * <p>Or for errors:
 * <pre>
 * {
 *   "status": "error",
 *   "error": { "code": "...", "message": "..." }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IpcMessage {

    private String type;
    private String status;
    private Object payload;
    @JsonProperty("error")
    private Map<String, String> error;

    /**
     * Default constructor for JSON deserialization.
     */
    public IpcMessage() {
        // For Jackson deserialization
    }

    /**
     * Creates a request message.
     *
     * @param type the request type
     * @param payload the request payload
     */
    private IpcMessage(String type, Object payload) {
        this.type = Objects.requireNonNull(type);
        this.payload = payload;
    }

    /**
     * Creates a response message.
     *
     * @param status the response status
     * @param payload the response payload
     * @param error the error details (for error responses)
     */
    private IpcMessage(String status, Object payload, Map<String, String> error) {
        this.status = Objects.requireNonNull(status);
        this.payload = payload;
        this.error = error;
    }

    /**
     * Creates a request message.
     *
     * @param type the request type
     * @param payload the request payload
     * @return the message
     */
    public static IpcMessage request(String type, Object payload) {
        return new IpcMessage(type, payload);
    }

    /**
     * Creates a successful response message.
     *
     * @param payload the response payload
     * @return the message
     */
    public static IpcMessage okResponse(Object payload) {
        return new IpcMessage("ok", payload, null);
    }

    /**
     * Creates an error response message.
     *
     * @param code the error code
     * @param message the error message
     * @return the message
     */
    public static IpcMessage errorResponse(String code, String message) {
        IpcMessage msg = new IpcMessage();
        msg.status = "error";
        msg.error = Map.of("code", code, "message", message);
        return msg;
    }

    /**
     * Gets the request type (for request messages).
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the request type.
     *
     * @param type the type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the response status (for response messages).
     *
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the response status.
     *
     * @param status the status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the payload.
     *
     * @return the payload
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * Sets the payload.
     *
     * @param payload the payload
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     * Gets the error details (for error responses).
     *
     * @return the error details
     */
    public Map<String, String> getError() {
        return error;
    }

    /**
     * Sets the error details.
     *
     * @param error the error details
     */
    public void setError(Map<String, String> error) {
        this.error = error;
    }

    /**
     * Checks if this is a successful response.
     *
     * @return true if status is "ok"
     */
    @JsonIgnore
    public boolean isOk() {
        return "ok".equals(status);
    }

    /**
     * Checks if this is an error response.
     *
     * @return true if status is "error"
     */
    @JsonIgnore
    public boolean isError() {
        return "error".equals(status);
    }

    /**
     * Gets the error code (for error responses).
     *
     * @return the error code, or null if not an error
     */
    @JsonIgnore
    public String getErrorCode() {
        return error != null ? error.get("code") : null;
    }

    /**
     * Gets the error message (for error responses).
     *
     * @return the error message, or null if not an error
     */
    @JsonIgnore
    public String getErrorMessage() {
        return error != null ? error.get("message") : null;
    }
}
