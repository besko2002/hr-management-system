package com.example.hr.auth;

import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.employee.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Seeds one ADMIN at startup when the database contains none, so a fresh installation is
 * usable without a public registration endpoint.
 *
 * <p>HR_ADMIN_PASSWORD has no default: while it is blank nothing is seeded. The seeded
 * admin is flagged {@code mustChangePassword}, so the configured password is single-use.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    AdminBootstrap(EmployeeRepository employees, PasswordEncoder passwordEncoder,
                   @Value("${app.bootstrap-admin.email}") String email,
                   @Value("${app.bootstrap-admin.password}") String password) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employees.existsByRole(Role.ADMIN)) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("No ADMIN exists and HR_ADMIN_PASSWORD is not set — bootstrap admin not created");
            return;
        }
        String normalized = Employee.normalizeEmail(email);
        if (employees.existsByEmail(normalized)) {
            log.warn("Bootstrap admin not created: {} already exists with a non-ADMIN role", normalized);
            return;
        }
        Employee admin = new Employee(employees.nextEmployeeNumber(), "System Administrator", normalized,
                passwordEncoder.encode(password), Role.ADMIN, "System Administrator", null, null,
                LocalDate.now(), null, true);
        employees.save(admin);
        log.info("Seeded bootstrap ADMIN {} — password change required on first login", normalized);
    }
}
