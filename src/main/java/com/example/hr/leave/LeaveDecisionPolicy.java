package com.example.hr.leave;

import com.example.hr.access.AncestryChecker;
import com.example.hr.common.ForbiddenException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who may see and who may decide a leave request. Reuses the Phase 1 recursive-CTE
 * ancestry check, so "is this person above that person in the tree?" has exactly one
 * implementation in the system.
 *
 * <p>Rules:
 * <ul>
 *   <li><b>Visibility</b>: the requester, any of their ancestor managers, HR and ADMIN.
 *       Everybody else gets 404 — never 403 — so request ids cannot be probed.</li>
 *   <li><b>Deciding</b>: the requester's <em>direct</em> manager. A higher ancestor who is
 *       not the direct manager may not decide (403). HR/ADMIN may always decide, as an
 *       override, and are also the only ones who can decide for an employee who has no
 *       manager at all.</li>
 *   <li><b>Never your own</b>: deciding your own request is 403 for everybody, HR and
 *       ADMIN included. The employee cancels instead.</li>
 *   <li><b>Cancelling</b>: the requester, or HR/ADMIN on their behalf. A manager rejects,
 *       they do not cancel (403).</li>
 * </ul>
 */
@Component
public class LeaveDecisionPolicy {

    private final AncestryChecker ancestry;

    LeaveDecisionPolicy(AncestryChecker ancestry) {
        this.ancestry = ancestry;
    }

    public boolean canView(Employee viewer, Employee requester) {
        return isSelf(viewer, requester)
                || viewer.getRole().isHrOrAdmin()
                || ancestry.isAncestorOf(viewer.getId(), requester.getId());
    }

    public void requireCanView(Employee viewer, Employee requester, UUID requestId) {
        if (!canView(viewer, requester)) {
            throw notFound(requestId);
        }
    }

    public void requireCanDecide(Employee actor, Employee requester, UUID requestId) {
        if (isSelf(actor, requester)) {
            throw new ForbiddenException("You cannot decide your own leave request; cancel it instead");
        }
        if (actor.getRole().isHrOrAdmin()) {
            return;
        }
        if (actor.getId().equals(requester.managerId())) {
            return;
        }
        if (ancestry.isAncestorOf(actor.getId(), requester.getId())) {
            throw new ForbiddenException("Only " + requester.getFullName()
                    + "'s direct manager or HR may decide this request");
        }
        throw notFound(requestId);
    }

    public void requireCanCancel(Employee actor, Employee requester, UUID requestId) {
        if (isSelf(actor, requester) || actor.getRole().isHrOrAdmin()) {
            return;
        }
        if (ancestry.isAncestorOf(actor.getId(), requester.getId())) {
            throw new ForbiddenException("Only the requester or HR may cancel a leave request; "
                    + "a manager rejects it instead");
        }
        throw notFound(requestId);
    }

    private static ResourceNotFoundException notFound(UUID requestId) {
        return new ResourceNotFoundException("No leave request with id " + requestId);
    }

    private static boolean isSelf(Employee a, Employee b) {
        return a.getId().equals(b.getId());
    }
}
