package com.samsonmedia.barn.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.state.JobState;

/**
 * Tests for RetryCalculator.
 */
class RetryCalculatorTest {

    private JobsConfig defaultConfig;
    private RetryCalculator calculator;

    @BeforeEach
    void setUp() {
        defaultConfig = JobsConfig.withDefaults();
        calculator = new RetryCalculator(defaultConfig);
    }

    @Nested
    class Constructor {

        @Test
        void constructor_withNullConfig_shouldThrow() {
            assertThatThrownBy(() -> new RetryCalculator(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
        }
    }

    @Nested
    class ShouldRetry {

        @Test
        void shouldRetry_withNullJob_shouldThrow() {
            assertThatThrownBy(() -> calculator.shouldRetry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }

        @Test
        void shouldRetry_withFailedJobUnderMaxRetries_shouldReturnTrue() {
            Job job = createJobWithExitCode(1, 0); // First attempt (0 retries)
            assertThat(calculator.shouldRetry(job)).isTrue();
        }

        @Test
        void shouldRetry_withFailedJobAtMaxRetries_shouldReturnFalse() {
            Job job = createJobWithExitCode(1, 3); // Already retried 3 times (max)
            assertThat(calculator.shouldRetry(job)).isFalse();
        }

        @Test
        void shouldRetry_withSuccessfulJob_shouldReturnFalse() {
            Job job = createJobWithExitCode(0, 0);
            assertThat(calculator.shouldRetry(job)).isFalse();
        }

        @Test
        void shouldRetry_withNullExitCode_shouldReturnFalse() {
            Job job = createJobWithNullExitCode(0);
            assertThat(calculator.shouldRetry(job)).isFalse();
        }

        @Test
        void shouldRetry_withMaxRetriesZero_shouldReturnFalse() {
            JobsConfig noRetryConfig = new JobsConfig(3600, 0, 30, 2.0, List.of());
            RetryCalculator noRetryCalc = new RetryCalculator(noRetryConfig);
            Job job = createJobWithExitCode(1, 0);
            assertThat(noRetryCalc.shouldRetry(job)).isFalse();
        }

        @Test
        void shouldRetry_withExitCodeInRetryList_shouldReturnTrue() {
            JobsConfig config = new JobsConfig(3600, 3, 30, 2.0, List.of(1, 2, 3));
            RetryCalculator calc = new RetryCalculator(config);
            Job job = createJobWithExitCode(2, 0);
            assertThat(calc.shouldRetry(job)).isTrue();
        }

        @Test
        void shouldRetry_withExitCodeNotInRetryList_shouldReturnFalse() {
            JobsConfig config = new JobsConfig(3600, 3, 30, 2.0, List.of(1, 2, 3));
            RetryCalculator calc = new RetryCalculator(config);
            Job job = createJobWithExitCode(127, 0); // Exit code not in list
            assertThat(calc.shouldRetry(job)).isFalse();
        }

        @Test
        void shouldRetry_withEmptyRetryList_shouldRetryAllNonZero() {
            Job job = createJobWithExitCode(127, 0);
            assertThat(calculator.shouldRetry(job)).isTrue();
        }
    }

    @Nested
    class ShouldRetryWithOverrides {

        @Test
        void shouldRetry_withOverride_underMaxRetries_shouldReturnTrue() {
            Job job = createJobWithExitCode(1, 2);
            assertThat(calculator.shouldRetry(job, 5, null)).isTrue();
        }

        @Test
        void shouldRetry_withOverride_atMaxRetries_shouldReturnFalse() {
            Job job = createJobWithExitCode(1, 5);
            assertThat(calculator.shouldRetry(job, 5, null)).isFalse();
        }

        @Test
        void shouldRetry_withOverride_exitCodeInList_shouldReturnTrue() {
            Job job = createJobWithExitCode(42, 0);
            assertThat(calculator.shouldRetry(job, 3, List.of(42, 43))).isTrue();
        }

        @Test
        void shouldRetry_withOverride_exitCodeNotInList_shouldReturnFalse() {
            Job job = createJobWithExitCode(99, 0);
            assertThat(calculator.shouldRetry(job, 3, List.of(42, 43))).isFalse();
        }
    }

    @Nested
    class CalculateDelay {

        @Test
        void calculateDelay_firstRetry_shouldReturnBaseDelay() {
            Duration delay = calculator.calculateDelayWithoutJitter(0);
            // 30 * 2^0 = 30 seconds
            assertThat(delay).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        void calculateDelay_secondRetry_shouldReturnDoubledDelay() {
            Duration delay = calculator.calculateDelayWithoutJitter(1);
            // 30 * 2^1 = 60 seconds
            assertThat(delay).isEqualTo(Duration.ofSeconds(60));
        }

        @Test
        void calculateDelay_thirdRetry_shouldReturnQuadrupledDelay() {
            Duration delay = calculator.calculateDelayWithoutJitter(2);
            // 30 * 2^2 = 120 seconds
            assertThat(delay).isEqualTo(Duration.ofSeconds(120));
        }

        @Test
        void calculateDelay_withNegativeRetryCount_shouldThrow() {
            assertThatThrownBy(() -> calculator.calculateDelay(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryCount");
        }

        @Test
        void calculateDelay_shouldIncludeJitter() {
            // Run multiple times to verify jitter is applied
            Duration delay1 = calculator.calculateDelay(0);
            Duration delay2 = calculator.calculateDelay(0);
            Duration delay3 = calculator.calculateDelay(0);

            // All delays should be within ±20% of base (30s)
            // 30 * 0.8 = 24, 30 * 1.2 = 36
            assertThat(delay1.toSeconds()).isBetween(24L, 36L);
            assertThat(delay2.toSeconds()).isBetween(24L, 36L);
            assertThat(delay3.toSeconds()).isBetween(24L, 36L);
        }

        @Test
        void calculateDelay_shouldCapAtMaxDelay() {
            // Very high retry count to test cap
            Duration delay = calculator.calculateDelayWithoutJitter(100);
            // Should be capped at 3600 seconds (1 hour)
            assertThat(delay).isLessThanOrEqualTo(Duration.ofHours(1));
        }

        @Test
        void calculateDelay_withCustomSettings_shouldUseProvidedValues() {
            Duration delay = calculator.calculateDelayWithoutJitter(2, 10, 3.0);
            // 10 * 3^2 = 90 seconds
            assertThat(delay).isEqualTo(Duration.ofSeconds(90));
        }
    }

    @Nested
    class CalculateRetryAt {

        @Test
        void calculateRetryAt_withNullJob_shouldThrow() {
            assertThatThrownBy(() -> calculator.calculateRetryAt(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("job");
        }

        @Test
        void calculateRetryAt_shouldReturnFutureInstant() {
            Job job = createJobWithExitCode(1, 0);
            Instant before = Instant.now();
            Instant retryAt = calculator.calculateRetryAt(job);
            Instant after = Instant.now();

            // Should be at least base delay in the future (minus jitter)
            assertThat(retryAt).isAfter(before);
            assertThat(retryAt).isBefore(after.plusSeconds(40)); // 30 + some buffer
        }

        @Test
        void calculateRetryAt_withHigherRetryCount_shouldReturnLaterInstant() {
            Job job0 = createJobWithExitCode(1, 0);
            Job job2 = createJobWithExitCode(1, 2);

            Instant retryAt0 = calculator.calculateRetryAt(job0);
            Instant retryAt2 = calculator.calculateRetryAt(job2);

            // Higher retry count should result in later retry time
            // Allow some tolerance for jitter
            assertThat(Duration.between(Instant.now(), retryAt2).toSeconds())
                .isGreaterThan(Duration.between(Instant.now(), retryAt0).toSeconds() / 2);
        }
    }

    @Nested
    class GetMaxRetries {

        @Test
        void getMaxRetries_shouldReturnConfiguredValue() {
            assertThat(calculator.getMaxRetries()).isEqualTo(3);
        }

        @Test
        void getMaxRetries_withCustomConfig_shouldReturnConfiguredValue() {
            JobsConfig customConfig = new JobsConfig(3600, 5, 30, 2.0, List.of());
            RetryCalculator customCalc = new RetryCalculator(customConfig);
            assertThat(customCalc.getMaxRetries()).isEqualTo(5);
        }
    }

    private Job createJobWithExitCode(int exitCode, int retryCount) {
        return new Job(
            "job-test",
            JobState.FAILED,
            List.of("echo", "test"),
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            exitCode,
            "error",
            12345L,
            Instant.now(),
            retryCount,
            null
        );
    }

    private Job createJobWithNullExitCode(int retryCount) {
        return new Job(
            "job-test",
            JobState.RUNNING,
            List.of("echo", "test"),
            null,
            Instant.now(),
            Instant.now(),
            null,
            null,  // null exit code
            null,
            12345L,
            Instant.now(),
            retryCount,
            null
        );
    }
}
