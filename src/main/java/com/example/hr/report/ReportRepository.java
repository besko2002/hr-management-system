package com.example.hr.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Report aggregations as SQL. Money stays {@link BigDecimal}; nothing is loaded row-by-row
 * into the application just to be summed.
 */
@Repository
class ReportRepository {

    private static final String UNASSIGNED = "Unassigned";

    private final JdbcTemplate jdbc;

    ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<ReportDtos.HeadcountRow> headcount() {
        return jdbc.query("""
                select coalesce(d.name, ?) as department,
                       e.status,
                       count(*)::bigint as headcount
                  from employees e
                  left join departments d on d.id = e.department_id
                 group by 1, 2
                 order by department, e.status
                """, (rs, rowNum) -> new ReportDtos.HeadcountRow(
                        rs.getString("department"),
                        rs.getString("status"),
                        rs.getLong("headcount")), UNASSIGNED);
    }

    List<ReportDtos.LeaveSummaryRow> leaveSummary(int year) {
        return jdbc.query("""
                select coalesce(d.name, ?) as department,
                       b.leave_type,
                       coalesce(sum(b.used_days), 0)::int as used_days,
                       coalesce(sum(b.pending_days), 0)::int as pending_days,
                       coalesce(sum(b.entitled_days + b.carried_over_days
                                    - b.used_days - b.pending_days), 0)::int as remaining_days
                  from leave_balances b
                  join employees e on e.id = b.employee_id
                  left join departments d on d.id = e.department_id
                 where b.balance_year = ?
                 group by 1, 2
                 order by department, b.leave_type
                """, (rs, rowNum) -> new ReportDtos.LeaveSummaryRow(
                        rs.getString("department"),
                        rs.getString("leave_type"),
                        rs.getInt("used_days"),
                        rs.getInt("pending_days"),
                        rs.getInt("remaining_days")), UNASSIGNED, year);
    }

    List<ReportDtos.PayrollSummaryRow> payrollSummary(int year) {
        return jdbc.query("""
                select r.run_month as month,
                       coalesce(sum(p.gross_earnings), 0) as gross_earnings,
                       coalesce(sum(p.insurance), 0) as insurance,
                       coalesce(sum(p.tax), 0) as tax,
                       coalesce(sum(p.net_pay), 0) as net_pay,
                       count(*)::bigint as payslip_count
                  from payroll_runs r
                  join payslips p on p.run_id = r.id
                 where r.run_year = ?
                   and r.status = 'FINALIZED'
                 group by r.run_month
                 order by r.run_month
                """, (rs, rowNum) -> new ReportDtos.PayrollSummaryRow(
                        rs.getInt("month"),
                        money(rs.getBigDecimal("gross_earnings")),
                        money(rs.getBigDecimal("insurance")),
                        money(rs.getBigDecimal("tax")),
                        money(rs.getBigDecimal("net_pay")),
                        rs.getLong("payslip_count")), year);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }
}
