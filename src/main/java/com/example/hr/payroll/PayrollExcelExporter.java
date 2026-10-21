package com.example.hr.payroll;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The payroll register of one run: a bold header row, one row per payslip, and a totals
 * row built from real {@code SUM()} formulas (not pre-computed numbers) so the sheet stays
 * correct if a reader filters or edits it. Every money column is a numeric cell with two
 * decimals — never a string.
 */
@Component
public class PayrollExcelExporter {

    public static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String[] HEADERS = {
            "Employee no", "Employee", "Department", "Base salary", "Working days", "Payable days",
            "Unpaid leave days", "Absent days", "Late minutes", "Overtime hours", "Holiday hours",
            "Base for period", "Overtime pay", "Holiday pay", "Unpaid leave deduction",
            "Absence deduction", "Gross earnings", "Insurable wage", "Insurance", "Taxable income",
            "Tax", "Net pay"
    };

    /** The money/number columns that the totals row sums. */
    private static final int[] SUMMED = {3, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};

    private final PayrollService payroll;

    PayrollExcelExporter(PayrollService payroll) {
        this.payroll = payroll;
    }

    @Transactional(readOnly = true)
    public byte[] export(UUID runId) {
        PayrollRun run = payroll.requireRunForExport(runId);
        List<Payslip> slips = payroll.payslipsOf(run.getId());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Payroll " + run.period());
            DataFormat formats = workbook.createDataFormat();

            CellStyle bold = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);

            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(formats.getFormat("#,##0.00"));

            CellStyle moneyTotal = workbook.createCellStyle();
            moneyTotal.setDataFormat(formats.getFormat("#,##0.00"));
            moneyTotal.setFont(boldFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(bold);
            }

            int rowIndex = 1;
            for (Payslip slip : slips) {
                Row row = sheet.createRow(rowIndex++);
                int column = 0;
                row.createCell(column++).setCellValue(slip.getEmployeeNumber());
                row.createCell(column++).setCellValue(slip.getEmployeeName());
                row.createCell(column++).setCellValue(
                        slip.getDepartmentName() == null ? "" : slip.getDepartmentName());
                amount(row, column++, slip.getBaseSalary(), money);
                row.createCell(column++).setCellValue(slip.getWorkingDaysInMonth());
                row.createCell(column++).setCellValue(slip.getPayableWorkingDays());
                row.createCell(column++).setCellValue(slip.getUnpaidLeaveDays());
                row.createCell(column++).setCellValue(slip.getAbsentDays());
                row.createCell(column++).setCellValue(slip.getLateMinutes());
                amount(row, column++, slip.getOvertimeHours(), money);
                amount(row, column++, slip.getHolidayHours(), money);
                amount(row, column++, slip.getProratedBase(), money);
                amount(row, column++, slip.getOvertimePay(), money);
                amount(row, column++, slip.getHolidayPay(), money);
                amount(row, column++, slip.getUnpaidLeaveDeduction(), money);
                amount(row, column++, slip.getAbsenceDeduction(), money);
                amount(row, column++, slip.getGrossEarnings(), money);
                amount(row, column++, slip.getInsurableWage(), money);
                amount(row, column++, slip.getInsurance(), money);
                amount(row, column++, slip.getTaxableIncome(), money);
                amount(row, column++, slip.getTax(), money);
                amount(row, column, slip.getNetPay(), money);
            }

            Row totals = sheet.createRow(rowIndex);
            Cell label = totals.createCell(0);
            label.setCellValue("TOTAL (" + slips.size() + " employees)");
            label.setCellStyle(bold);
            if (!slips.isEmpty()) {
                for (int column : SUMMED) {
                    String letter = CellReference.convertNumToColString(column);
                    Cell cell = totals.createCell(column, CellType.FORMULA);
                    cell.setCellFormula("SUM(%s2:%s%d)".formatted(letter, letter, rowIndex));
                    cell.setCellStyle(moneyTotal);
                }
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, 4600);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write the payroll workbook", ex);
        }
    }

    private static void amount(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column, CellType.NUMERIC);
        // doubleValue() is used only to hand POI a cell value; every stored and computed
        // figure in this module is a BigDecimal at scale 2.
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }
}
