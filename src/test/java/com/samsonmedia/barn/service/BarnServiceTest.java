package com.samsonmedia.barn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.samsonmedia.barn.config.CleanupConfig;
import com.samsonmedia.barn.config.Config;
import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.config.LoadLevelConfig;
import com.samsonmedia.barn.config.LogLevel;
import com.samsonmedia.barn.config.ServiceConfig;
import com.samsonmedia.barn.config.StorageConfig;

/**
 * Tests for {@link BarnService}.
 */
class BarnServiceTest {

    @TempDir
    private Path tempDir;

    private Config config;
    private Path socketPath;

    @BeforeEach
    void setUp() throws IOException {
        socketPath = tempDir.resolve("barn.sock");

        ServiceConfig serviceConfig = new ServiceConfig(
            LogLevel.INFO,
            2,
            5,
            socketPath,
            30
        );

        JobsConfig jobsConfig = JobsConfig.withDefaults();
        CleanupConfig cleanupConfig = new CleanupConfig(false, 60, 24, false, 168);
        StorageConfig storageConfig = new StorageConfig(tempDir, 50, false);

        config = new Config(serviceConfig, jobsConfig, cleanupConfig, storageConfig,
            LoadLevelConfig.withDefaults());
    }

    @Test
    void constructor_withNullConfig_shouldThrowException() {
        assertThatThrownBy(() -> new BarnService(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("config");
    }

    @Test
    void constructor_withValidConfig_shouldCreateService() {
        // Act
        BarnService service = new BarnService(config);

        // Assert
        assertThat(service).isNotNull();
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void isRunning_beforeStart_shouldReturnFalse() {
        // Arrange
        BarnService service = new BarnService(config);

        // Assert
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void getHealth_beforeStart_shouldReturnStopped() {
        // Arrange
        BarnService service = new BarnService(config);

        // Act
        ServiceHealth health = service.getHealth();

        // Assert
        assertThat(health.running()).isFalse();
        assertThat(health.activeJobs()).isZero();
        assertThat(health.queuedJobs()).isZero();
    }

    @Test
    void getRepository_shouldReturnRepository() {
        // Arrange
        BarnService service = new BarnService(config);

        // Assert
        assertThat(service.getRepository()).isNotNull();
    }

    @Test
    void getScheduler_shouldReturnScheduler() {
        // Arrange
        BarnService service = new BarnService(config);

        // Assert
        assertThat(service.getScheduler()).isNotNull();
    }

    @Test
    void getDirectories_shouldReturnDirectories() {
        // Arrange
        BarnService service = new BarnService(config);

        // Assert
        assertThat(service.getDirectories()).isNotNull();
    }

    @Test
    void start_withValidConfig_shouldStartService() throws IOException {
        // Arrange
        BarnService service = new BarnService(config);

        try {
            // Act
            service.start();

            // Assert
            assertThat(service.isRunning()).isTrue();
            assertThat(service.getHealth().running()).isTrue();
        } finally {
            service.stop();
        }
    }

    @Test
    void start_whenAlreadyRunning_shouldThrowException() throws IOException {
        // Arrange
        BarnService service = new BarnService(config);
        service.start();

        try {
            // Act & Assert
            assertThatThrownBy(() -> service.start())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
        } finally {
            service.stop();
        }
    }

    @Test
    void stop_whenRunning_shouldStopService() throws IOException {
        // Arrange
        BarnService service = new BarnService(config);
        service.start();
        assertThat(service.isRunning()).isTrue();

        // Act
        service.stop();

        // Assert
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void stop_whenNotRunning_shouldBeNoOp() {
        // Arrange
        BarnService service = new BarnService(config);

        // Act - should not throw
        service.stop();

        // Assert
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void reload_shouldLogMessage() throws IOException {
        // Arrange
        BarnService service = new BarnService(config);
        service.start();

        try {
            // Act - should not throw
            service.reload();
        } finally {
            service.stop();
        }
    }

    @Test
    void start_shouldInitializeDirectories() throws IOException {
        // Arrange
        BarnService service = new BarnService(config);

        try {
            // Act
            service.start();

            // Assert
            assertThat(Files.exists(tempDir.resolve("jobs"))).isTrue();
        } finally {
            service.stop();
        }
    }

    @Test
    void getHealth_whenRunning_shouldReturnHealthStatus() throws IOException {
        // Arrange
        BarnService service = new BarnService(config);
        service.start();

        try {
            // Act
            ServiceHealth health = service.getHealth();

            // Assert
            assertThat(health.running()).isTrue();
            assertThat(health.startedAt()).isNotNull();
            assertThat(health.uptimeSeconds()).isGreaterThanOrEqualTo(0);
        } finally {
            service.stop();
        }
    }
}
