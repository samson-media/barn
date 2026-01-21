package com.samsonmedia.barn.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.samsonmedia.barn.config.JobsConfig;

/**
 * Tests for JobManifest record.
 */
class JobManifestTest {

    @Test
    void constructor_withValidValues_shouldCreateManifest() {
        Instant now = Instant.now();
        JobManifest manifest = new JobManifest(
            "job-12345",
            List.of("echo", "hello"),
            "test-tag",
            now,
            3, 30, 2.0
        );

        assertThat(manifest.id()).isEqualTo("job-12345");
        assertThat(manifest.command()).containsExactly("echo", "hello");
        assertThat(manifest.tag()).isEqualTo("test-tag");
        assertThat(manifest.createdAt()).isEqualTo(now);
        assertThat(manifest.maxRetries()).isEqualTo(3);
        assertThat(manifest.retryDelaySeconds()).isEqualTo(30);
        assertThat(manifest.retryBackoffMultiplier()).isEqualTo(2.0);
    }

    @Test
    void constructor_withNullId_shouldThrowException() {
        assertThatThrownBy(() -> new JobManifest(
            null, List.of("echo"), null, Instant.now(), 3, 30, 2.0))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id");
    }

    @Test
    void constructor_withBlankId_shouldThrowException() {
        assertThatThrownBy(() -> new JobManifest(
            "  ", List.of("echo"), null, Instant.now(), 3, 30, 2.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void constructor_withEmptyCommand_shouldThrowException() {
        assertThatThrownBy(() -> new JobManifest(
            "job-12345", List.of(), null, Instant.now(), 3, 30, 2.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void constructor_withNegativeMaxRetries_shouldThrowException() {
        assertThatThrownBy(() -> new JobManifest(
            "job-12345", List.of("echo"), null, Instant.now(), -1, 30, 2.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxRetries");
    }

    @Test
    void constructor_withNegativeRetryDelay_shouldThrowException() {
        assertThatThrownBy(() -> new JobManifest(
            "job-12345", List.of("echo"), null, Instant.now(), 3, -1, 2.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retryDelaySeconds");
    }

    @Test
    void constructor_withBackoffMultiplierLessThanOne_shouldThrowException() {
        assertThatThrownBy(() -> new JobManifest(
            "job-12345", List.of("echo"), null, Instant.now(), 3, 30, 0.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retryBackoffMultiplier");
    }

    @Test
    void create_withConfig_shouldUseConfigDefaults() {
        JobsConfig config = JobsConfig.withDefaults();

        JobManifest manifest = JobManifest.create("job-12345", List.of("echo"), "tag", config);

        assertThat(manifest.maxRetries()).isEqualTo(config.maxRetries());
        assertThat(manifest.retryDelaySeconds()).isEqualTo(config.retryDelaySeconds());
        assertThat(manifest.retryBackoffMultiplier()).isEqualTo(config.retryBackoffMultiplier());
    }

    @Test
    void create_withCustomValues_shouldUseCustomValues() {
        JobManifest manifest = JobManifest.create(
            "job-12345", List.of("echo"), "tag", 5, 60, 3.0);

        assertThat(manifest.maxRetries()).isEqualTo(5);
        assertThat(manifest.retryDelaySeconds()).isEqualTo(60);
        assertThat(manifest.retryBackoffMultiplier()).isEqualTo(3.0);
    }

    @Test
    void calculateRetryDelay_shouldUseExponentialBackoff() {
        JobManifest manifest = new JobManifest(
            "job-12345", List.of("echo"), null, Instant.now(), 3, 30, 2.0);

        assertThat(manifest.calculateRetryDelay(0)).isEqualTo(30);  // 30 * 2^0
        assertThat(manifest.calculateRetryDelay(1)).isEqualTo(60);  // 30 * 2^1
        assertThat(manifest.calculateRetryDelay(2)).isEqualTo(120); // 30 * 2^2
    }

    @Test
    void calculateRetryDelay_withNegativeCount_shouldThrowException() {
        JobManifest manifest = new JobManifest(
            "job-12345", List.of("echo"), null, Instant.now(), 3, 30, 2.0);

        assertThatThrownBy(() -> manifest.calculateRetryDelay(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canRetry_withRetriesAvailable_shouldReturnTrue() {
        JobManifest manifest = new JobManifest(
            "job-12345", List.of("echo"), null, Instant.now(), 3, 30, 2.0);

        assertThat(manifest.canRetry(0)).isTrue();
        assertThat(manifest.canRetry(1)).isTrue();
        assertThat(manifest.canRetry(2)).isTrue();
    }

    @Test
    void canRetry_withNoRetriesLeft_shouldReturnFalse() {
        JobManifest manifest = new JobManifest(
            "job-12345", List.of("echo"), null, Instant.now(), 3, 30, 2.0);

        assertThat(manifest.canRetry(3)).isFalse();
        assertThat(manifest.canRetry(4)).isFalse();
    }

    @Test
    void canRetry_withZeroMaxRetries_shouldAlwaysReturnFalse() {
        JobManifest manifest = new JobManifest(
            "job-12345", List.of("echo"), null, Instant.now(), 0, 30, 2.0);

        assertThat(manifest.canRetry(0)).isFalse();
    }
}
