package com.samsonmedia.barn.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ServiceHealth}.
 */
class ServiceHealthTest {

    @Test
    void running_shouldCreateHealthRecordWithCorrectValues() {
        // Arrange
        Instant startedAt = Instant.now().minusSeconds(3600);

        // Act
        ServiceHealth health = ServiceHealth.running(5, 10, startedAt);

        // Assert
        assertThat(health.running()).isTrue();
        assertThat(health.activeJobs()).isEqualTo(5);
        assertThat(health.queuedJobs()).isEqualTo(10);
        assertThat(health.startedAt()).isEqualTo(startedAt);
        assertThat(health.uptimeSeconds()).isGreaterThanOrEqualTo(3600);
    }

    @Test
    void running_withZeroJobs_shouldCreateHealthRecord() {
        // Arrange
        Instant startedAt = Instant.now();

        // Act
        ServiceHealth health = ServiceHealth.running(0, 0, startedAt);

        // Assert
        assertThat(health.running()).isTrue();
        assertThat(health.activeJobs()).isZero();
        assertThat(health.queuedJobs()).isZero();
    }

    @Test
    void stopped_shouldCreateHealthRecordWithDefaults() {
        // Act
        ServiceHealth health = ServiceHealth.stopped();

        // Assert
        assertThat(health.running()).isFalse();
        assertThat(health.activeJobs()).isZero();
        assertThat(health.queuedJobs()).isZero();
        assertThat(health.startedAt()).isNull();
        assertThat(health.uptimeSeconds()).isZero();
    }

    @Test
    void uptimeSeconds_shouldBeCalculatedFromStartTime() {
        // Arrange
        Instant twoMinutesAgo = Instant.now().minusSeconds(120);

        // Act
        ServiceHealth health = ServiceHealth.running(1, 2, twoMinutesAgo);

        // Assert
        // Allow for slight timing differences
        assertThat(health.uptimeSeconds()).isBetween(119L, 122L);
    }
}
