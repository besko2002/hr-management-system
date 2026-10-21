package com.example.hr.auth;

import com.example.hr.auth.AuthDtos.AuthenticatedUser;
import com.example.hr.auth.AuthDtos.ChangePasswordRequest;
import com.example.hr.auth.AuthDtos.LoginRequest;
import com.example.hr.auth.AuthDtos.LoginResponse;
import com.example.hr.common.BadRequestException;
import com.example.hr.common.TooManyRequestsException;
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
    private final LoginRateLimiter rateLimiter;
    /** Checked against when the e-mail is unknown, so that path costs one BCrypt like the others. */
    private final String dummyHash;

    AuthService(EmployeeRepository employees, PasswordEncoder passwordEncoder, JwtService jwt,
                CurrentEmployee currentEmployee, LoginRateLimiter rateLimiter) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.currentEmployee = currentEmployee;
        this.rateLimiter = rateLimiter;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-equalization");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientIp) {
        String email = Employee.normalizeEmail(request.email());
        if (rateLimiter.isBlocked(email, clientIp)) {
            throw new TooManyRequestsException("Too many failed login attempts. Try again later.");
        }
        Optional<Employee> found = employees.findByEmail(email);
        boolean matches = passwordEncoder.matches(request.password(),
                found.map(Employee::getPasswordHash).orElse(dummyHash));
        // Unknown e-mail, wrong password and a terminated employee all get the same answer.
        if (found.isEmpty() || !matches || !found.get().isActive()) {
            rateLimiter.recordFailure(email, clientIp);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        rateLimiter.reset(email, clientIp);
        Employee employee = found.get();
        return new LoginResponse(jwt.issue(employee), "Bearer", jwt.expiresInSeconds(),
                AuthenticatedUser.of(employee));
    }

    /** The updated user plus a token issued after the change (older tokens are now rejected). */
    public record PasswordChanged(AuthenticatedUser user, String accessToken) {
    }

    /** Any authenticated user may change their own password; this clears mustChangePassword. */
    @Transactional
    public PasswordChanged changePassword(ChangePasswordRequest request) {
        Employee employee = currentEmployee.require();
        if (!passwordEncoder.matches(request.currentPassword(), employee.getPasswordHash())) {
            throw new UnauthorizedException("The current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), employee.getPasswordHash())) {
            throw new BadRequestException("The new password must differ from the current one");
        }
        employee.setPassword(passwordEncoder.encode(request.newPassword()));
        Employee saved = employees.save(employee);
        return new PasswordChanged(AuthenticatedUser.of(saved), jwt.issue(saved));
    }
}
