package com.samsonmedia.barn.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests for JobIdGenerator.
 */
class JobIdGeneratorTest {

    @Test
    void generate_shouldReturnIdWithCorrectPrefix() {
        String id = JobIdGenerator.generate();

        assertThat(id).startsWith("job-");
    }

    @Test
    void generate_shouldReturnIdWithCorrectLength() {
        String id = JobIdGenerator.generate();

        assertThat(id).hasSize(12); // "job-" (4) + 8 hex chars
    }

    @Test
    void generate_shouldReturnIdWithHexSuffix() {
        String id = JobIdGenerator.generate();
        String suffix = id.substring(4);

        assertThat(suffix).matches("[0-9a-f]{8}");
    }

    @Test
    void generate_shouldProduceUniqueIds() {
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            ids.add(JobIdGenerator.generate());
        }

        assertThat(ids).hasSize(1000);
    }

    @Test
    void isValid_withValidId_shouldReturnTrue() {
        assertThat(JobIdGenerator.isValid("job-12345678")).isTrue();
        assertThat(JobIdGenerator.isValid("job-abcdef01")).isTrue();
        assertThat(JobIdGenerator.isValid("job-9f83c012")).isTrue();
    }

    @Test
    void isValid_withNull_shouldReturnFalse() {
        assertThat(JobIdGenerator.isValid(null)).isFalse();
    }

    @Test
    void isValid_withWrongPrefix_shouldReturnFalse() {
        assertThat(JobIdGenerator.isValid("task-12345678")).isFalse();
        assertThat(JobIdGenerator.isValid("12345678")).isFalse();
    }

    @Test
    void isValid_withWrongLength_shouldReturnFalse() {
        assertThat(JobIdGenerator.isValid("job-1234567")).isFalse();
        assertThat(JobIdGenerator.isValid("job-123456789")).isFalse();
    }

    @Test
    void isValid_withInvalidHexChars_shouldReturnFalse() {
        assertThat(JobIdGenerator.isValid("job-ABCDEF01")).isFalse();
        assertThat(JobIdGenerator.isValid("job-ghijklmn")).isFalse();
        assertThat(JobIdGenerator.isValid("job-12-45678")).isFalse();
    }

    @Test
    void generate_shouldPassOwnValidation() {
        for (int i = 0; i < 100; i++) {
            String id = JobIdGenerator.generate();
            assertThat(JobIdGenerator.isValid(id))
                .as("Generated ID '%s' should be valid", id)
                .isTrue();
        }
    }
}
