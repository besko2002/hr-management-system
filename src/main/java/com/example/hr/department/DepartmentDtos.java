package com.example.hr.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request/response shapes for the department API. */
public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    public record DepartmentRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record DepartmentResponse(UUID id, String name, Instant createdAt) {

        static DepartmentResponse of(Department department) {
            return new DepartmentResponse(department.getId(), department.getName(), department.getCreatedAt());
        }
    }
}
