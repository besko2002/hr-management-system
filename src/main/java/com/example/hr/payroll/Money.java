package com.example.hr.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The money convention of the whole payroll module, in one place: {@link BigDecimal},
 * scale 2, {@link RoundingMode#HALF_UP}. There is no {@code double} or {@code float}
 * anywhere in this package — not in a field, not in an intermediate, not in a DTO.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = scaled(BigDecimal.ZERO);

    private Money() {
    }

    /** Rounds to the money scale; the single rounding entry point of the module. */
    public static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String value) {
        return scaled(new BigDecimal(value));
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return scaled(a.multiply(b));
    }

    /** {@code a / b} at the money scale; {@code b == 0} yields zero rather than an error. */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return ZERO;
        }
        return a.divide(b, SCALE, ROUNDING);
    }

    public static BigDecimal divide(BigDecimal a, int b) {
        return divide(a, BigDecimal.valueOf(b));
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** Never negative: the floor the net pay and the taxable income both use. */
    public static BigDecimal atLeastZero(BigDecimal value) {
        return value.signum() < 0 ? ZERO : scaled(value);
    }

    public static BigDecimal clamp(BigDecimal value, BigDecimal low, BigDecimal high) {
        return min(max(value, low), high);
    }

    /** Minutes to hours at the money scale, e.g. 90 → 1.50, 100 → 1.67. */
    public static BigDecimal hoursOf(int minutes) {
        return divide(BigDecimal.valueOf(minutes), 60);
    }
}
