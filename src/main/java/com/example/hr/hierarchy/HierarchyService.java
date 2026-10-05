package com.example.hr.hierarchy;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.common.ConflictException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeMapper;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.employee.EmployeeStatus;
import com.example.hr.employee.dto.ChainMemberResponse;
import com.example.hr.employee.dto.EmployeeProfile;
import com.example.hr.employee.dto.OrgChartNode;
import com.example.hr.employee.dto.TeamMemberResponse;
import com.example.hr.security.CurrentEmployee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The org tree: manager assignment with cycle prevention, team queries and the org chart.
 * All traversal is done by PostgreSQL recursive CTEs (see {@link EmployeeRepository}).
 */
@Service
public class HierarchyService {

    private final EmployeeRepository employees;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;

    HierarchyService(EmployeeRepository employees, EmployeeAccessPolicy policy, CurrentEmployee currentEmployee) {
        this.employees = employees;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
    }

    /**
     * Sets (or clears, when {@code managerId} is null) an employee's manager.
     * Refuses with 409 when the new manager is the employee themself or any of their
     * descendants — the check runs as a recursive CTE inside this transaction — and when
     * the proposed manager is terminated.
     */
    @Transactional
    public EmployeeProfile setManager(UUID employeeId, UUID managerId) {
        Employee actor = currentEmployee.require();
        Employee employee = require(employeeId);
        policy.requireCanManage(actor, employee, "reassign the manager of");

        if (managerId == null) {
            employee.changeManager(null);
            return EmployeeMapper.forViewer(employees.save(employee), policy.canSeeSalary(actor, employee));
        }

        Employee manager = require(managerId);
        if (manager.getId().equals(employee.getId())) {
            throw new ConflictException("An employee cannot be their own manager");
        }
        if (!manager.isActive()) {
            throw new ConflictException("A terminated employee cannot be a manager");
        }
        if (employees.isSelfOrDescendant(employeeId, managerId)) {
            throw new ConflictException(
                    "That change would create a cycle: the proposed manager reports to this employee");
        }
        employee.changeManager(manager);
        return EmployeeMapper.forViewer(employees.save(employee), policy.canSeeSalary(actor, employee));
    }

    /** Direct reports of the caller. No salary fields. */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> myTeam() {
        Employee me = currentEmployee.require();
        return employees.findByManagerIdOrderByFullNameAsc(me.getId()).stream()
                .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE)
                .map(EmployeeMapper::toDirectReport)
                .toList();
    }

    /** Every descendant of the caller with its depth (1 = direct report). No salary fields. */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> myWholeTeam() {
        Employee me = currentEmployee.require();
        return employees.findDescendants(me.getId()).stream().map(EmployeeMapper::toTeamMember).toList();
    }

    /** The management chain of an employee upwards to the root. */
    @Transactional(readOnly = true)
    public List<ChainMemberResponse> managementChain(UUID employeeId) {
        Employee actor = currentEmployee.require();
        Employee target = require(employeeId);
        policy.requireCanViewProfile(actor, target);
        return employees.findManagementChain(employeeId).stream().map(EmployeeMapper::toChainMember).toList();
    }

    /**
     * The whole company as a nested tree, built from one recursive CTE and assembled in
     * memory (no N+1). Roots are the employees without a manager.
     */
    @Transactional(readOnly = true)
    public List<OrgChartNode> orgChart() {
        currentEmployee.require();
        List<Object[]> rows = employees.findOrgChartRows();

        Map<UUID, List<OrgChartNode>> childrenById = new LinkedHashMap<>();
        Map<UUID, UUID> parentById = new LinkedHashMap<>();
        Map<UUID, OrgChartNode> nodes = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            List<OrgChartNode> children = new ArrayList<>();
            childrenById.put(id, children);
            nodes.put(id, new OrgChartNode(id, (String) row[1], (String) row[2], (String) row[3], (String) row[4],
                    children));
            parentById.put(id, (UUID) row[5]);
        }

        List<OrgChartNode> roots = new ArrayList<>();
        for (Map.Entry<UUID, OrgChartNode> entry : nodes.entrySet()) {
            UUID parentId = parentById.get(entry.getKey());
            List<OrgChartNode> siblings = parentId == null ? roots : childrenById.get(parentId);
            (siblings == null ? roots : siblings).add(entry.getValue());
        }
        return roots;
    }

    private Employee require(UUID id) {
        return employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No employee with id " + id));
    }
}
