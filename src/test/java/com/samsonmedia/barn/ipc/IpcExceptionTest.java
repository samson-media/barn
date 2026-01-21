package com.samsonmedia.barn.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for IpcException.
 */
class IpcExceptionTest {

    @Nested
    class Construction {

        @Test
        void constructor_shouldStoreCodeAndMessage() {
            var ex = new IpcException("TEST_CODE", "Test message");

            assertThat(ex.getCode()).isEqualTo("TEST_CODE");
            assertThat(ex.getMessage()).isEqualTo("Test message");
        }

        @Test
        void constructor_withCause_shouldStoreCause() {
            var cause = new RuntimeException("Underlying error");
            var ex = new IpcException("TEST_CODE", "Test message", cause);

            assertThat(ex.getCode()).isEqualTo("TEST_CODE");
            assertThat(ex.getMessage()).isEqualTo("Test message");
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        void constructor_withNullCode_shouldThrow() {
            assertThatThrownBy(() -> new IpcException(null, "message"))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withNullMessage_shouldNotThrow() {
            var ex = new IpcException("CODE", null);

            assertThat(ex.getCode()).isEqualTo("CODE");
            assertThat(ex.getMessage()).isNull();
        }
    }

    @Nested
    class ErrorCodes {

        @Test
        void errorCodes_shouldBeAccessible() {
            assertThat(IpcErrorCode.SERVICE_NOT_RUNNING).isEqualTo("SERVICE_NOT_RUNNING");
            assertThat(IpcErrorCode.JOB_NOT_FOUND).isEqualTo("JOB_NOT_FOUND");
            assertThat(IpcErrorCode.INVALID_REQUEST).isEqualTo("INVALID_REQUEST");
            assertThat(IpcErrorCode.INTERNAL_ERROR).isEqualTo("INTERNAL_ERROR");
            assertThat(IpcErrorCode.JOB_NOT_RUNNING).isEqualTo("JOB_NOT_RUNNING");
            assertThat(IpcErrorCode.CONNECTION_FAILED).isEqualTo("CONNECTION_FAILED");
            assertThat(IpcErrorCode.TIMEOUT).isEqualTo("TIMEOUT");
        }
    }
}
