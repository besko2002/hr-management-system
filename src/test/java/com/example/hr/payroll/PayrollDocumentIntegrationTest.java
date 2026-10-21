package com.example.hr.payroll;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two generated documents. The PDF is parsed back with PDFBox (an independent library
 * from the OpenPDF writer) and the workbook with POI, so neither assertion simply trusts
 * the code that produced the bytes.
 */
class PayrollDocumentIntegrationTest extends AbstractPayrollIntegrationTest {

    private String adminToken;
    private String hrToken;
    private NewEmployee staff;
    private String staffToken;
    private NewEmployee manager;
    private UUID runId;
    private UUID payslipId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        NewEmployee hr = createEmployee(adminToken, "Hana HR", Role.HR, null);
        hrToken = login(hr.email(), hr.password());
        manager = createEmployee(adminToken, "Mary Manager", Role.EMPLOYEE, null);
        staff = createEmployee(adminToken, "Sara Staff", Role.EMPLOYEE, manager.id(), null,
                REFERENCE_SALARY);
        staffToken = login(staff.email(), staff.password());

        buildReferenceMarch(hrToken, staff.id());
        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);
        runId = runIdOf(run);
        payslipId = payslipIdOf(run, staff.id());
    }

    // ------------------------------------------------------------------ PDF

    @Test
    void thePayslipPdfIsAValidPdfContainingEveryLineItem() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());

        byte[] bytes = payslipPdf(staffToken, payslipId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            String text = new PDFTextStripper().getText(document);

            assertThat(text).contains("Example Holding");
            assertThat(text).contains("PAYSLIP");
            assertThat(text).contains("March 2025");
            assertThat(text).contains("Sara Staff");
            assertThat(text).contains(staff.id() == null ? "" : "EMP-");
            // The hand-computed line items, all of them.
            assertThat(text).contains("8800.00");  // base salary
            assertThat(text).contains("400.00");   // daily rate / deductions / holiday pay
            assertThat(text).contains("50.00");    // hourly rate
            assertThat(text).contains("225.00");   // overtime pay
            assertThat(text).contains("968.00");   // insurance
            assertThat(text).contains("1250.00");  // personal exemption
            assertThat(text).contains("6407.00");  // taxable income
            assertThat(text).contains("731.40");   // tax
            assertThat(text).contains("6925.60");  // NET PAY
            assertThat(text).contains("NET PAY");
            assertThat(text).contains("not");      // the disclaimer line
        }
    }

    @Test
    void theContentTypeIsApplicationPdf() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());
        payslipPdf(staffToken, payslipId)
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith("application/pdf"));
    }

    @Test
    void hrCanDownloadADraftPayslipPdfButTheEmployeeCannot() throws Exception {
        payslipPdf(hrToken, payslipId).andExpect(status().isOk());
        payslipPdf(staffToken, payslipId).andExpect(status().isNotFound());
    }

    @Test
    void theManagerGetsNotFoundOnTheReportsPayslipPdf() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());
        payslipPdf(login(manager.email(), manager.password()), payslipId)
                .andExpect(status().isNotFound());
    }

    @Test
    void aFlooredNetPrintsTheWarningOnThePdf() throws Exception {
        // The manager worked no day at all, so their net floors at zero.
        UUID managerSlip = payslipIdOf(runDetail(hrToken, runId), manager.id());
        byte[] bytes = payslipPdf(hrToken, managerSlip).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Warning");
            assertThat(text).contains("floored");
        }
    }

    // ------------------------------------------------------------------ XLSX

    @Test
    void theRunExportHasAHeaderOneRowPerPayslipAndASumTotalsRow() throws Exception {
        byte[] bytes = getAs(hrToken, "/api/payroll/runs/" + runId + "/export.xlsx")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // A real OOXML package starts with the ZIP magic.
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Payroll 2025-03");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Employee no");
            assertThat(sheet.getRow(0).getCell(21).getStringCellValue()).isEqualTo("Net pay");

            JsonNode detail = runDetail(hrToken, runId);
            int payslips = detail.path("payslips").size();
            assertThat(payslips).isEqualTo(3); // HR, manager, staff
            assertThat(sheet.getLastRowNum()).isEqualTo(payslips + 1); // header + rows + totals

            Row staffRow = null;
            for (int i = 1; i <= payslips; i++) {
                if ("Sara Staff".equals(sheet.getRow(i).getCell(1).getStringCellValue())) {
                    staffRow = sheet.getRow(i);
                }
            }
            assertThat(staffRow).isNotNull();
            assertThat(staffRow.getCell(3).getNumericCellValue()).isEqualTo(8800.00);
            assertThat((int) staffRow.getCell(4).getNumericCellValue()).isEqualTo(22);
            assertThat((int) staffRow.getCell(6).getNumericCellValue()).isEqualTo(1);  // unpaid leave
            assertThat((int) staffRow.getCell(7).getNumericCellValue()).isEqualTo(1);  // absences
            assertThat((int) staffRow.getCell(8).getNumericCellValue()).isEqualTo(60); // late minutes
            assertThat(staffRow.getCell(12).getNumericCellValue()).isEqualTo(225.00);
            assertThat(staffRow.getCell(13).getNumericCellValue()).isEqualTo(400.00);
            assertThat(staffRow.getCell(16).getNumericCellValue()).isEqualTo(8625.00);
            assertThat(staffRow.getCell(18).getNumericCellValue()).isEqualTo(968.00);
            assertThat(staffRow.getCell(20).getNumericCellValue()).isEqualTo(731.40);
            assertThat(staffRow.getCell(21).getNumericCellValue()).isEqualTo(6925.60);

            // The totals row is a real formula and evaluates to the run total.
            Row totals = sheet.getRow(payslips + 1);
            assertThat(totals.getCell(0).getStringCellValue()).contains("TOTAL");
            Cell netTotal = totals.getCell(21);
            assertThat(netTotal.getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(netTotal.getCellFormula()).isEqualTo("SUM(V2:V4)");

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            BigDecimal evaluated = BigDecimal.valueOf(evaluator.evaluate(netTotal).getNumberValue())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            assertThat(evaluated)
                    .isEqualByComparingTo(detail.path("totals").path("netPay").decimalValue());
        }
    }

    @Test
    void theRunExportIsRestrictedToHrAndAdmin() throws Exception {
        getAs(staffToken, "/api/payroll/runs/" + runId + "/export.xlsx")
                .andExpect(status().isForbidden());
        getAs(login(manager.email(), manager.password()),
                "/api/payroll/runs/" + runId + "/export.xlsx").andExpect(status().isForbidden());
        getAs(adminToken, "/api/payroll/runs/" + runId + "/export.xlsx").andExpect(status().isOk());
    }

    @Test
    void exportingAnUnknownRunIsANotFound() throws Exception {
        getAs(hrToken, "/api/payroll/runs/" + UUID.randomUUID() + "/export.xlsx")
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmptyRunStillProducesAReadableWorkbook() throws Exception {
        // A month before anybody was hired has no eligible employee at all.
        JsonNode empty = createRunOk(hrToken, 2023, 1);
        assertThat(empty.path("payslips")).isEmpty();

        byte[] bytes = getAs(hrToken, "/api/payroll/runs/" + runIdOf(empty) + "/export.xlsx")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("0 employees");
        }
    }
}
