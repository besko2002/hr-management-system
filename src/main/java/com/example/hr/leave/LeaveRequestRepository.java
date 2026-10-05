package com.example.hr.leave;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {

    List<LeaveRequest> findByEmployeeIdAndStatusOrderByStartDateAsc(UUID employeeId, LeaveStatus status);

    /**
     * Loads one request with {@code SELECT … FOR UPDATE}. Every state change takes this
     * lock <em>before</em> it checks the current status, otherwise two simultaneous
     * approvals could both read PENDING and the second would try to spend a reservation
     * the first had already consumed. With the row locked, the loser reads the committed
     * APPROVED state and is refused with a clean 409.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") UUID id);

    /**
     * True when the employee already has a PENDING or APPROVED request touching any day of
     * {@code [start, end]}. Inclusive on both sides, so adjacent ranges
     * (…ends on the 5th, next starts on the 6th) do <em>not</em> overlap, while any shared
     * day does. REJECTED and CANCELLED requests are ignored.
     */
    @Query("""
            select count(r) > 0 from LeaveRequest r
             where r.employee.id = :employeeId
               and r.status in :blockingStatuses
               and r.startDate <= :end
               and r.endDate >= :start
            """)
    boolean overlaps(@Param("employeeId") UUID employeeId,
                     @Param("start") LocalDate start,
                     @Param("end") LocalDate end,
                     @Param("blockingStatuses") Collection<LeaveStatus> blockingStatuses);

    /** PENDING requests of the caller's direct reports. */
    @Query(value = """
            select r from LeaveRequest r
              join fetch r.employee e
             where e.manager.id = :managerId
               and r.status = :status
            """,
            countQuery = """
            select count(r) from LeaveRequest r
             where r.employee.manager.id = :managerId
               and r.status = :status
            """)
    Page<LeaveRequest> findByManager(@Param("managerId") UUID managerId,
                                     @Param("status") LeaveStatus status,
                                     Pageable pageable);

    /** Every request in one state, company-wide (HR/ADMIN view of the approval queue). */
    @Query(value = """
            select r from LeaveRequest r
              join fetch r.employee e
             where r.status = :status
            """,
            countQuery = "select count(r) from LeaveRequest r where r.status = :status")
    Page<LeaveRequest> findByStatus(@Param("status") LeaveStatus status, Pageable pageable);

    /**
     * Team calendar: APPROVED leave of {@code rootId} and every descendant, overlapping
     * {@code [from, to]}. One recursive CTE, no Java-side traversal, and the projection
     * deliberately omits {@code reason} — the calendar must never leak why somebody is off.
     *
     * <p>Columns: request id, employee id, full name, leave type, start date, end date, working days.
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
            select r.id, e.id, e.full_name, r.leave_type, r.start_date, r.end_date, r.working_days
              from leave_requests r
              join employees e on e.id = r.employee_id
             where r.employee_id in (select id from subtree)
               and r.status = 'APPROVED'
               and r.start_date <= :to
               and r.end_date >= :from
             order by r.start_date, e.full_name
            """, nativeQuery = true)
    List<Object[]> findTeamCalendar(@Param("rootId") UUID rootId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /**
     * APPROVED leave of several employees overlapping {@code [from, to]}, with the
     * paid/unpaid flag of the leave type joined in. Used by attendance (to tell ON_LEAVE
     * from ABSENT) and by payroll (which deducts only UNPAID days).
     *
     * <p>Columns: employee id, start date, end date, {@code paid}.
     */
    @Query("""
            select r.employee.id, r.startDate, r.endDate, t.paid
              from LeaveRequest r, LeaveTypeDefinition t
             where t.code = r.leaveType
               and r.employee.id in :employeeIds
               and r.status = com.example.hr.leave.LeaveStatus.APPROVED
               and r.startDate <= :to
               and r.endDate >= :from
            """)
    List<Object[]> findApprovedCoverage(@Param("employeeIds") Collection<UUID> employeeIds,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /** The same projection for the whole company (HR/ADMIN). */
    @Query(value = """
            select r.id, e.id, e.full_name, r.leave_type, r.start_date, r.end_date, r.working_days
              from leave_requests r
              join employees e on e.id = r.employee_id
             where r.status = 'APPROVED'
               and r.start_date <= :to
               and r.end_date >= :from
             order by r.start_date, e.full_name
            """, nativeQuery = true)
    List<Object[]> findCompanyCalendar(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
