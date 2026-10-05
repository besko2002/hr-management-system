package com.example.hr.payroll;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the payroll module. {@link PayrollRates} is validated at startup, so an
 * invalid bracket table or a negative rate stops the application instead of producing
 * wrong money later.
 */
@Configuration
@EnableConfigurationProperties(PayrollProperties.class)
class PayrollConfig {

    @Bean
    PayrollRates payrollRates(PayrollProperties properties) {
        return properties.toRates();
    }

    @Bean
    PayrollCalculator payrollCalculator() {
        return new PayrollCalculator();
    }
}
