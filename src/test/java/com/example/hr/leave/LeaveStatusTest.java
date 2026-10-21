package com.example.hr.leave;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The state machine, exhaustively: every ordered pair of states is asserted against the
 * specification, so an accidental edge in the transition table cannot slip through.
 *
 * <pre>
 *   PENDING   -> APPROVED, REJECTED, CANCELLED
 *   APPROVED  -> CANCELLED
 *   REJECTED  -> (nothing)
 *   CANCELLED -> (nothing)
 * </pre>
 */
class LeaveStatusTest {

    private static final Set<String> SPECIFIED_EDGES = Set.of(
            "PENDING>APPROVED",
            "PENDING>REJECTED",
            "PENDING>CANCELLED",
            "APPROVED>CANCELLED");

    static Stream<Arguments> everyOrderedPair() {
        List<Arguments> pairs = new ArrayList<>();
        for (LeaveStatus from : LeaveStatus.values()) {
            for (LeaveStatus to : LeaveStatus.values()) {
                pairs.add(Arguments.of(from, to, SPECIFIED_EDGES.contains(from + ">" + to)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("everyOrderedPair")
    void everyTransitionPairMatchesTheSpecification(LeaveStatus from, LeaveStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void aPendingRequestMayBeApprovedRejectedOrCancelled() {
        assertThat(LeaveStatus.PENDING.allowedTransitions())
                .containsExactlyInAnyOrder(LeaveStatus.APPROVED, LeaveStatus.REJECTED, LeaveStatus.CANCELLED);
    }

    @Test
    void anApprovedRequestMayOnlyBeCancelled() {
        assertThat(LeaveStatus.APPROVED.allowedTransitions()).containsExactly(LeaveStatus.CANCELLED);
    }

    @Test
    void rejectedAndCancelledAreTerminal() {
        assertThat(LeaveStatus.REJECTED.isTerminal()).isTrue();
        assertThat(LeaveStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(LeaveStatus.REJECTED.allowedTransitions()).isEmpty();
        assertThat(LeaveStatus.CANCELLED.allowedTransitions()).isEmpty();
    }

    @Test
    void pendingAndApprovedAreNotTerminal() {
        assertThat(LeaveStatus.PENDING.isTerminal()).isFalse();
        assertThat(LeaveStatus.APPROVED.isTerminal()).isFalse();
    }

    @Test
    void noStateMayTransitionToItself() {
        for (LeaveStatus status : LeaveStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }

    @Test
    void aNullTargetIsNeverAllowed() {
        for (LeaveStatus status : LeaveStatus.values()) {
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    void onlyPendingAndApprovedOccupyTheCalendar() {
        assertThat(LeaveStatus.PENDING.blocksOverlap()).isTrue();
        assertThat(LeaveStatus.APPROVED.blocksOverlap()).isTrue();
        assertThat(LeaveStatus.REJECTED.blocksOverlap()).isFalse();
        assertThat(LeaveStatus.CANCELLED.blocksOverlap()).isFalse();
    }

    @Test
    void theTransitionTableCannotBeTamperedWith() {
        Set<LeaveStatus> transitions = LeaveStatus.APPROVED.allowedTransitions();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transitions.add(LeaveStatus.PENDING))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(LeaveStatus.APPROVED.allowedTransitions()).containsExactly(LeaveStatus.CANCELLED);
    }
}
