package com.example.hr.employee;

import com.example.hr.employee.dto.ChainMemberResponse;
import com.example.hr.employee.dto.CreateEmployeeRequest;
import com.example.hr.employee.dto.CreatedEmployeeResponse;
import com.example.hr.employee.dto.EmployeeProfile;
import com.example.hr.employee.dto.EmployeeResponse;
import com.example.hr.employee.dto.PageResponse;
import com.example.hr.employee.dto.PatchEmployeeRequest;
import com.example.hr.employee.dto.SetManagerRequest;
import com.example.hr.employee.dto.TeamMemberResponse;
import com.example.hr.employee.dto.UpdateEmployeeRequest;
import com.example.hr.hierarchy.HierarchyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees")
class EmployeeController {

    private final EmployeeService employees;
    private final HierarchyService hierarchy;

    EmployeeController(EmployeeService employees, HierarchyService hierarchy) {
        this.employees = employees;
        this.hierarchy = hierarchy;
    }

    @PostMapping
    @Operation(summary = "Create an employee (HR/ADMIN). Returns the temporary password once.")
    ResponseEntity<CreatedEmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employees.create(request));
    }

    @GetMapping
    @Operation(summary = "List employees (HR/ADMIN only), paginated, filterable and sortable")
    PageResponse<EmployeeResponse> list(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) UUID departmentId,
                                        @RequestParam(required = false) EmployeeStatus status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String sort) {
        return employees.list(q, departmentId, status, page, size, sort);
    }

    @GetMapping("/me")
    @Operation(summary = "The caller's own profile, salary included")
    EmployeeResponse me() {
        return employees.me();
    }

    @GetMapping("/me/team")
    @Operation(summary = "The caller's direct reports (no salary)")
    List<TeamMemberResponse> myTeam() {
        return hierarchy.myTeam();
    }

    @GetMapping("/me/team/all")
    @Operation(summary = "All of the caller's descendants with their depth (no salary)")
    List<TeamMemberResponse> myWholeTeam() {
        return hierarchy.myWholeTeam();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One profile: self, HR, ADMIN or any ancestor manager; 404 for anybody else")
    EmployeeProfile get(@PathVariable UUID id) {
        return employees.get(id);
    }

    @GetMapping("/{id}/chain")
    @Operation(summary = "The management chain upwards to the root")
    List<ChainMemberResponse> chain(@PathVariable UUID id) {
        return hierarchy.managementChain(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the mutable profile (HR/ADMIN)")
    EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employees.update(id, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update the profile (HR/ADMIN)")
    EmployeeResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchEmployeeRequest request) {
        return employees.patch(id, request);
    }

    @PostMapping("/{id}/terminate")
    @Operation(summary = "Terminate an employee; their direct reports move to their manager")
    EmployeeResponse terminate(@PathVariable UUID id) {
        return employees.terminate(id);
    }

    @PutMapping("/{id}/manager")
    @Operation(summary = "Set or clear the manager (HR/ADMIN); refuses cycles with 409")
    EmployeeProfile setManager(@PathVariable UUID id, @RequestBody SetManagerRequest request) {
        return hierarchy.setManager(id, request.managerId());
    }
}
