package com.example.hr.report;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.attendance.AttendanceDeriver;
import com.example.hr.attendance.AttendanceStatus;
import com.example.hr.attendance.DailyAttendance;
import com.example.hr.common.BadRequestException;
import com.example.hr.department.Department;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.security.CurrentEmployee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private static final String UNASSIGNED = "Unassigned";

    private final ReportRepository reports;
    private final EmployeeRepository employees;
    private final AttendanceDeriver attendance;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;

    ReportService(ReportRepository reports, EmployeeRepository employees, AttendanceDeriver attendance,
                  EmployeeAccessPolicy policy, CurrentEmployee currentEmployee) {
        this.reports = reports;
        this.employees = employees;
        this.attendance = attendance;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.HeadcountRow> headcount() {
        requireHr();
        return reports.headcount();
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.LeaveSummaryRow> leaveSummary(Integer year) {
        requireHr();
        return reports.leaveSummary(requireYear(year));
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.PayrollSummaryRow> payrollSummary(Integer year) {
        requireHr();
        return reports.payrollSummary(requireYear(year));
    }

    /**
     * Late / absent / overtime per department for one calendar month. Derivation reuses
     * {@link AttendanceDeriver} (same rules as the attendance module) and only department
     * totals leave this method — never the full day list.
     */
    @Transactional(readOnly = true)
    public List<ReportDtos.AttendanceSummaryRow> attendanceSummary(Integer year, Integer month) {
        requireHr();
        int y = requireYear(year);
        if (month == null || month < 1 || month > 12) {
            throw new BadRequestException("month must be between 1 and 12");
        }
        YearMonth period = YearMonth.of(y, month);
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();

        List<Employee> all = employees.findAll();
        Map<UUID, List<DailyAttendance>> byEmployee = attendance.deriveAll(all, from, to);

        Map<String, long[]> totals = new LinkedHashMap<>();
        for (Employee employee : all) {
            String department = departmentName(employee.getDepartment());
            long[] bucket = totals.computeIfAbsent(department, key -> new long[3]);
            for (DailyAttendance day : byEmployee.getOrDefault(employee.getId(), List.of())) {
                if (day.status() == AttendanceStatus.LATE) {
                    bucket[0]++;
                } else if (day.status() == AttendanceStatus.ABSENT) {
                    bucket[1]++;
                }
                bucket[2] += day.overtimeMinutes();
            }
        }

        List<ReportDtos.AttendanceSummaryRow> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            long[] v = entry.getValue();
            rows.add(new ReportDtos.AttendanceSummaryRow(entry.getKey(), v[0], v[1], v[2]));
        }
        rows.sort(Comparator.comparing(ReportDtos.AttendanceSummaryRow::department));
        return rows;
    }

    private void requireHr() {
        policy.requireHrOrAdmin(currentEmployee.require(), "view reports");
    }

    private static int requireYear(Integer year) {
        if (year == null) {
            throw new BadRequestException("year is required");
        }
        if (year < 2000 || year > 2100) {
            throw new BadRequestException("year must be between 2000 and 2100");
        }
        return year;
    }

    private static String departmentName(Department department) {
        return department == null ? UNASSIGNED : department.getName();
    }
}
