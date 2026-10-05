package com.example.hr.auth;

import com.example.hr.auth.AuthDtos.AuthenticatedUser;
import com.example.hr.auth.AuthDtos.ChangePasswordRequest;
import com.example.hr.auth.AuthDtos.LoginRequest;
import com.example.hr.auth.AuthDtos.LoginResponse;
import com.example.hr.common.BadRequestException;
import com.example.hr.common.UnauthorizedException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.security.CurrentEmployee;
import com.example.hr.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Login and password change. There is no public registration: accounts exist because
 * HR/ADMIN created them.
 */
@Service
public class AuthService {

    /** One message for every failure mode, so e-mail existence and status do not leak. */
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final CurrentEmployee currentEmployee;

    AuthService(EmployeeRepository employees, PasswordEncoder passwordEncoder, JwtService jwt,
                CurrentEmployee currentEmployee) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.currentEmployee = currentEmployee;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<Employee> found = employees.findByEmail(Employee.normalizeEmail(request.email()));
        if (found.isEmpty()) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        Employee employee = found.get();
        if (!passwordEncoder.matches(request.password(), employee.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        // A terminated employee gets exactly the same answer as a wrong password.
        if (!employee.isActive()) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return new LoginResponse(jwt.issue(employee), "Bearer", jwt.expiresInSeconds(),
                AuthenticatedUser.of(employee));
    }

    /** Any authenticated user may change their own password; this clears mustChangePassword. */
    @Transactional
    public AuthenticatedUser changePassword(ChangePasswordRequest request) {
        Employee employee = currentEmployee.require();
        if (!passwordEncoder.matches(request.currentPassword(), employee.getPasswordHash())) {
            throw new UnauthorizedException("The current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), employee.getPasswordHash())) {
            throw new BadRequestException("The new password must differ from the current one");
        }
        employee.setPassword(passwordEncoder.encode(request.newPassword()));
        return AuthenticatedUser.of(employees.save(employee));
    }
}
