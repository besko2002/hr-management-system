package com.example.hr.leave;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The monthly accrual job. Both the cron expression and the job itself are configuration:
 *
 * <pre>
 * app.leave.accrual.enabled: false            # off by default, so tests and the upfront-grant policy are unaffected
 * app.leave.accrual.cron:    "0 30 1 1 * *"   # 01:30 on the first day of every month
 * app.leave.accrual.zone:    UTC
 * </pre>
 *
 * The bean (and with it {@code @EnableScheduling}) only exists when the flag is true, so
 * nothing is scheduled in the default configuration. Running the job is idempotent per
 * (employee, year, month), so a double fire credits nothing twice — see
 * {@link LeaveAccrualService}.
 *
 * <p>Monthly accrual and upfront granting are alternatives, so enabling this job while
 * {@code app.leave.annual.upfront} is still true is refused at <em>startup</em>: the
 * application does not come up at all, rather than silently double-granting on the first
 * fire.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.leave.accrual", name = "enabled", havingValue = "true")
@EnableScheduling
class LeaveAccrualScheduler {

    private final LeaveAccrualService accrual;

    LeaveAccrualScheduler(LeaveAccrualService accrual,
                          @Value("${app.leave.annual.upfront:true}") boolean annualUpfront) {
        if (annualUpfront) {
            String message = "Invalid leave configuration: app.leave.accrual.enabled=true requires "
                    + "app.leave.annual.upfront=false — upfront granting and monthly accrual are "
                    + "alternatives, enabling both would grant more than the yearly allowance";
            log.error(message);
            throw new IllegalStateException(message);
        }
        this.accrual = accrual;
    }

    @Scheduled(cron = "${app.leave.accrual.cron:0 30 1 1 * *}", zone = "${app.leave.accrual.zone:UTC}")
    void accrueCurrentMonth() {
        log.info("Scheduled leave accrual starting");
        accrual.runForCurrentMonth();
    }
}
