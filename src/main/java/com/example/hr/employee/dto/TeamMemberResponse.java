package com.example.hr.employee.dto;

import com.example.hr.employee.EmployeeStatus;

import java.util.UUID;

/**
 * A team member as seen by their manager: no salary field, plus {@code depth}
 * (1 = direct report, 2 = report of a report, ...).
 */
public record TeamMemberResponse(
        UUID id,
        String employeeNumber,
        String fullName,
        String email,
        String jobTitle,
        String departmentName,
        UUID managerId,
        EmployeeStatus status,
        int depth) {
}
