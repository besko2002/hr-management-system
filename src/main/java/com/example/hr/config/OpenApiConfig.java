package com.example.hr.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("HR Management System API")
                        .version("v1")
                        .description("""
                                Phases 1–4: authentication, employees/departments/org hierarchy, leave, \
                                attendance, payroll, Envers audit trail and management reports.

                                Log in at `POST /api/auth/login` and send the token as \
                                `Authorization: Bearer <token>`. Errors are always `ApiError` JSON \
                                (400 with `fieldErrors`, 401/403, 404 for unknown or out-of-scope \
                                records, 409 for conflicts)."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .name(BEARER)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT access token from POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .tags(List.of(
                        tag("Authentication", "Login and password change"),
                        tag("Employees", "Employee CRUD, terminate, self profile"),
                        tag("Departments", "Department CRUD"),
                        tag("Org chart", "Hierarchy, team, management chain"),
                        tag("Leave", "Types, balances, requests, holidays, accrual"),
                        tag("Attendance", "Check-in/out, corrections, team today, Excel export"),
                        tag("Payroll", "Monthly runs, payslips, PDF and Excel"),
                        tag("Audit", "Envers field-level history (HR/ADMIN)"),
                        tag("Reports", "Headcount, leave, payroll and attendance summaries")
                ));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
