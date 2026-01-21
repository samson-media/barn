package com.samsonmedia.barn.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for IpcMessage.
 */
class IpcMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    class RequestMessage {

        @Test
        void request_shouldSetTypeAndPayload() {
            var message = IpcMessage.request("run_job", Map.of("command", "echo"));

            assertThat(message.getType()).isEqualTo("run_job");
            assertThat(message.getPayload()).isEqualTo(Map.of("command", "echo"));
        }

        @Test
        void request_shouldAllowNullPayload() {
            var message = IpcMessage.request("shutdown", null);

            assertThat(message.getType()).isEqualTo("shutdown");
            assertThat(message.getPayload()).isNull();
        }
    }

    @Nested
    class ResponseMessage {

        @Test
        void okResponse_shouldSetStatusAndPayload() {
            var message = IpcMessage.okResponse(Map.of("job_id", "job-123"));

            assertThat(message.getStatus()).isEqualTo("ok");
            assertThat(message.getPayload()).isEqualTo(Map.of("job_id", "job-123"));
            assertThat(message.isOk()).isTrue();
            assertThat(message.isError()).isFalse();
        }

        @Test
        void errorResponse_shouldSetStatusAndError() {
            var message = IpcMessage.errorResponse("JOB_NOT_FOUND", "Job not found");

            assertThat(message.getStatus()).isEqualTo("error");
            assertThat(message.isOk()).isFalse();
            assertThat(message.isError()).isTrue();
            assertThat(message.getErrorCode()).isEqualTo("JOB_NOT_FOUND");
            assertThat(message.getErrorMessage()).isEqualTo("Job not found");
        }
    }

    @Nested
    class JsonSerialization {

        @Test
        void request_shouldSerializeToJson() throws Exception {
            var message = IpcMessage.request("run_job", Map.of("tag", "test"));

            String json = objectMapper.writeValueAsString(message);

            assertThat(json).contains("\"type\":\"run_job\"");
            assertThat(json).contains("\"tag\":\"test\"");
        }

        @Test
        void okResponse_shouldSerializeToJson() throws Exception {
            var message = IpcMessage.okResponse(Map.of("job_id", "job-123"));

            String json = objectMapper.writeValueAsString(message);

            assertThat(json).contains("\"status\":\"ok\"");
            assertThat(json).contains("\"job_id\":\"job-123\"");
        }

        @Test
        void errorResponse_shouldSerializeToJson() throws Exception {
            var message = IpcMessage.errorResponse("TEST_ERROR", "Test message");

            String json = objectMapper.writeValueAsString(message);

            assertThat(json).contains("\"status\":\"error\"");
            assertThat(json).contains("\"code\":\"TEST_ERROR\"");
            assertThat(json).contains("\"message\":\"Test message\"");
        }

        @Test
        void request_shouldDeserializeFromJson() throws Exception {
            String json = "{\"type\":\"get_status\",\"payload\":{\"limit\":10}}";

            IpcMessage message = objectMapper.readValue(json, IpcMessage.class);

            assertThat(message.getType()).isEqualTo("get_status");
            assertThat(message.getPayload()).isNotNull();
        }

        @Test
        void okResponse_shouldDeserializeFromJson() throws Exception {
            String json = "{\"status\":\"ok\",\"payload\":{\"count\":5}}";

            IpcMessage message = objectMapper.readValue(json, IpcMessage.class);

            assertThat(message.getStatus()).isEqualTo("ok");
            assertThat(message.isOk()).isTrue();
        }

        @Test
        void errorResponse_shouldDeserializeFromJson() throws Exception {
            String json = "{\"status\":\"error\",\"error\":{\"code\":\"TEST\",\"message\":\"Fail\"}}";

            IpcMessage message = objectMapper.readValue(json, IpcMessage.class);

            assertThat(message.getStatus()).isEqualTo("error");
            assertThat(message.isError()).isTrue();
            assertThat(message.getErrorCode()).isEqualTo("TEST");
            assertThat(message.getErrorMessage()).isEqualTo("Fail");
        }
    }

    @Nested
    class ErrorHelpers {

        @Test
        void getErrorCode_withNoError_shouldReturnNull() {
            var message = IpcMessage.okResponse(null);

            assertThat(message.getErrorCode()).isNull();
        }

        @Test
        void getErrorMessage_withNoError_shouldReturnNull() {
            var message = IpcMessage.okResponse(null);

            assertThat(message.getErrorMessage()).isNull();
        }
    }
}
