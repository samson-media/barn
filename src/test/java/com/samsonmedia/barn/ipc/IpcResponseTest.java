package com.samsonmedia.barn.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for IpcResponse types.
 */
class IpcResponseTest {

    @Nested
    class OkResponse {

        @Test
        void ok_shouldStorePayload() {
            var response = new IpcResponse.Ok<>("test-payload");

            assertThat(response.payload()).isEqualTo("test-payload");
        }

        @Test
        void ok_shouldAllowNullPayload() {
            var response = new IpcResponse.Ok<String>(null);

            assertThat(response.payload()).isNull();
        }

        @Test
        void okFactory_shouldCreateOkResponse() {
            IpcResponse<String> response = IpcResponse.ok("test");

            assertThat(response).isInstanceOf(IpcResponse.Ok.class);
            assertThat(((IpcResponse.Ok<String>) response).payload()).isEqualTo("test");
        }
    }

    @Nested
    class ErrorResponse {

        @Test
        void error_shouldStoreCodeAndMessage() {
            var response = new IpcResponse.Error<String>("TEST_ERROR", "Test message");

            assertThat(response.code()).isEqualTo("TEST_ERROR");
            assertThat(response.message()).isEqualTo("Test message");
        }

        @Test
        void errorFactory_shouldCreateErrorResponse() {
            IpcResponse<String> response = IpcResponse.error("TEST_ERROR", "Test message");

            assertThat(response).isInstanceOf(IpcResponse.Error.class);
            IpcResponse.Error<String> error = (IpcResponse.Error<String>) response;
            assertThat(error.code()).isEqualTo("TEST_ERROR");
            assertThat(error.message()).isEqualTo("Test message");
        }
    }
}
