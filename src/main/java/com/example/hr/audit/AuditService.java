package com.example.hr.audit;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.employee.dto.PageResponse;
import com.example.hr.security.CurrentEmployee;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads Envers history for an employee and flattens it to per-field before/after diffs.
 * HR and ADMIN only — anybody else gets 404 so salary-related history cannot be probed.
 */
@Service
public class AuditService {

    /** Fields the API surfaces (PLAN: salary / position / manager / status + role / department). */
    private static final List<String> TRACKED_FIELDS = List.of(
            "salary", "jobTitle", "managerId", "status", "role", "departmentId");

    private final EntityManager entityManager;
    private final EmployeeRepository employees;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;
    private final int maxPageSize;

    AuditService(EntityManager entityManager, EmployeeRepository employees, EmployeeAccessPolicy policy,
                 CurrentEmployee currentEmployee,
                 @Value("${app.employees.max-page-size:100}") int maxPageSize) {
        this.entityManager = entityManager;
        this.employees = employees;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
        this.maxPageSize = maxPageSize;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditChangeResponse> employeeHistory(UUID employeeId, int page, int size) {
        Employee actor = currentEmployee.require();
        // Out-of-scope (including the employee themself and their manager) → 404, never 403.
        if (!policy.isHrOrAdmin(actor) || !employees.existsById(employeeId)) {
            throw new ResourceNotFoundException("No employee with id " + employeeId);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(Employee.class, false, true)
                .add(AuditEntity.id().eq(employeeId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<AuditChangeResponse> changes = new ArrayList<>();
        Map<String, String> previous = Map.of();
        for (Object[] row : rows) {
            Employee snapshot = (Employee) row[0];
            AuditRevision revision = (AuditRevision) row[1];
            RevisionType type = (RevisionType) row[2];
            Map<String, String> current = snapshotValues(snapshot);
            Instant at = Instant.ofEpochMilli(revision.getTimestamp());
            if (type == RevisionType.ADD) {
                for (String field : TRACKED_FIELDS) {
                    String after = current.get(field);
                    if (after != null) {
                        changes.add(new AuditChangeResponse(revision.getId(), at, revision.getChangedBy(),
                                field, null, after));
                    }
                }
            } else if (type == RevisionType.MOD) {
                for (String field : TRACKED_FIELDS) {
                    String before = previous.get(field);
                    String after = current.get(field);
                    if (!Objects.equals(before, after)) {
                        changes.add(new AuditChangeResponse(revision.getId(), at, revision.getChangedBy(),
                                field, before, after));
                    }
                }
            } else if (type == RevisionType.DEL) {
                for (String field : TRACKED_FIELDS) {
                    String before = previous.get(field);
                    if (before != null) {
                        changes.add(new AuditChangeResponse(revision.getId(), at, revision.getChangedBy(),
                                field, before, null));
                    }
                }
            }
            previous = current;
        }

        // Newest first.
        changes = new ArrayList<>(changes.reversed());

        int pageIndex = Math.max(page, 0);
        int pageSize = clampSize(size);
        int from = Math.min(pageIndex * pageSize, changes.size());
        int to = Math.min(from + pageSize, changes.size());
        List<AuditChangeResponse> slice = List.copyOf(changes.subList(from, to));
        return PageResponse.of(new PageImpl<>(slice, PageRequest.of(pageIndex, pageSize), changes.size()),
                c -> c);
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, maxPageSize);
    }

    private static Map<String, String> snapshotValues(Employee employee) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("salary", money(employee.getSalary()));
        values.put("jobTitle", employee.getJobTitle());
        values.put("managerId", id(employee.managerId()));
        values.put("status", employee.getStatus() == null ? null : employee.getStatus().name());
        values.put("role", employee.getRole() == null ? null : employee.getRole().name());
        values.put("departmentId", employee.getDepartment() == null ? null : id(employee.getDepartment().getId()));
        return values;
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String id(UUID value) {
        return value == null ? null : value.toString();
    }
}
