package com.example.hr.employee;

/**
 * Account role. "Manager" is deliberately NOT a role: being a manager is derived from
 * having direct reports, so access is a combination of role and position in the tree.
 */
public enum Role {
    ADMIN,
    HR,
    EMPLOYEE;

    public boolean isHrOrAdmin() {
        return this == HR || this == ADMIN;
    }
}
