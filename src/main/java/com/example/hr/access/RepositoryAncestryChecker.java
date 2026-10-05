package com.example.hr.access;

import com.example.hr.employee.EmployeeRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Database-backed {@link AncestryChecker}: one recursive CTE, no Java recursion. */
@Component
class RepositoryAncestryChecker implements AncestryChecker {

    private final EmployeeRepository employees;

    RepositoryAncestryChecker(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    public boolean isAncestorOf(UUID ancestorId, UUID employeeId) {
        return employees.isAncestorOf(ancestorId, employeeId);
    }
}
