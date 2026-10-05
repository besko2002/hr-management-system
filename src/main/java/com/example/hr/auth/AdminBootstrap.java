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
 * <p>The defaults (admin@hr.local / Admin@12345) are DEV-ONLY — set HR_ADMIN_EMAIL and
 * HR_ADMIN_PASSWORD in every real environment.
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
        String normalized = Employee.normalizeEmail(email);
        if (employees.existsByEmail(normalized)) {
            log.warn("Bootstrap admin not created: {} already exists with a non-ADMIN role", normalized);
            return;
        }
        Employee admin = new Employee(employees.nextEmployeeNumber(), "System Administrator", normalized,
                passwordEncoder.encode(password), Role.ADMIN, "System Administrator", null, null,
                LocalDate.now(), null, false);
        employees.save(admin);
        log.info("Seeded bootstrap ADMIN {} — change this password immediately outside development", normalized);
    }
}
