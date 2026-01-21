package com.samsonmedia.barn.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.samsonmedia.barn.state.JobState;

/**
 * Tests for JobStateMachine.
 */
class JobStateMachineTest {

    @Nested
    class ValidateTransition {

        @Test
        void queued_toRunning_shouldBeValid() {
            JobStateMachine.validateTransition(JobState.QUEUED, JobState.RUNNING);
            // No exception means valid
        }

        @Test
        void queued_toCanceled_shouldBeValid() {
            JobStateMachine.validateTransition(JobState.QUEUED, JobState.CANCELED);
        }

        @Test
        void running_toSucceeded_shouldBeValid() {
            JobStateMachine.validateTransition(JobState.RUNNING, JobState.SUCCEEDED);
        }

        @Test
        void running_toFailed_shouldBeValid() {
            JobStateMachine.validateTransition(JobState.RUNNING, JobState.FAILED);
        }

        @Test
        void running_toCanceled_shouldBeValid() {
            JobStateMachine.validateTransition(JobState.RUNNING, JobState.CANCELED);
        }

        @Test
        void failed_toQueued_shouldBeValid() {
            JobStateMachine.validateTransition(JobState.FAILED, JobState.QUEUED);
        }

        @Test
        void queued_toSucceeded_shouldThrowException() {
            assertThatThrownBy(() ->
                JobStateMachine.validateTransition(JobState.QUEUED, JobState.SUCCEEDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid state transition")
                .hasMessageContaining("QUEUED")
                .hasMessageContaining("SUCCEEDED");
        }

        @Test
        void queued_toFailed_shouldThrowException() {
            assertThatThrownBy(() ->
                JobStateMachine.validateTransition(JobState.QUEUED, JobState.FAILED))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void succeeded_toAny_shouldThrowException() {
            assertThatThrownBy(() ->
                JobStateMachine.validateTransition(JobState.SUCCEEDED, JobState.QUEUED))
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() ->
                JobStateMachine.validateTransition(JobState.SUCCEEDED, JobState.RUNNING))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void canceled_toAny_shouldThrowException() {
            assertThatThrownBy(() ->
                JobStateMachine.validateTransition(JobState.CANCELED, JobState.QUEUED))
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() ->
                JobStateMachine.validateTransition(JobState.CANCELED, JobState.RUNNING))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void withNullFrom_shouldThrowException() {
            assertThatThrownBy(() ->
                JobStateMachine.validateTransition(null, JobState.RUNNING))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void withNullTo_shouldThrowException() {
            assertThatThrownBy(() ->
                JobStateMachine.validateTransition(JobState.QUEUED, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class IsValidTransition {

        @Test
        void validTransitions_shouldReturnTrue() {
            assertThat(JobStateMachine.isValidTransition(JobState.QUEUED, JobState.RUNNING)).isTrue();
            assertThat(JobStateMachine.isValidTransition(JobState.QUEUED, JobState.CANCELED)).isTrue();
            assertThat(JobStateMachine.isValidTransition(JobState.RUNNING, JobState.SUCCEEDED)).isTrue();
            assertThat(JobStateMachine.isValidTransition(JobState.RUNNING, JobState.FAILED)).isTrue();
            assertThat(JobStateMachine.isValidTransition(JobState.RUNNING, JobState.CANCELED)).isTrue();
            assertThat(JobStateMachine.isValidTransition(JobState.FAILED, JobState.QUEUED)).isTrue();
        }

        @Test
        void invalidTransitions_shouldReturnFalse() {
            assertThat(JobStateMachine.isValidTransition(JobState.QUEUED, JobState.SUCCEEDED)).isFalse();
            assertThat(JobStateMachine.isValidTransition(JobState.QUEUED, JobState.FAILED)).isFalse();
            assertThat(JobStateMachine.isValidTransition(JobState.SUCCEEDED, JobState.QUEUED)).isFalse();
            assertThat(JobStateMachine.isValidTransition(JobState.CANCELED, JobState.RUNNING)).isFalse();
        }

        @Test
        void withNull_shouldReturnFalse() {
            assertThat(JobStateMachine.isValidTransition(null, JobState.RUNNING)).isFalse();
            assertThat(JobStateMachine.isValidTransition(JobState.QUEUED, null)).isFalse();
        }
    }

    @Nested
    class GetValidTransitions {

        @Test
        void forQueued_shouldReturnRunningAndCanceled() {
            assertThat(JobStateMachine.getValidTransitions(JobState.QUEUED))
                .containsExactlyInAnyOrder(JobState.RUNNING, JobState.CANCELED);
        }

        @Test
        void forRunning_shouldReturnSucceededFailedCanceled() {
            assertThat(JobStateMachine.getValidTransitions(JobState.RUNNING))
                .containsExactlyInAnyOrder(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELED);
        }

        @Test
        void forFailed_shouldReturnQueued() {
            assertThat(JobStateMachine.getValidTransitions(JobState.FAILED))
                .containsExactly(JobState.QUEUED);
        }

        @Test
        void forSucceeded_shouldReturnEmpty() {
            assertThat(JobStateMachine.getValidTransitions(JobState.SUCCEEDED)).isEmpty();
        }

        @Test
        void forCanceled_shouldReturnEmpty() {
            assertThat(JobStateMachine.getValidTransitions(JobState.CANCELED)).isEmpty();
        }

        @Test
        void withNull_shouldThrowException() {
            assertThatThrownBy(() -> JobStateMachine.getValidTransitions(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class TransitionRecord {

        @Test
        void constructor_shouldCreateTransition() {
            Instant now = Instant.now();
            JobStateMachine.Transition transition = new JobStateMachine.Transition(
                JobState.QUEUED, JobState.RUNNING, now, "Starting execution");

            assertThat(transition.from()).isEqualTo(JobState.QUEUED);
            assertThat(transition.to()).isEqualTo(JobState.RUNNING);
            assertThat(transition.timestamp()).isEqualTo(now);
            assertThat(transition.reason()).isEqualTo("Starting execution");
        }

        @Test
        void constructor_withNullFrom_shouldThrowException() {
            assertThatThrownBy(() ->
                new JobStateMachine.Transition(null, JobState.RUNNING, Instant.now(), null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void now_shouldCreateWithCurrentTimestamp() {
            JobStateMachine.Transition transition = JobStateMachine.Transition.now(
                JobState.QUEUED, JobState.RUNNING, "Test");

            assertThat(transition.from()).isEqualTo(JobState.QUEUED);
            assertThat(transition.to()).isEqualTo(JobState.RUNNING);
            assertThat(transition.timestamp()).isNotNull();
            assertThat(transition.reason()).isEqualTo("Test");
        }
    }
}
