package com.example.hr.leave;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    List<LeaveBalance> findByEmployeeIdAndYearOrderByLeaveTypeAsc(UUID employeeId, int year);

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeAndYear(UUID employeeId, LeaveType leaveType, int year);

    /**
     * Locks <em>every</em> balance row of one employee for one year with
     * {@code SELECT … FOR UPDATE}, in a deterministic order (by leave type, so two
     * transactions can never deadlock against each other).
     *
     * <p>This is the single serialisation point of all leave bookkeeping: check and
     * reserve, approve, reject, cancel and the termination refund all take it first, which
     * is what makes "two simultaneous requests cannot overspend the balance" true — and,
     * because all types of one employee are locked together, the overlap check that runs
     * under the same lock is race-free across leave types as well. A request never spans
     * two calendar years, so one year's rows are the complete working set.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.employeeId = :employeeId and b.year = :year order by b.leaveType")
    List<LeaveBalance> lockForUpdate(@Param("employeeId") UUID employeeId, @Param("year") int year);

    /**
     * Creates the balance row only if it does not exist yet, in one atomic statement, so
     * two concurrent first-time requests cannot both insert it (the loser's
     * {@code on conflict do nothing} simply does nothing).
     *
     * @return 1 when a row was created, 0 when it already existed
     */
    // clearAutomatically stays false on purpose: callers hold managed entities (the leave
    // request being decided) across this call and must not have them detached.
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            insert into leave_balances (id, employee_id, leave_type, balance_year,
                                        entitled_days, carried_over_days, used_days, pending_days,
                                        created_at, updated_at, version)
            values (:id, :employeeId, cast(:leaveType as varchar), :year, :entitledDays, :carriedOverDays,
                    0, 0, now(), now(), 0)
            on conflict (employee_id, leave_type, balance_year) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("employeeId") UUID employeeId,
                       @Param("leaveType") String leaveType,
                       @Param("year") int year,
                       @Param("entitledDays") int entitledDays,
                       @Param("carriedOverDays") int carriedOverDays);
}
