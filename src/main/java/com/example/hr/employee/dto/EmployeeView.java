package com.example.hr.employee.dto;

import com.example.hr.employee.EmployeeStatus;
import com.example.hr.employee.Role;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Profile without any salary field, used when a manager looks at one of their reports.
 * This is a distinct type rather than the same record with {@code salary = null}.
 */
public record EmployeeView(
        UUID id,
        String employeeNumber,
        String fullName,
        String email,
        Role role,
        String jobTitle,
        UUID departmentId,
        String departmentName,
        UUID managerId,
        String managerName,
        LocalDate hireDate,
        EmployeeStatus status,
        Instant createdAt,
        Instant updatedAt) implements EmployeeProfile {
}
