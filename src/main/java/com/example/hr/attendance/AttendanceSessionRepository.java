package com.example.hr.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    /** Sentinel for {@link #overlaps} when no existing row has to be ignored. */
    UUID NOTHING_EXCLUDED = new UUID(0L, 0L);

    List<AttendanceSession> findByEmployeeIdAndWorkDateBetweenOrderByCheckInAsc(
            UUID employeeId, LocalDate from, LocalDate to);

    /**
     * The employee's open sessions, newest first. There is at most one per work day (the
     * check-in guard), but a day left unclosed in the past can coexist with today's.
     */
    @Query("""
            select s from AttendanceSession s
             where s.employee.id = :employeeId
               and s.checkOut is null
             order by s.checkIn desc
            """)
    List<AttendanceSession> findOpen(@Param("employeeId") UUID employeeId);

    @Query("""
            select s from AttendanceSession s
             where s.employee.id = :employeeId
               and s.checkOut is null
               and s.workDate = :workDate
            """)
    Optional<AttendanceSession> findOpenOn(@Param("employeeId") UUID employeeId,
                                           @Param("workDate") LocalDate workDate);

    /**
     * True when the employee already has a session whose interval overlaps
     * {@code [checkIn, checkOut)}. An open session is treated as covering only its own
     * check-in instant — it cannot be known how far it reaches — and {@code excludeId}
     * lets a correction ignore the row it is editing (pass {@link #NOTHING_EXCLUDED} when
     * there is none). Touching endpoints do not overlap: leaving at 13:00 and returning at
     * 13:00 is legal.
     */
    @Query("""
            select count(s) > 0 from AttendanceSession s
             where s.employee.id = :employeeId
               and s.id <> :excludeId
               and s.checkIn < :checkOut
               and coalesce(s.checkOut, s.checkIn) > :checkIn
            """)
    boolean overlaps(@Param("employeeId") UUID employeeId,
                     @Param("checkIn") Instant checkIn,
                     @Param("checkOut") Instant checkOut,
                     @Param("excludeId") UUID excludeId);

    /** Every session of a set of employees in a date range — one query for a team view or an export. */
    @Query("""
            select s from AttendanceSession s
              join fetch s.employee e
             where s.employee.id in :employeeIds
               and s.workDate between :from and :to
             order by e.fullName, s.checkIn
            """)
    List<AttendanceSession> findForEmployees(@Param("employeeIds") Collection<UUID> employeeIds,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);
}
