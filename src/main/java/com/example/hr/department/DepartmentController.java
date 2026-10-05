package com.example.hr.department;

import com.example.hr.department.DepartmentDtos.DepartmentRequest;
import com.example.hr.department.DepartmentDtos.DepartmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/departments")
@Tag(name = "Departments")
class DepartmentController {

    private final DepartmentService departments;

    DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @GetMapping
    @Operation(summary = "List departments (any authenticated user)")
    List<DepartmentResponse> list() {
        return departments.list();
    }

    @PostMapping
    @Operation(summary = "Create a department (HR/ADMIN)")
    ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departments.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a department (HR/ADMIN)")
    DepartmentResponse rename(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
        return departments.rename(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a department (HR/ADMIN); 409 while it still has employees")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        departments.delete(id);
        return ResponseEntity.noContent().build();
    }
}
