package com.example.hr.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window throttle on failed logins, keyed by (normalized e-mail, client IP).
 * Per instance and lost on restart — enough to stop online password guessing on one node.
 */
@Component
public class LoginRateLimiter {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    LoginRateLimiter(@Value("${app.login-rate-limit.max-failures:5}") int maxFailures,
                     @Value("${app.login-rate-limit.window:PT15M}") Duration window) {
        this(maxFailures, window, Clock.systemUTC());
    }

    LoginRateLimiter(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** True when the key has used up its failures within the window. */
    public boolean isBlocked(String email, String ip) {
        Deque<Instant> recent = failures.get(key(email, ip));
        if (recent == null) {
            return false;
        }
        synchronized (recent) {
            prune(recent);
            return recent.size() >= maxFailures;
        }
    }

    public void recordFailure(String email, String ip) {
        Deque<Instant> recent = failures.computeIfAbsent(key(email, ip), k -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent);
            recent.addLast(clock.instant());
        }
        if (failures.size() > 10_000) {
            evictExpired();
        }
    }

    public void reset(String email, String ip) {
        failures.remove(key(email, ip));
    }

    /** Clears every counter (tests). */
    public void clear() {
        failures.clear();
    }

    private void evictExpired() {
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue());
                return e.getValue().isEmpty();
            }
        });
    }

    private void prune(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(window);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.pollFirst();
        }
    }

    private static String key(String email, String ip) {
        return email + "|" + ip;
    }
}
