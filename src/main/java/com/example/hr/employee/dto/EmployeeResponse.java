package com.example.hr.employee.dto;

import com.example.hr.employee.EmployeeStatus;
import com.example.hr.employee.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Full profile including salary — only for the employee themself, HR and ADMIN. */
public record EmployeeResponse(
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
        BigDecimal salary,
        EmployeeStatus status,
        LocalDate terminatedAt,
        boolean mustChangePassword,
        Instant createdAt,
        Instant updatedAt) implements EmployeeProfile {
}
