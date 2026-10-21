package com.example.hr.auth;

import com.example.hr.employee.Employee;
import com.example.hr.employee.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request/response shapes for authentication. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /** The authenticated identity. Deliberately carries no salary. */
    public record AuthenticatedUser(
            UUID id,
            String employeeNumber,
            String fullName,
            String email,
            Role role,
            String jobTitle,
            String departmentName,
            boolean mustChangePassword) {

        static AuthenticatedUser of(Employee employee) {
            return new AuthenticatedUser(employee.getId(), employee.getEmployeeNumber(), employee.getFullName(),
                    employee.getEmail(), employee.getRole(), employee.getJobTitle(),
                    employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                    employee.isMustChangePassword());
        }
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            AuthenticatedUser user) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record MessageResponse(String message) {
    }
}
