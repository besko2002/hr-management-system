package com.example.hr.employee.dto;

import com.example.hr.employee.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full replacement of the mutable profile (PUT): every field is applied as given, so a
 * {@code null} departmentId or salary clears it. The manager is changed through
 * PUT /api/employees/{id}/manager instead.
 */
public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Role role,
        @Size(max = 120) String jobTitle,
        UUID departmentId,
        @NotNull LocalDate hireDate,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salary) {
}
