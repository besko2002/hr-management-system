package com.example.hr.payroll;

import java.math.BigDecimal;
import java.util.List;

/** The shipped illustrative rates, built in code so the unit tests need no Spring context. */
final class PayrollRatesFixture {

    private PayrollRatesFixture() {
    }

    static PayrollRates defaults() {
        return new PayrollRates(
                8,
                new BigDecimal("1.5"),
                new BigDecimal("2.0"),
                new BigDecimal("0.11"),
                new BigDecimal("2000.00"),
                new BigDecimal("12600.00"),
                new BigDecimal("1250.00"),
                List.of(
                        new TaxBracket(new BigDecimal("1500.00"), new BigDecimal("0.000")),
                        new TaxBracket(new BigDecimal("3000.00"), new BigDecimal("0.100")),
                        new TaxBracket(new BigDecimal("5000.00"), new BigDecimal("0.150")),
                        new TaxBracket(new BigDecimal("8000.00"), new BigDecimal("0.200")),
                        new TaxBracket(new BigDecimal("12000.00"), new BigDecimal("0.225")),
                        new TaxBracket(null, new BigDecimal("0.250"))));
    }
}
