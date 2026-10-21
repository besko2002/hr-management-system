package com.example.hr.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };

    @Test
    void blocksAfterTheLimitAndUnblocksWhenTheWindowPasses() {
        LoginRateLimiter limiter = new LoginRateLimiter(5, Duration.ofMinutes(15), clock);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("a@x", "1.1.1.1");
        }
        assertThat(limiter.isBlocked("a@x", "1.1.1.1")).isFalse();
        limiter.recordFailure("a@x", "1.1.1.1");
        assertThat(limiter.isBlocked("a@x", "1.1.1.1")).isTrue();
        assertThat(limiter.isBlocked("a@x", "2.2.2.2")).isFalse();

        now = now.plus(Duration.ofMinutes(15)).plusSeconds(1);
        assertThat(limiter.isBlocked("a@x", "1.1.1.1")).isFalse();
    }

    @Test
    void resetClearsTheKey() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMinutes(15), clock);
        limiter.recordFailure("a@x", "1.1.1.1");
        assertThat(limiter.isBlocked("a@x", "1.1.1.1")).isTrue();
        limiter.reset("a@x", "1.1.1.1");
        assertThat(limiter.isBlocked("a@x", "1.1.1.1")).isFalse();
    }
}
