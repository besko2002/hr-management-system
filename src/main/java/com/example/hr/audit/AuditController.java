package com.example.hr.audit;

import com.example.hr.employee.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit")
@SecurityRequirement(name = "bearerAuth")
class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/employees/{id}")
    @Operation(summary = "Employee field-level audit history (HR/ADMIN only)",
            description = "Per-revision changed-field diffs for salary, jobTitle, managerId, status, "
                    + "role and departmentId. Newest first. Non-HR callers receive 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged field diffs"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
            @ApiResponse(responseCode = "404", description = "Unknown employee or caller is not HR/ADMIN",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    PageResponse<AuditChangeResponse> employeeHistory(@PathVariable UUID id,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return audit.employeeHistory(id, page, size);
    }
}
