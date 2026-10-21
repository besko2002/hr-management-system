package com.example.hr.leave;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Filters for "my leave requests": optional status and optional year. */
public final class LeaveRequestSpecifications {

    private LeaveRequestSpecifications() {
    }

    public static Specification<LeaveRequest> of(UUID employeeId, LeaveStatus status, Integer year) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("employee").get("id"), employeeId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (year != null) {
                predicates.add(cb.equal(root.get("leaveYear"), year));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
