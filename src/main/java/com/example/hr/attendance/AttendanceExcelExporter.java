package com.example.hr.attendance;

import com.example.hr.common.BadRequestException;
import com.example.hr.employee.Employee;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The monthly attendance sheet: a header row and then <b>one row per employee-day</b> —
 * the same derived figures the API returns, so the export can never disagree with the
 * screen. HR/ADMIN only (checked by {@link AttendanceService#exportableEmployees()}).
 */
@Component
public class AttendanceExcelExporter {

    public static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final String[] HEADERS = {
            "Employee no", "Employee", "Department", "Date", "Day", "Day kind", "Status", "Leave",
            "First in", "Last out", "Sessions", "Worked minutes", "Worked hours", "Late minutes",
            "Overtime minutes", "Holiday/weekend minutes"
    };

    private final AttendanceService attendance;
    private final AttendanceDeriver deriver;

    AttendanceExcelExporter(AttendanceService attendance, AttendanceDeriver deriver) {
        this.attendance = attendance;
        this.deriver = deriver;
    }

    @Transactional(readOnly = true)
    public byte[] export(int year, int month) {
        if (month < 1 || month > 12) {
            throw new BadRequestException("month must be between 1 and 12");
        }
        if (year < 2000 || year > 2100) {
            throw new BadRequestException("year must be between 2000 and 2100");
        }
        List<Employee> employees = attendance.exportableEmployees();
        YearMonth period = YearMonth.of(year, month);
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        Map<UUID, List<DailyAttendance>> derived = deriver.deriveAll(employees, from, to);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Attendance " + period);
            CellStyle bold = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(bold);
            }

            int rowIndex = 1;
            for (Employee employee : employees) {
                for (DailyAttendance day : derived.getOrDefault(employee.getId(), List.of())) {
                    Row row = sheet.createRow(rowIndex++);
                    int column = 0;
                    row.createCell(column++).setCellValue(employee.getEmployeeNumber());
                    row.createCell(column++).setCellValue(employee.getFullName());
                    row.createCell(column++).setCellValue(employee.getDepartment() == null
                            ? "" : employee.getDepartment().getName());
                    row.createCell(column++).setCellValue(day.day().toString());
                    row.createCell(column++).setCellValue(day.day().getDayOfWeek().toString());
                    row.createCell(column++).setCellValue(day.dayKind().name());
                    row.createCell(column++).setCellValue(day.status().name());
                    row.createCell(column++).setCellValue(day.leave().name());
                    row.createCell(column++).setCellValue(day.firstIn() == null
                            ? "" : day.firstIn().format(TIME));
                    row.createCell(column++).setCellValue(day.lastOut() == null
                            ? "" : day.lastOut().format(TIME));
                    row.createCell(column++).setCellValue(day.sessionCount());
                    row.createCell(column++).setCellValue(day.workedMinutes());
                    row.createCell(column++).setCellValue(day.workedMinutes() / 60.0);
                    row.createCell(column++).setCellValue(day.lateMinutes());
                    row.createCell(column++).setCellValue(day.weekdayOvertimeMinutes());
                    row.createCell(column).setCellValue(day.restDayMinutes());
                }
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, 4000);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write the attendance workbook", ex);
        }
    }
}
