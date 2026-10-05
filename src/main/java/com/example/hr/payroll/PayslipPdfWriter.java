package com.example.hr.payroll;

import com.example.hr.payroll.PayrollDtos.PayslipResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders one payslip as a PDF with OpenPDF: company header from configuration, the
 * employee and the period, then <b>every</b> line item — the inputs, the derived rates,
 * the earnings, the deductions, the tax bracket breakdown and the net — so the paper
 * version can be audited exactly like the JSON one.
 */
@Component
public class PayslipPdfWriter {

    public static final String PDF_MEDIA_TYPE = "application/pdf";

    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final PayrollProperties properties;

    PayslipPdfWriter(PayrollProperties properties) {
        this.properties = properties;
    }

    public byte[] write(PayslipResponse slip) {
        Document document = new Document(PageSize.A4, 42, 42, 42, 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

            document.add(paragraph(properties.getCompany().getName(), title));
            document.add(paragraph(properties.getCompany().getAddress(), small));
            document.add(paragraph(" ", small));

            Paragraph heading = paragraph("PAYSLIP — "
                    + YearMonth.of(slip.year(), slip.month()).format(PERIOD),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
            heading.setSpacingAfter(8f);
            document.add(heading);

            document.add(employeeTable(slip));
            document.add(paragraph(" ", small));

            PdfPTable lines = new PdfPTable(new float[] {6f, 3f});
            lines.setWidthPercentage(100);
            section(lines, "Inputs");
            line(lines, "Monthly base salary", slip.baseSalary());
            line(lines, "Working days in month", String.valueOf(slip.workingDaysInMonth()));
            line(lines, "Payable working days", String.valueOf(slip.payableWorkingDays()));
            line(lines, "Unpaid leave days", String.valueOf(slip.unpaidLeaveDays()));
            line(lines, "Absent days", String.valueOf(slip.absentDays()));
            line(lines, "Late minutes (reported, not deducted)", String.valueOf(slip.lateMinutes()));
            line(lines, "Overtime minutes", String.valueOf(slip.overtimeMinutes()));
            line(lines, "Holiday/weekend minutes", String.valueOf(slip.holidayMinutes()));

            section(lines, "Rates");
            line(lines, "Daily rate (base / working days)", slip.dailyRate());
            line(lines, "Hourly rate (daily / " + slip.ratesUsed().workingHoursPerDay() + "h)",
                    slip.hourlyRate());
            line(lines, "Overtime hours x " + slip.ratesUsed().overtimeMultiplier(),
                    slip.overtimeHours().toPlainString());
            line(lines, "Holiday hours x " + slip.ratesUsed().holidayOvertimeMultiplier(),
                    slip.holidayHours().toPlainString());

            section(lines, "Earnings");
            line(lines, "Base for the period", slip.proratedBase());
            line(lines, "Overtime pay", slip.overtimePay());
            line(lines, "Holiday/weekend pay", slip.holidayPay());

            section(lines, "Deductions");
            line(lines, "Unpaid leave", slip.unpaidLeaveDeduction().negate());
            line(lines, "Absence", slip.absenceDeduction().negate());
            total(lines, "Gross earnings", slip.grossEarnings());

            section(lines, "Statutory");
            line(lines, "Insurable wage (clamped "
                    + slip.ratesUsed().minInsurableWage().toPlainString() + " – "
                    + slip.ratesUsed().maxInsurableWage().toPlainString() + ")", slip.insurableWage());
            line(lines, "Social insurance @ " + slip.ratesUsed().insuranceEmployeeRate(),
                    slip.insurance().negate());
            line(lines, "Personal exemption", slip.personalExemption());
            line(lines, "Taxable income", slip.taxableIncome());
            for (TaxBracketCharge charge : slip.taxBreakdown()) {
                String band = charge.to() == null
                        ? "above " + charge.from().toPlainString()
                        : charge.from().toPlainString() + " – " + charge.to().toPlainString();
                line(lines, "  Tax " + band + " @ " + charge.rate()
                        + " on " + charge.taxedAmount().toPlainString(), charge.tax().negate());
            }
            line(lines, "Income tax", slip.tax().negate());
            total(lines, "NET PAY", slip.netPay());
            document.add(lines);

            if (slip.netFloored()) {
                document.add(paragraph("Warning: " + slip.warning(),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.RED)));
            }
            document.add(paragraph(" ", small));
            document.add(paragraph("Figures are illustrative and simplified — this document is not "
                    + "tax or legal advice.", small));
            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Could not render the payslip PDF", ex);
        }
    }

    private static Paragraph paragraph(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_LEFT);
        return paragraph;
    }

    private static PdfPTable employeeTable(PayslipResponse slip) {
        PdfPTable table = new PdfPTable(new float[] {2f, 4f, 2f, 4f});
        table.setWidthPercentage(100);
        cellPair(table, "Employee", slip.employeeName());
        cellPair(table, "Employee no", slip.employeeNumber());
        cellPair(table, "Job title", slip.jobTitle() == null ? "—" : slip.jobTitle());
        cellPair(table, "Department", slip.departmentName() == null ? "—" : slip.departmentName());
        cellPair(table, "Hire date", String.valueOf(slip.hireDate()));
        cellPair(table, "Terminated", slip.terminatedAt() == null ? "—" : slip.terminatedAt().toString());
        cellPair(table, "Period", slip.year() + "-" + String.format("%02d", slip.month()));
        cellPair(table, "Run status", slip.runStatus().name());
        return table;
    }

    private static void cellPair(PdfPTable table, String label, String value) {
        table.addCell(plain(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        table.addCell(plain(value, FontFactory.getFont(FontFactory.HELVETICA, 9)));
    }

    private static PdfPCell plain(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderWidth(0.3f);
        cell.setPadding(4f);
        return cell;
    }

    private static void section(PdfPTable table, String name) {
        PdfPCell cell = new PdfPCell(new Phrase(name,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
        cell.setBackgroundColor(Color.DARK_GRAY);
        cell.setColspan(2);
        cell.setPadding(4f);
        table.addCell(cell);
    }

    private static void line(PdfPTable table, String label, BigDecimal amount) {
        line(table, label, amount.toPlainString());
    }

    private static void line(PdfPTable table, String label, String value) {
        table.addCell(plain(label, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        PdfPCell cell = plain(value, FontFactory.getFont(FontFactory.HELVETICA, 9));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private static void total(PdfPTable table, String label, BigDecimal amount) {
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        table.addCell(plain(label, bold));
        PdfPCell cell = plain(amount.toPlainString(), bold);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }
}
