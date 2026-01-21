package com.samsonmedia.barn.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for OperatingSystem enum.
 */
class OperatingSystemTest {

    @Test
    void current_shouldReturnValidOperatingSystem() {
        OperatingSystem os = OperatingSystem.current();
        assertThat(os).isNotNull();
        assertThat(os).isIn(OperatingSystem.LINUX, OperatingSystem.MACOS, OperatingSystem.WINDOWS);
    }

    @Test
    void current_shouldReturnConsistentValue() {
        OperatingSystem first = OperatingSystem.current();
        OperatingSystem second = OperatingSystem.current();
        assertThat(first).isSameAs(second);
    }

    @Test
    void isUnixLike_forLinux_shouldReturnTrue() {
        assertThat(OperatingSystem.LINUX.isUnixLike()).isTrue();
    }

    @Test
    void isUnixLike_forMacOS_shouldReturnTrue() {
        assertThat(OperatingSystem.MACOS.isUnixLike()).isTrue();
    }

    @Test
    void isUnixLike_forWindows_shouldReturnFalse() {
        assertThat(OperatingSystem.WINDOWS.isUnixLike()).isFalse();
    }
}
