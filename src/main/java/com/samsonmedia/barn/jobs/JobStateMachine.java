package com.samsonmedia.barn.jobs;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.samsonmedia.barn.state.JobState;

/**
 * Enforces valid state transitions for jobs.
 *
 * <p>Valid transitions:
 * <ul>
 *   <li>QUEUED → RUNNING, FAILED, CANCELED</li>
 *   <li>RUNNING → SUCCEEDED, FAILED, CANCELED</li>
 *   <li>FAILED → QUEUED (for retry)</li>
 *   <li>SUCCEEDED → (none, terminal)</li>
 *   <li>CANCELED → (none, terminal)</li>
 * </ul>
 */
public final class JobStateMachine {

    private static final Map<JobState, Set<JobState>> VALID_TRANSITIONS = Map.of(
        JobState.QUEUED, Set.of(JobState.RUNNING, JobState.FAILED, JobState.CANCELED),
        JobState.RUNNING, Set.of(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELED),
        JobState.FAILED, Set.of(JobState.QUEUED),  // For retry
        JobState.SUCCEEDED, Set.of(),
        JobState.CANCELED, Set.of()
    );

    private JobStateMachine() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that a state transition is allowed.
     *
     * @param from the current state
     * @param to the target state
     * @throws IllegalStateException if the transition is not valid
     */
    public static void validateTransition(JobState from, JobState to) {
        Objects.requireNonNull(from, "from state must not be null");
        Objects.requireNonNull(to, "to state must not be null");

        Set<JobState> validTargets = VALID_TRANSITIONS.get(from);
        if (!validTargets.contains(to)) {
            throw new IllegalStateException(
                "Invalid state transition: " + from + " -> " + to);
        }
    }

    /**
     * Checks if a state transition is valid.
     *
     * @param from the current state
     * @param to the target state
     * @return true if the transition is valid
     */
    public static boolean isValidTransition(JobState from, JobState to) {
        if (from == null || to == null) {
            return false;
        }
        return VALID_TRANSITIONS.get(from).contains(to);
    }

    /**
     * Gets the valid target states for a given state.
     *
     * @param state the current state
     * @return the set of valid target states
     */
    public static Set<JobState> getValidTransitions(JobState state) {
        Objects.requireNonNull(state, "state must not be null");
        return VALID_TRANSITIONS.get(state);
    }

    /**
     * Record representing a state transition event.
     *
     * @param from the previous state
     * @param to the new state
     * @param timestamp when the transition occurred
     * @param reason optional reason for the transition
     */
    public record Transition(
        JobState from,
        JobState to,
        Instant timestamp,
        String reason
    ) {
        /**
         * Creates a Transition with validation.
         */
        public Transition {
            Objects.requireNonNull(from, "from must not be null");
            Objects.requireNonNull(to, "to must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }

        /**
         * Creates a Transition with the current timestamp.
         *
         * @param from the previous state
         * @param to the new state
         * @param reason optional reason
         * @return a new Transition
         */
        public static Transition now(JobState from, JobState to, String reason) {
            return new Transition(from, to, Instant.now(), reason);
        }
    }
}
