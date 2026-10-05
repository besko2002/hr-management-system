package com.example.hr.security;

import com.example.hr.common.ApiError;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.employee.EmployeeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Rejects tokens belonging to employees who no longer exist or who have been terminated.
 *
 * <p>Design decision: instead of a token-version / denylist scheme, every authenticated
 * request performs one indexed primary-key lookup ({@code select status from employees
 * where id = ?}). Revocation is therefore immediate — a termination takes effect on the
 * terminated person's very next request, with no need to re-issue or track tokens — at
 * the cost of a single cheap query per request, which the connection pool absorbs easily.
 */
public class ActiveEmployeeFilter extends OncePerRequestFilter {

    private final EmployeeRepository employees;
    private final ObjectMapper json;

    public ActiveEmployeeFilter(EmployeeRepository employees, ObjectMapper json) {
        this.employees = employees;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            if (!isActive(token.getName())) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                json.writeValue(response.getOutputStream(), ApiError.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        "This account is no longer active",
                        request.getRequestURI()));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isActive(String subject) {
        UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        Optional<String> status = employees.findStatusById(id);
        return status.isPresent() && EmployeeStatus.ACTIVE.name().equals(status.get());
    }
}
