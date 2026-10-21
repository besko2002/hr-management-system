package com.example.hr.security;

import com.example.hr.common.UnauthorizedException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves the authenticated employee (JWT subject = employee id) for service-level checks. */
@Component
public class CurrentEmployee {

    private final EmployeeRepository employees;

    CurrentEmployee(EmployeeRepository employees) {
        this.employees = employees;
    }

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication is required to access this resource");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("The token does not identify a known employee");
        }
    }

    public Employee require() {
        return employees.findById(id())
                .orElseThrow(() -> new UnauthorizedException("The token does not identify a known employee"));
    }
}
