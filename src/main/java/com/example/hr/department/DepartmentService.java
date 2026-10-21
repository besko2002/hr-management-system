package com.example.hr.department;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.common.ConflictException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.department.DepartmentDtos.DepartmentRequest;
import com.example.hr.department.DepartmentDtos.DepartmentResponse;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.security.CurrentEmployee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Departments: HR/ADMIN maintain them, every authenticated user may list them. */
@Service
public class DepartmentService {

    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;

    DepartmentService(DepartmentRepository departments, EmployeeRepository employees,
                      EmployeeAccessPolicy policy, CurrentEmployee currentEmployee) {
        this.departments = departments;
        this.employees = employees;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        currentEmployee.require();
        return departments.findAllByOrderByNameAsc().stream().map(DepartmentResponse::of).toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        policy.requireHrOrAdmin(currentEmployee.require(), "create departments");
        String name = request.name().trim();
        if (departments.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A department named '" + name + "' already exists");
        }
        return DepartmentResponse.of(departments.save(new Department(name)));
    }

    @Transactional
    public DepartmentResponse rename(UUID id, DepartmentRequest request) {
        policy.requireHrOrAdmin(currentEmployee.require(), "rename departments");
        Department department = require(id);
        String name = request.name().trim();
        departments.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ConflictException("A department named '" + name + "' already exists");
            }
        });
        department.rename(name);
        return DepartmentResponse.of(departments.save(department));
    }

    @Transactional
    public void delete(UUID id) {
        policy.requireHrOrAdmin(currentEmployee.require(), "delete departments");
        Department department = require(id);
        long headcount = employees.countByDepartmentId(id);
        if (headcount > 0) {
            throw new ConflictException(
                    "This department still has " + headcount + " employee(s); move them first");
        }
        departments.delete(department);
    }

    private Department require(UUID id) {
        return departments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No department with id " + id));
    }
}
