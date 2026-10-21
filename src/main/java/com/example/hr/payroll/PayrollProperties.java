package com.example.hr.payroll;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Binding target for {@code app.payroll.*}. Mutable only because that is what relaxed
 * binding needs; it is converted once into the immutable {@link PayrollRates} that the
 * calculator and the payslip snapshots use.
 *
 * <p><b>Every default is illustrative and simplified. It is not tax or legal advice.</b>
 */
@ConfigurationProperties(prefix = "app.payroll")
public class PayrollProperties {

    private int workingHoursPerDay = 8;
    private BigDecimal overtimeMultiplier = new BigDecimal("1.5");
    private BigDecimal holidayOvertimeMultiplier = new BigDecimal("2.0");
    private Insurance insurance = new Insurance();
    private Tax tax = new Tax();
    private Company company = new Company();

    public PayrollRates toRates() {
        List<TaxBracket> brackets = new ArrayList<>();
        for (Bracket bracket : tax.getBrackets()) {
            brackets.add(new TaxBracket(bracket.getUpTo(), bracket.getRate()));
        }
        return new PayrollRates(workingHoursPerDay, overtimeMultiplier, holidayOvertimeMultiplier,
                insurance.getEmployeeRate(), insurance.getMinInsurable(), insurance.getMaxInsurable(),
                tax.getPersonalExemptionMonthly(), brackets);
    }

    public static class Insurance {
        private BigDecimal employeeRate = new BigDecimal("0.11");
        private BigDecimal minInsurable = new BigDecimal("2000.00");
        private BigDecimal maxInsurable = new BigDecimal("12600.00");

        public BigDecimal getEmployeeRate() {
            return employeeRate;
        }

        public void setEmployeeRate(BigDecimal employeeRate) {
            this.employeeRate = employeeRate;
        }

        public BigDecimal getMinInsurable() {
            return minInsurable;
        }

        public void setMinInsurable(BigDecimal minInsurable) {
            this.minInsurable = minInsurable;
        }

        public BigDecimal getMaxInsurable() {
            return maxInsurable;
        }

        public void setMaxInsurable(BigDecimal maxInsurable) {
            this.maxInsurable = maxInsurable;
        }
    }

    public static class Tax {
        private BigDecimal personalExemptionMonthly = new BigDecimal("1250.00");
        private List<Bracket> brackets = defaultBrackets();

        private static List<Bracket> defaultBrackets() {
            List<Bracket> brackets = new ArrayList<>();
            brackets.add(new Bracket(new BigDecimal("1500"), new BigDecimal("0.00")));
            brackets.add(new Bracket(new BigDecimal("3000"), new BigDecimal("0.10")));
            brackets.add(new Bracket(new BigDecimal("5000"), new BigDecimal("0.15")));
            brackets.add(new Bracket(new BigDecimal("8000"), new BigDecimal("0.20")));
            brackets.add(new Bracket(new BigDecimal("12000"), new BigDecimal("0.225")));
            brackets.add(new Bracket(null, new BigDecimal("0.25")));
            return brackets;
        }

        public BigDecimal getPersonalExemptionMonthly() {
            return personalExemptionMonthly;
        }

        public void setPersonalExemptionMonthly(BigDecimal personalExemptionMonthly) {
            this.personalExemptionMonthly = personalExemptionMonthly;
        }

        public List<Bracket> getBrackets() {
            return brackets;
        }

        public void setBrackets(List<Bracket> brackets) {
            this.brackets = brackets;
        }
    }

    /** {@code up-to} omitted means the open-ended top bracket. */
    public static class Bracket {
        private BigDecimal upTo;
        private BigDecimal rate;

        public Bracket() {
        }

        Bracket(BigDecimal upTo, BigDecimal rate) {
            this.upTo = upTo;
            this.rate = rate;
        }

        public BigDecimal getUpTo() {
            return upTo;
        }

        public void setUpTo(BigDecimal upTo) {
            this.upTo = upTo;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }
    }

    /** Printed on the payslip PDF header. */
    public static class Company {
        private String name = "Example Holding";
        private String address = "12 Nile Corniche, Cairo, Egypt";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

    public int getWorkingHoursPerDay() {
        return workingHoursPerDay;
    }

    public void setWorkingHoursPerDay(int workingHoursPerDay) {
        this.workingHoursPerDay = workingHoursPerDay;
    }

    public BigDecimal getOvertimeMultiplier() {
        return overtimeMultiplier;
    }

    public void setOvertimeMultiplier(BigDecimal overtimeMultiplier) {
        this.overtimeMultiplier = overtimeMultiplier;
    }

    public BigDecimal getHolidayOvertimeMultiplier() {
        return holidayOvertimeMultiplier;
    }

    public void setHolidayOvertimeMultiplier(BigDecimal holidayOvertimeMultiplier) {
        this.holidayOvertimeMultiplier = holidayOvertimeMultiplier;
    }

    public Insurance getInsurance() {
        return insurance;
    }

    public void setInsurance(Insurance insurance) {
        this.insurance = insurance;
    }

    public Tax getTax() {
        return tax;
    }

    public void setTax(Tax tax) {
        this.tax = tax;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }
}
