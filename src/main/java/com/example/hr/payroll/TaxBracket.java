package com.example.hr.payroll;

import java.math.BigDecimal;

/**
 * One monthly, <em>marginal</em> tax bracket: income above the previous bracket's ceiling
 * and up to {@code upTo} is taxed at {@code rate}.
 *
 * @param upTo the inclusive ceiling of this bracket, or {@code null} for the open-ended
 *             top bracket (there must be exactly one, and it must be last)
 * @param rate a fraction, e.g. {@code 0.225} for 22.5 %
 */
public record TaxBracket(BigDecimal upTo, BigDecimal rate) {

    public TaxBracket {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A tax rate must be between 0 and 1, got " + rate);
        }
        if (upTo != null && upTo.signum() <= 0) {
            throw new IllegalArgumentException("A tax bracket ceiling must be positive, got " + upTo);
        }
    }

    public boolean isUnbounded() {
        return upTo == null;
    }
}
