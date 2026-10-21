package com.example.hr.employee;

import com.example.hr.employee.dto.ChainMemberResponse;
import com.example.hr.employee.dto.EmployeeProfile;
import com.example.hr.employee.dto.EmployeeResponse;
import com.example.hr.employee.dto.EmployeeView;
import com.example.hr.employee.dto.TeamMemberResponse;

import java.util.UUID;

/** Entity to DTO mapping. Must be called inside a transaction (lazy department/manager). */
public final class EmployeeMapper {

    private EmployeeMapper() {
    }

    public static EmployeeResponse withSalary(Employee e) {
        return new EmployeeResponse(
                e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getEmail(), e.getRole(), e.getJobTitle(),
                e.getDepartment() == null ? null : e.getDepartment().getId(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getManager() == null ? null : e.getManager().getId(),
                e.getManager() == null ? null : e.getManager().getFullName(),
                e.getHireDate(), e.getSalary(), e.getStatus(), e.getTerminatedAt(), e.isMustChangePassword(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public static EmployeeView withoutSalary(Employee e) {
        return new EmployeeView(
                e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getEmail(), e.getRole(), e.getJobTitle(),
                e.getDepartment() == null ? null : e.getDepartment().getId(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getManager() == null ? null : e.getManager().getId(),
                e.getManager() == null ? null : e.getManager().getFullName(),
                e.getHireDate(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public static EmployeeProfile forViewer(Employee e, boolean includeSalary) {
        return includeSalary ? withSalary(e) : withoutSalary(e);
    }

    /** Row layout of {@link EmployeeRepository#findDescendants(UUID)}. */
    public static TeamMemberResponse toTeamMember(Object[] row) {
        return new TeamMemberResponse(
                (UUID) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4], (String) row[5],
                (UUID) row[8], EmployeeStatus.valueOf((String) row[6]), ((Number) row[7]).intValue());
    }

    public static TeamMemberResponse toDirectReport(Employee e) {
        return new TeamMemberResponse(
                e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getEmail(), e.getJobTitle(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.managerId(), e.getStatus(), 1);
    }

    /** Row layout of {@link EmployeeRepository#findManagementChain(UUID)}. */
    public static ChainMemberResponse toChainMember(Object[] row) {
        return new ChainMemberResponse(
                (UUID) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4], (String) row[5],
                EmployeeStatus.valueOf((String) row[6]), ((Number) row[7]).intValue());
    }
}
