package com.example.hr.leave;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The startup guard of the accrual scheduler. Scheduling the monthly accrual while the
 * ANNUAL entitlement is also granted upfront would double-grant on the very first fire, so
 * the combination must not be deployable: the context fails to start.
 */
class LeaveAccrualSchedulerGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withBean(LeaveAccrualService.class, () -> Mockito.mock(LeaveAccrualService.class))
            .withUserConfiguration(LeaveAccrualScheduler.class);

    @Test
    void enablingTheAccrualWhileTheEntitlementIsGrantedUpfrontFailsAtStartup() {
        contextRunner
                .withPropertyValues("app.leave.accrual.enabled=true", "app.leave.annual.upfront=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause()
                        .hasMessageContaining("app.leave.accrual.enabled=true requires "
                                + "app.leave.annual.upfront=false"));
    }

    @Test
    void theDefaultedUpfrontFlagIsAlsoRefused() {
        // upfront is not set at all, so it defaults to true — still a misconfiguration.
        contextRunner
                .withPropertyValues("app.leave.accrual.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enablingTheAccrualInAccrualModeStartsTheScheduler() {
        contextRunner
                .withPropertyValues("app.leave.accrual.enabled=true", "app.leave.annual.upfront=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(LeaveAccrualScheduler.class));
    }

    @Test
    void withoutTheFlagNothingIsScheduledAtAll() {
        contextRunner
                .withPropertyValues("app.leave.annual.upfront=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(LeaveAccrualScheduler.class));
    }

    @Test
    void theGuardIsInTheConstructorItself() {
        LeaveAccrualService accrual = Mockito.mock(LeaveAccrualService.class);
        assertThatThrownBy(() -> new LeaveAccrualScheduler(accrual, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upfront granting and monthly accrual are alternatives");
        assertThat(new LeaveAccrualScheduler(accrual, false)).isNotNull();
    }
}
