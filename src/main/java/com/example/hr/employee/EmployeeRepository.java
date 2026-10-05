package com.example.hr.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Employee persistence. Everything hierarchy-related is a PostgreSQL recursive CTE
 * executed as one native query — never Java-side recursion, never N+1.
 */
public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    long countByDepartmentId(UUID departmentId);

    List<Employee> findByManagerIdOrderByFullNameAsc(UUID managerId);

    /** Everybody still employed on a given date; used by the monthly leave accrual. */
    List<Employee> findByStatusAndHireDateLessThanEqual(EmployeeStatus status, java.time.LocalDate hiredOnOrBefore);

    /**
     * Everybody a monthly payroll run must produce a payslip for: hired on or before the
     * end of the month, with a base salary, and either still ACTIVE or terminated
     * <em>inside</em> that month (so a final, pro-rated month is still paid). An employee
     * with no {@code salary} is not payable and is deliberately left out.
     */
    @Query("""
            select e from Employee e
             where e.hireDate <= :monthEnd
               and e.salary is not null
               and (e.status = com.example.hr.employee.EmployeeStatus.ACTIVE
                    or (e.status = com.example.hr.employee.EmployeeStatus.TERMINATED
                        and e.terminatedAt is not null
                        and e.terminatedAt >= :monthStart
                        and e.terminatedAt <= :monthEnd))
             order by e.employeeNumber
            """)
    List<Employee> findPayrollEligible(@Param("monthStart") java.time.LocalDate monthStart,
                                       @Param("monthEnd") java.time.LocalDate monthEnd);

    /** Next human-readable employee number (EMP-0001, ...) straight from the database sequence. */
    @Query(value = "select 'EMP-' || lpad(nextval('employee_number_seq')::text, 4, '0')", nativeQuery = true)
    String nextEmployeeNumber();

    /**
     * Lightweight per-request status probe: a single primary-key lookup used by the
     * security filter to reject tokens of employees who have been terminated.
     */
    @Query(value = "select e.status from employees e where e.id = :id", nativeQuery = true)
    Optional<String> findStatusById(@Param("id") UUID id);

    /**
     * True when {@code candidateId} is {@code rootId} itself or sits anywhere below it in
     * the tree. Used inside the manager-assignment transaction to refuse cycles.
     */
    @Query(value = """
            with recursive subtree as (
                select e.id
                  from employees e
                 where e.id = :rootId
                union all
                select child.id
                  from employees child
                  join subtree s on child.manager_id = s.id
            )
            select exists (select 1 from subtree where id = :candidateId)
            """, nativeQuery = true)
    boolean isSelfOrDescendant(@Param("rootId") UUID rootId, @Param("candidateId") UUID candidateId);

    /** True when {@code ancestorId} appears anywhere above {@code employeeId} in the tree. */
    @Query(value = """
            with recursive chain as (
                select e.manager_id as id
                  from employees e
                 where e.id = :employeeId
                union all
                select parent.manager_id
                  from employees parent
                  join chain c on parent.id = c.id
            )
            select exists (select 1 from chain where id = :ancestorId)
            """, nativeQuery = true)
    boolean isAncestorOf(@Param("ancestorId") UUID ancestorId, @Param("employeeId") UUID employeeId);

    /**
     * All descendants (direct and indirect) of {@code rootId} with their depth
     * (1 = direct report). Columns: id, employee_number, full_name, email, job_title,
     * department name, status, depth, manager_id.
     */
    @Query(value = """
            with recursive subtree as (
                select e.id, e.manager_id, 1 as depth
                  from employees e
                 where e.manager_id = :rootId
                   and e.status = 'ACTIVE'
                union all
                select child.id, child.manager_id, s.depth + 1
                  from employees child
                  join subtree s on child.manager_id = s.id
                 where child.status = 'ACTIVE'
            )
            select e.id, e.employee_number, e.full_name, e.email, e.job_title,
                   d.name, e.status, s.depth, e.manager_id
              from subtree s
              join employees e on e.id = s.id
              left join departments d on d.id = e.department_id
             order by s.depth, e.full_name
            """, nativeQuery = true)
    List<Object[]> findDescendants(@Param("rootId") UUID rootId);

    /**
     * The management chain upwards from {@code employeeId} to the root, nearest manager first.
     * Columns: id, employee_number, full_name, email, job_title, department name, status, depth.
     */
    @Query(value = """
            with recursive chain as (
                select e.id, e.manager_id, 0 as depth
                  from employees e
                 where e.id = :employeeId
                union all
                select parent.id, parent.manager_id, c.depth + 1
                  from employees parent
                  join chain c on parent.id = c.manager_id
            )
            select e.id, e.employee_number, e.full_name, e.email, e.job_title,
                   d.name, e.status, c.depth
              from chain c
              join employees e on e.id = c.id
              left join departments d on d.id = e.department_id
             where c.depth > 0
             order by c.depth
            """, nativeQuery = true)
    List<Object[]> findManagementChain(@Param("employeeId") UUID employeeId);

    /**
     * Every active employee with the data the org chart needs, in one query; the tree is
     * assembled in memory from this single result set.
     * Columns: id, employee_number, full_name, job_title, department name, manager_id.
     */
    @Query(value = """
            with recursive tree as (
                select e.id, e.manager_id, 0 as depth
                  from employees e
                 where e.manager_id is null
                   and e.status = 'ACTIVE'
                union all
                select child.id, child.manager_id, t.depth + 1
                  from employees child
                  join tree t on child.manager_id = t.id
                 where child.status = 'ACTIVE'
            )
            select e.id, e.employee_number, e.full_name, e.job_title, d.name, e.manager_id
              from tree t
              join employees e on e.id = t.id
              left join departments d on d.id = e.department_id
             order by t.depth, e.full_name
            """, nativeQuery = true)
    List<Object[]> findOrgChartRows();
}
