package com.example.hr.employee.dto;

import com.example.hr.employee.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Partial update (PATCH): only non-null fields are applied. */
public record PatchEmployeeRequest(
        @Size(max = 150) String fullName,
        @Email @Size(max = 255) String email,
        Role role,
        @Size(max = 120) String jobTitle,
        UUID departmentId,
        LocalDate hireDate,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salary) {
}
