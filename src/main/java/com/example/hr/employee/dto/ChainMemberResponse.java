package com.example.hr.employee.dto;

import com.example.hr.employee.EmployeeStatus;

import java.util.UUID;

/**
 * One step of the management chain upwards. {@code level} 1 is the direct manager,
 * 2 their manager, and so on up to the root. No salary field.
 */
public record ChainMemberResponse(
        UUID id,
        String employeeNumber,
        String fullName,
        String email,
        String jobTitle,
        String departmentName,
        EmployeeStatus status,
        int level) {
}
