package com.example.hr.leave;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The leave-request state machine.
 *
 * <pre>
 *   PENDING  -> APPROVED | REJECTED | CANCELLED
 *   APPROVED -> CANCELLED            (only while the leave has not started yet)
 *   REJECTED -> (terminal)
 *   CANCELLED-> (terminal)
 * </pre>
 *
 * <p>The transition table is the only place where legality is decided; every attempt that
 * is not listed here is refused with 409 by the service layer. The extra temporal rule
 * for cancelling an already approved leave is a business guard on top of the machine, not
 * part of it.
 */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    private static final Map<LeaveStatus, Set<LeaveStatus>> ALLOWED;

    static {
        Map<LeaveStatus, Set<LeaveStatus>> allowed = new EnumMap<>(LeaveStatus.class);
        allowed.put(PENDING, Collections.unmodifiableSet(EnumSet.of(APPROVED, REJECTED, CANCELLED)));
        allowed.put(APPROVED, Collections.unmodifiableSet(EnumSet.of(CANCELLED)));
        allowed.put(REJECTED, Collections.unmodifiableSet(EnumSet.noneOf(LeaveStatus.class)));
        allowed.put(CANCELLED, Collections.unmodifiableSet(EnumSet.noneOf(LeaveStatus.class)));
        ALLOWED = Collections.unmodifiableMap(allowed);
    }

    /** The states this state may move to. Never null, possibly empty. */
    public Set<LeaveStatus> allowedTransitions() {
        return ALLOWED.get(this);
    }

    public boolean canTransitionTo(LeaveStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    /** No transition leaves this state. */
    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }

    /** States that occupy the calendar and therefore block an overlapping request. */
    public boolean blocksOverlap() {
        return this == PENDING || this == APPROVED;
    }
}
