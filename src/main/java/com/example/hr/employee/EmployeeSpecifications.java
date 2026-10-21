package com.example.hr.employee;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Filters for the HR employee list: free-text search, department and status. */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> filter(String q, UUID departmentId, EmployeeStatus status) {
        return (root, query, cb) -> {
            // Fetch the two to-one associations eagerly for the data query (not the count query)
            // so mapping a page of employees stays a single round trip.
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                root.fetch("department", JoinType.LEFT);
                root.fetch("manager", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("employeeNumber")), like)));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
