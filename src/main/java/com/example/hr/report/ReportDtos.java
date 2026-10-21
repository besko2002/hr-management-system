package com.example.hr.report;

import java.math.BigDecimal;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record HeadcountRow(String department, String status, long count) {
    }

    public record LeaveSummaryRow(
            String department,
            String leaveType,
            int usedDays,
            int pendingDays,
            int remainingDays) {
    }

    public record PayrollSummaryRow(
            int month,
            BigDecimal grossEarnings,
            BigDecimal insurance,
            BigDecimal tax,
            BigDecimal netPay,
            long payslipCount) {
    }

    public record AttendanceSummaryRow(
            String department,
            long lateDays,
            long absentDays,
            long overtimeMinutes) {
    }
}
