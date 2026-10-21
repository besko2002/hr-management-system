package com.example.hr.employee.dto;

/**
 * Returned once, by POST /api/employees only: the generated temporary password is never
 * retrievable again and the employee must change it on first login.
 */
public record CreatedEmployeeResponse(EmployeeResponse employee, String temporaryPassword) {
}
