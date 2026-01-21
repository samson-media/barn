package com.samsonmedia.barn.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for WindowsServiceManager.
 */
class WindowsServiceManagerTest {

    private WindowsServiceManager manager;

    @BeforeEach
    void setUp() {
        manager = new WindowsServiceManager();
    }

    @Nested
    class GetServiceName {

        @Test
        void getServiceName_shouldReturnBarn() {
            assertThat(manager.getServiceName()).isEqualTo("barn");
        }
    }

    @Nested
    class GetDisplayName {

        @Test
        void getDisplayName_shouldReturnCorrectName() {
            assertThat(manager.getDisplayName()).isEqualTo("Barn Job Daemon");
        }
    }

    @Nested
    class HasWindowsServices {

        @Test
        void hasWindowsServices_shouldReturnBoolean() {
            // This test just verifies the method doesn't throw
            boolean result = WindowsServiceManager.hasWindowsServices();
            assertThat(result).isIn(true, false);
        }

        @Test
        void hasWindowsServices_shouldReturnTrueOnWindows() {
            String osName = System.getProperty("os.name", "").toLowerCase();
            boolean expected = osName.contains("windows");
            assertThat(WindowsServiceManager.hasWindowsServices()).isEqualTo(expected);
        }
    }

    @Nested
    class IsUnderServiceManager {

        @Test
        void isUnderServiceManager_shouldReturnBoolean() {
            // This test just verifies the method doesn't throw
            boolean result = WindowsServiceManager.isUnderServiceManager();
            assertThat(result).isIn(true, false);
        }
    }

    @Nested
    class IsAdmin {

        @Test
        void isAdmin_shouldReturnBoolean() {
            // This test just verifies the method doesn't throw
            // On non-Windows, this will return false
            boolean result = WindowsServiceManager.isAdmin();
            assertThat(result).isIn(true, false);
        }
    }

    @Nested
    class InstallValidation {

        @Test
        void install_withNullBinaryPath_shouldThrow() {
            assertThatThrownBy(() -> manager.install(null, "/tmp/barn"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("binaryPath");
        }

        @Test
        void install_withNullBarnDir_shouldThrow() {
            assertThatThrownBy(() -> manager.install("/usr/local/bin/barn", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("barnDir");
        }
    }

    @Nested
    class ServiceState {

        @Test
        void serviceState_shouldHaveExpectedValues() {
            assertThat(WindowsServiceManager.ServiceState.RUNNING).isNotNull();
            assertThat(WindowsServiceManager.ServiceState.STOPPED).isNotNull();
            assertThat(WindowsServiceManager.ServiceState.START_PENDING).isNotNull();
            assertThat(WindowsServiceManager.ServiceState.STOP_PENDING).isNotNull();
            assertThat(WindowsServiceManager.ServiceState.PAUSED).isNotNull();
            assertThat(WindowsServiceManager.ServiceState.UNKNOWN).isNotNull();
        }

        @Test
        void serviceState_shouldHaveSixValues() {
            assertThat(WindowsServiceManager.ServiceState.values()).hasSize(6);
        }
    }

    @Nested
    class WindowsServiceExceptionTest {

        @Test
        void constructor_withMessage_shouldSetMessage() {
            var exception = new WindowsServiceManager.WindowsServiceException("test message");
            assertThat(exception.getMessage()).isEqualTo("test message");
        }

        @Test
        void constructor_withCause_shouldSetCause() {
            var cause = new RuntimeException("cause");
            var exception = new WindowsServiceManager.WindowsServiceException("test message", cause);
            assertThat(exception.getMessage()).isEqualTo("test message");
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    class ServiceOperationsOnNonWindows {

        @Test
        void isInstalled_onNonWindows_shouldReturnFalse() {
            if (!WindowsServiceManager.hasWindowsServices()) {
                // On non-Windows, this should return false (service not installed)
                assertThat(manager.isInstalled()).isFalse();
            }
        }

        @Test
        void isRunning_onNonWindows_shouldReturnFalse() {
            if (!WindowsServiceManager.hasWindowsServices()) {
                // On non-Windows, this should return false
                assertThat(manager.isRunning()).isFalse();
            }
        }

        @Test
        void getState_onNonWindows_shouldReturnNull() {
            if (!WindowsServiceManager.hasWindowsServices()) {
                // On non-Windows, this should return null
                assertThat(manager.getState()).isNull();
            }
        }
    }
}
