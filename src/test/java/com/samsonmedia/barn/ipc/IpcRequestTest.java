package com.samsonmedia.barn.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for IpcRequest types.
 */
class IpcRequestTest {

    @Nested
    class RunJobRequest {

        @Test
        void runJob_shouldStoreAllFields() {
            var request = new IpcRequest.RunJob(
                List.of("echo", "hello"),
                "test-tag",
                3,
                30
            );

            assertThat(request.command()).containsExactly("echo", "hello");
            assertThat(request.tag()).isEqualTo("test-tag");
            assertThat(request.maxRetries()).isEqualTo(3);
            assertThat(request.retryDelaySeconds()).isEqualTo(30);
        }

        @Test
        void runJob_shouldAllowNullTag() {
            var request = new IpcRequest.RunJob(
                List.of("echo", "hello"),
                null,
                0,
                0
            );

            assertThat(request.tag()).isNull();
        }
    }

    @Nested
    class GetStatusRequest {

        @Test
        void getStatus_shouldStoreAllFields() {
            var request = new IpcRequest.GetStatus("my-tag", "RUNNING", 10);

            assertThat(request.tag()).isEqualTo("my-tag");
            assertThat(request.state()).isEqualTo("RUNNING");
            assertThat(request.limit()).isEqualTo(10);
        }

        @Test
        void getStatus_shouldAllowNullFilters() {
            var request = new IpcRequest.GetStatus(null, null, null);

            assertThat(request.tag()).isNull();
            assertThat(request.state()).isNull();
            assertThat(request.limit()).isNull();
        }
    }

    @Nested
    class GetJobRequest {

        @Test
        void getJob_shouldStoreAllFields() {
            var request = new IpcRequest.GetJob("job-123", true, true);

            assertThat(request.jobId()).isEqualTo("job-123");
            assertThat(request.includeLogs()).isTrue();
            assertThat(request.includeManifest()).isTrue();
        }
    }

    @Nested
    class KillJobRequest {

        @Test
        void killJob_shouldStoreAllFields() {
            var request = new IpcRequest.KillJob("job-123", true);

            assertThat(request.jobId()).isEqualTo("job-123");
            assertThat(request.force()).isTrue();
        }
    }

    @Nested
    class CleanJobsRequest {

        @Test
        void cleanJobs_shouldStoreAllFields() {
            var request = new IpcRequest.CleanJobs(true, "24h", true, "job-123", false);

            assertThat(request.all()).isTrue();
            assertThat(request.olderThan()).isEqualTo("24h");
            assertThat(request.includeFailed()).isTrue();
            assertThat(request.jobId()).isEqualTo("job-123");
            assertThat(request.dryRun()).isFalse();
        }
    }

    @Nested
    class ServiceRequests {

        @Test
        void getServiceStatus_shouldBeInstantiable() {
            var request = new IpcRequest.GetServiceStatus();
            assertThat(request).isNotNull();
        }

        @Test
        void shutdown_shouldBeInstantiable() {
            var request = new IpcRequest.Shutdown();
            assertThat(request).isNotNull();
        }

        @Test
        void reload_shouldBeInstantiable() {
            var request = new IpcRequest.Reload();
            assertThat(request).isNotNull();
        }
    }
}
