package com.example.hr.auth;

import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.employee.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);

    @Test
    void nothingIsSeededWithoutAPassword() {
        when(employees.existsByRole(Role.ADMIN)).thenReturn(false);
        new AdminBootstrap(employees, NoOpPasswordEncoder.getInstance(), "admin@hr.local", "").run(null);
        verify(employees, never()).save(any());
    }

    @Test
    void theSeededAdminMustChangeThePassword() {
        when(employees.existsByRole(Role.ADMIN)).thenReturn(false);
        when(employees.nextEmployeeNumber()).thenReturn("EMP-0001");
        new AdminBootstrap(employees, NoOpPasswordEncoder.getInstance(), "admin@hr.local", "Secret@123").run(null);
        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    }
}
