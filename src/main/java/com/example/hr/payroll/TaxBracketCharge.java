package com.example.hr.payroll;

import java.math.BigDecimal;

/**
 * What one bracket actually contributed, so the tax line of a payslip can be re-added by
 * hand: {@code taxedAmount = to − from}, {@code tax = round(taxedAmount × rate)}.
 *
 * @param to {@code null} for the open-ended top bracket
 */
public record TaxBracketCharge(
        BigDecimal from,
        BigDecimal to,
        BigDecimal rate,
        BigDecimal taxedAmount,
        BigDecimal tax) {
}
