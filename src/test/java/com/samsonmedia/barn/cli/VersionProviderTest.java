package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionProviderTest {

    @Test
    void getVersion_shouldReturnVersionFromProperties() {
        // Arrange
        var provider = new VersionProvider();

        // Act
        String[] version = provider.getVersion();

        // Assert
        assertThat(version).hasSize(1);
        assertThat(version[0]).startsWith("barn ");
        assertThat(version[0]).doesNotContain("unknown");
        assertThat(version[0]).doesNotContain("${");
    }
}
