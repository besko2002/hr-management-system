package com.example.hr.access;

import java.util.UUID;

/**
 * Answers "is A somewhere above B in the org tree?". Implemented by a recursive CTE;
 * kept behind an interface so the access policy can be unit-tested without a database.
 */
public interface AncestryChecker {

    boolean isAncestorOf(UUID ancestorId, UUID employeeId);
}
