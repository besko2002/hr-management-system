package com.example.hr.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AccrualLogRepository extends JpaRepository<AccrualLogEntry, UUID> {

    List<AccrualLogEntry> findByEmployeeIdAndYearOrderByMonthAsc(UUID employeeId, int year);

    long countByYearAndMonth(int year, int month);

    /**
     * The idempotency guard. Attempts to claim (employee, type, year, month); the unique
     * key makes a second attempt a no-op instead of a second credit.
     *
     * @return 1 when this call claimed the month (so the caller must credit the days),
     *         0 when it had already been claimed (so the caller must credit nothing)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            insert into accrual_log (id, employee_id, leave_type, accrual_year, accrual_month, days, created_at)
            values (:id, :employeeId, cast(:leaveType as varchar), :year, :month, :days, now())
            on conflict (employee_id, leave_type, accrual_year, accrual_month) do nothing
            """, nativeQuery = true)
    int claimMonth(@Param("id") UUID id,
                   @Param("employeeId") UUID employeeId,
                   @Param("leaveType") String leaveType,
                   @Param("year") int year,
                   @Param("month") int month,
                   @Param("days") int days);
}
