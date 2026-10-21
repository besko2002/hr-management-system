package com.example.hr.access;

import com.example.hr.common.ForbiddenException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.Role;
import org.springframework.stereotype.Component;

/**
 * The single place where "who may see or change what" is decided.
 *
 * <p>Rules:
 * <ul>
 *   <li>A profile is readable by the employee themself, by HR, by ADMIN and by <em>any</em>
 *       ancestor manager. For anybody else the profile answers 404, not 403, so that
 *       employee ids cannot be probed.</li>
 *   <li>Salary is readable by the employee themself, HR and ADMIN only — never by a manager.</li>
 *   <li>Only an ADMIN may grant or revoke the ADMIN role, or modify an existing ADMIN.
 *       Nobody may change their own role.</li>
 * </ul>
 */
@Component
public class EmployeeAccessPolicy {

    private final AncestryChecker ancestry;

    public EmployeeAccessPolicy(AncestryChecker ancestry) {
        this.ancestry = ancestry;
    }

    // ------------------------------------------------------------------ reading

    public boolean canViewProfile(Employee viewer, Employee target) {
        if (isSelf(viewer, target) || viewer.getRole().isHrOrAdmin()) {
            return true;
        }
        return ancestry.isAncestorOf(viewer.getId(), target.getId());
    }

    /** 404 (not 403) for everybody outside the viewer's scope, so ids do not leak. */
    public void requireCanViewProfile(Employee viewer, Employee target) {
        if (!canViewProfile(viewer, target)) {
            throw new ResourceNotFoundException("No employee with id " + target.getId());
        }
    }

    public boolean canSeeSalary(Employee viewer, Employee target) {
        return isSelf(viewer, target) || viewer.getRole().isHrOrAdmin();
    }

    // ------------------------------------------------------------------ writing

    public boolean isHrOrAdmin(Employee actor) {
        return actor.getRole().isHrOrAdmin();
    }

    public void requireHrOrAdmin(Employee actor, String action) {
        if (!isHrOrAdmin(actor)) {
            throw new ForbiddenException("Only HR or an ADMIN may " + action);
        }
    }

    /** HR manages everybody except ADMIN accounts; only an ADMIN may touch an ADMIN. */
    public void requireCanManage(Employee actor, Employee target, String action) {
        requireHrOrAdmin(actor, action);
        if (target.getRole() == Role.ADMIN && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only an ADMIN may " + action + " an ADMIN account");
        }
    }

    /**
     * Role assignment rules. {@code target} is {@code null} when creating a new employee.
     */
    public void requireCanAssignRole(Employee actor, Employee target, Role newRole) {
        requireHrOrAdmin(actor, "assign roles");
        if (newRole == Role.ADMIN && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only an ADMIN may grant the ADMIN role");
        }
        if (target == null) {
            return;
        }
        if (target.getRole() == Role.ADMIN && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only an ADMIN may change the role of an ADMIN account");
        }
        if (isSelf(actor, target) && newRole != target.getRole()) {
            throw new ForbiddenException("You cannot change your own role");
        }
    }

    private static boolean isSelf(Employee viewer, Employee target) {
        return viewer.getId().equals(target.getId());
    }
}
