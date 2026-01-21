package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for ConfigDefaults.
 */
class ConfigDefaultsTest {

    @Test
    void getDefaultBaseDir_shouldReturnNonNullPath() {
        Path baseDir = ConfigDefaults.getDefaultBaseDir();
        assertThat(baseDir).isNotNull();
        assertThat(baseDir.toString()).isNotEmpty();
    }

    @Test
    void getDefaultIpcSocket_shouldReturnNonNullPath() {
        Path socket = ConfigDefaults.getDefaultIpcSocket();
        assertThat(socket).isNotNull();
        assertThat(socket.toString()).isNotEmpty();
    }

    @Test
    void getSystemConfigPath_shouldReturnNonNullPath() {
        Path configPath = ConfigDefaults.getSystemConfigPath();
        assertThat(configPath).isNotNull();
        assertThat(configPath.toString()).contains("barn.conf");
    }

    @Test
    void getUserConfigPath_shouldReturnNonNullPath() {
        Path configPath = ConfigDefaults.getUserConfigPath();
        assertThat(configPath).isNotNull();
        assertThat(configPath.toString()).contains("barn.conf");
    }

    @Test
    void getConfigSearchPaths_shouldReturnNonEmptyList() {
        List<Path> paths = ConfigDefaults.getConfigSearchPaths();
        assertThat(paths).isNotEmpty();
        assertThat(paths).hasSize(2);
    }

    @Test
    void getConfigSearchPaths_shouldIncludeSystemAndUserPaths() {
        List<Path> paths = ConfigDefaults.getConfigSearchPaths();
        assertThat(paths).contains(ConfigDefaults.getSystemConfigPath());
        assertThat(paths).contains(ConfigDefaults.getUserConfigPath());
    }

    @Test
    void getDefaultBaseDir_onUnix_shouldBeInTmp() {
        if (OperatingSystem.current().isUnixLike()) {
            Path baseDir = ConfigDefaults.getDefaultBaseDir();
            assertThat(baseDir.toString()).startsWith("/tmp");
        }
    }

    @Test
    void getDefaultIpcSocket_onUnix_shouldBeUnixSocket() {
        if (OperatingSystem.current().isUnixLike()) {
            Path socket = ConfigDefaults.getDefaultIpcSocket();
            assertThat(socket.toString()).contains("barn.sock");
        }
    }

    @Test
    void getDefaultIpcSocket_onWindows_shouldBeNamedPipe() {
        if (OperatingSystem.current() == OperatingSystem.WINDOWS) {
            Path socket = ConfigDefaults.getDefaultIpcSocket();
            assertThat(socket.toString()).contains("pipe");
        }
    }
}
