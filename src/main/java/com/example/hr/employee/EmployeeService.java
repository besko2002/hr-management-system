package com.example.hr.employee;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.common.BadRequestException;
import com.example.hr.common.ConflictException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.department.Department;
import com.example.hr.department.DepartmentRepository;
import com.example.hr.employee.dto.CreateEmployeeRequest;
import com.example.hr.employee.dto.CreatedEmployeeResponse;
import com.example.hr.employee.dto.EmployeeProfile;
import com.example.hr.employee.dto.EmployeeResponse;
import com.example.hr.employee.dto.PageResponse;
import com.example.hr.employee.dto.PatchEmployeeRequest;
import com.example.hr.employee.dto.UpdateEmployeeRequest;
import com.example.hr.security.CurrentEmployee;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Employee lifecycle. Every rule goes through {@link EmployeeAccessPolicy}. */
@Service
public class EmployeeService {

    private static final Set<String> SORTABLE = Set.of(
            "fullName", "email", "employeeNumber", "jobTitle", "hireDate", "salary", "status", "createdAt");

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final List<EmployeeTerminationListener> terminationListeners;
    private final Clock clock;
    private final int maxPageSize;

    EmployeeService(EmployeeRepository employees, DepartmentRepository departments, EmployeeAccessPolicy policy,
                    CurrentEmployee currentEmployee, PasswordEncoder passwordEncoder,
                    TemporaryPasswordGenerator passwordGenerator,
                    List<EmployeeTerminationListener> terminationListeners, Clock clock,
                    @Value("${app.employees.max-page-size:100}") int maxPageSize) {
        this.employees = employees;
        this.departments = departments;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.terminationListeners = terminationListeners;
        this.clock = clock;
        this.maxPageSize = maxPageSize;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public CreatedEmployeeResponse create(CreateEmployeeRequest request) {
        Employee actor = currentEmployee.require();
        policy.requireCanAssignRole(actor, null, request.role());

        String email = Employee.normalizeEmail(request.email());
        if (employees.existsByEmail(email)) {
            throw new ConflictException("An employee with this email already exists");
        }

        Department department = resolveDepartment(request.departmentId());
        Employee manager = resolveManager(request.managerId());

        String temporaryPassword = passwordGenerator.generate();
        Employee employee = new Employee(
                employees.nextEmployeeNumber(), request.fullName().trim(), email,
                passwordEncoder.encode(temporaryPassword), request.role(), trimToNull(request.jobTitle()),
                department, manager, request.hireDate(), request.salary(), true);
        Employee saved = employees.save(employee);
        return new CreatedEmployeeResponse(EmployeeMapper.withSalary(saved), temporaryPassword);
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public EmployeeProfile get(UUID id) {
        Employee actor = currentEmployee.require();
        Employee target = require(id);
        policy.requireCanViewProfile(actor, target);
        return EmployeeMapper.forViewer(target, policy.canSeeSalary(actor, target));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse me() {
        return EmployeeMapper.withSalary(currentEmployee.require());
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(String q, UUID departmentId, EmployeeStatus status,
                                               int page, int size, String sort) {
        Employee actor = currentEmployee.require();
        policy.requireHrOrAdmin(actor, "list employees");

        Page<Employee> found = employees.findAll(
                EmployeeSpecifications.filter(q, departmentId, status),
                PageRequest.of(Math.max(page, 0), clampSize(size), parseSort(sort)));
        return PageResponse.of(found, EmployeeMapper::withSalary);
    }

    // ------------------------------------------------------------------ update

    @Transactional
    public EmployeeResponse update(UUID id, UpdateEmployeeRequest request) {
        Employee actor = currentEmployee.require();
        Employee target = require(id);
        policy.requireCanManage(actor, target, "update");
        policy.requireCanAssignRole(actor, target, request.role());

        applyEmail(target, request.email());
        target.rename(request.fullName().trim());
        target.changeJobTitle(trimToNull(request.jobTitle()));
        target.changeDepartment(resolveDepartment(request.departmentId()));
        target.changeHireDate(request.hireDate());
        target.changeSalary(request.salary());
        target.changeRole(request.role());
        return EmployeeMapper.withSalary(employees.save(target));
    }

    @Transactional
    public EmployeeResponse patch(UUID id, PatchEmployeeRequest request) {
        Employee actor = currentEmployee.require();
        Employee target = require(id);
        policy.requireCanManage(actor, target, "update");
        if (request.role() != null) {
            policy.requireCanAssignRole(actor, target, request.role());
            target.changeRole(request.role());
        }
        if (request.email() != null) {
            applyEmail(target, request.email());
        }
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw new BadRequestException("fullName must not be blank");
            }
            target.rename(request.fullName().trim());
        }
        if (request.jobTitle() != null) {
            target.changeJobTitle(trimToNull(request.jobTitle()));
        }
        if (request.departmentId() != null) {
            target.changeDepartment(resolveDepartment(request.departmentId()));
        }
        if (request.hireDate() != null) {
            target.changeHireDate(request.hireDate());
        }
        if (request.salary() != null) {
            target.changeSalary(request.salary());
        }
        return EmployeeMapper.withSalary(employees.save(target));
    }

    /**
     * Terminates an employee and re-parents their direct reports to the terminated
     * person's own manager, in the same transaction. Registered
     * {@link EmployeeTerminationListener}s run in that same transaction — the leave module
     * uses one to cancel the pending requests and refund the reserved days.
     */
    @Transactional
    public EmployeeResponse terminate(UUID id) {
        Employee actor = currentEmployee.require();
        Employee target = require(id);
        policy.requireCanManage(actor, target, "terminate");
        if (actor.getId().equals(target.getId())) {
            throw new ConflictException("You cannot terminate your own account");
        }
        if (!target.isActive()) {
            throw new ConflictException("This employee is already terminated");
        }

        Employee newManager = target.getManager();
        List<Employee> reports = employees.findByManagerIdOrderByFullNameAsc(target.getId());
        for (Employee report : reports) {
            report.changeManager(newManager);
        }
        employees.saveAll(reports);
        // The effective date comes from the injected Clock, so tests control it and
        // payroll can pro-rate the final month to exactly this day.
        target.terminate(LocalDate.now(clock));
        EmployeeResponse response = EmployeeMapper.withSalary(employees.save(target));
        for (EmployeeTerminationListener listener : terminationListeners) {
            listener.onTerminated(target);
        }
        return response;
    }

    // ------------------------------------------------------------------ helpers

    Employee require(UUID id) {
        return employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No employee with id " + id));
    }

    private void applyEmail(Employee target, String rawEmail) {
        String email = Employee.normalizeEmail(rawEmail);
        if (!email.equals(target.getEmail()) && employees.existsByEmail(email)) {
            throw new ConflictException("An employee with this email already exists");
        }
        target.changeEmail(email);
    }

    private Department resolveDepartment(UUID departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departments.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("No department with id " + departmentId));
    }

    private Employee resolveManager(UUID managerId) {
        if (managerId == null) {
            return null;
        }
        Employee manager = employees.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("No employee with id " + managerId));
        if (!manager.isActive()) {
            throw new ConflictException("A terminated employee cannot be a manager");
        }
        return manager;
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, maxPageSize);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.asc("fullName"));
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!SORTABLE.contains(property)) {
            throw new BadRequestException("Cannot sort by '" + property + "'; allowed: " + SORTABLE);
        }
        boolean descending = parts.length > 1 && parts[1].trim().toLowerCase(Locale.ROOT).startsWith("desc");
        return Sort.by(descending ? Sort.Order.desc(property) : Sort.Order.asc(property));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
