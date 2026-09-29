package com.jobmatching.demo.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-29T12:00:00Z"));
    // 5 logins per IP, 3 per account, 4 registrations per IP, 2 per email, 4 verifications per IP, 2 per email, 3 resets per IP
    private final AuthRateLimiter limiter = new AuthRateLimiter(5, 3, 4, 2, 4, 2, 3, clock);

    @Test
    void locksAnAccountAfterTooManyAttemptsFromAnyAddress() {
        assertEquals(0, limiter.loginRetryAfter("1.1.1.1", "ada"));
        assertEquals(0, limiter.loginRetryAfter("2.2.2.2", "ada"));
        assertEquals(0, limiter.loginRetryAfter("3.3.3.3", "Ada "), "same account, case and spacing ignored");
        assertTrue(limiter.loginRetryAfter("4.4.4.4", "ada") > 0);
        assertEquals(0, limiter.loginRetryAfter("4.4.4.4", "grace"), "other accounts are unaffected");
    }

    @Test
    void limitsAnAddressAcrossAccounts() {
        for (int i = 0; i < 5; i++) {
            assertEquals(0, limiter.loginRetryAfter("1.1.1.1", "user" + i));
        }
        assertTrue(limiter.loginRetryAfter("1.1.1.1", "another") > 0);
        assertEquals(0, limiter.loginRetryAfter("2.2.2.2", "another"));
    }

    @Test
    void successfulLoginClearsTheAccountsFailures() {
        limiter.loginRetryAfter("1.1.1.1", "ada");
        limiter.loginRetryAfter("1.1.1.1", "ada");
        limiter.loginSucceeded("ada");
        assertEquals(0, limiter.loginRetryAfter("1.1.1.1", "ada"));
        assertEquals(0, limiter.loginRetryAfter("1.1.1.1", "ada"));
        assertEquals(0, limiter.loginRetryAfter("1.1.1.1", "ada"));
    }

    @Test
    void attemptsAgeOutAfterTheWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.loginRetryAfter("1.1.1.1", "ada");
        }
        assertTrue(limiter.loginRetryAfter("1.1.1.1", "ada") > 0);
        clock.advance(Duration.ofMinutes(16));
        assertEquals(0, limiter.loginRetryAfter("1.1.1.1", "ada"));
    }

    @Test
    void limitsRegistrationsPerEmailAndPerAddress() {
        assertEquals(0, limiter.registerRetryAfter("1.1.1.1", "a@example.com"));
        assertEquals(0, limiter.registerRetryAfter("1.1.1.1", "A@example.com"));
        assertTrue(limiter.registerRetryAfter("2.2.2.2", "a@example.com") > 0, "third code to one inbox");
        assertEquals(0, limiter.registerRetryAfter("1.1.1.1", "b@example.com"));
        assertEquals(0, limiter.registerRetryAfter("1.1.1.1", "c@example.com"));
        assertTrue(limiter.registerRetryAfter("1.1.1.1", "d@example.com") > 0, "fifth from one address");
    }

    @Test
    void forgotPasswordHasItsOwnAllowancePerEmailAndAddress() {
        assertEquals(0, limiter.forgotPasswordRetryAfter("1.1.1.1", "a@example.com"));
        assertEquals(0, limiter.forgotPasswordRetryAfter("1.1.1.1", "A@example.com"));
        assertTrue(limiter.forgotPasswordRetryAfter("2.2.2.2", "a@example.com") > 0, "third link to one inbox");
        assertEquals(0, limiter.registerRetryAfter("2.2.2.2", "a@example.com"), "separate from register");
        assertEquals(0, limiter.forgotPasswordRetryAfter("1.1.1.1", "b@example.com"));
        assertEquals(0, limiter.forgotPasswordRetryAfter("1.1.1.1", "c@example.com"));
        assertTrue(limiter.forgotPasswordRetryAfter("1.1.1.1", "d@example.com") > 0, "fifth from one address");
    }

    @Test
    void limitsVerificationAttemptsPerEmailAndPerAddress() {
        assertEquals(0, limiter.verifyEmailRetryAfter("1.1.1.1", "a@example.com"));
        assertEquals(0, limiter.verifyEmailRetryAfter("1.1.1.1", "A@example.com"));
        assertTrue(limiter.verifyEmailRetryAfter("2.2.2.2", "a@example.com") > 0, "third try on one sign-up");
        assertEquals(0, limiter.verifyEmailRetryAfter("1.1.1.1", "b@example.com"));
        assertEquals(0, limiter.verifyEmailRetryAfter("1.1.1.1", "c@example.com"));
        assertTrue(limiter.verifyEmailRetryAfter("1.1.1.1", "d@example.com") > 0, "fifth from one address");
    }

    @Test
    void limitsPasswordResetAttemptsPerAddress() {
        for (int i = 0; i < 3; i++) {
            assertEquals(0, limiter.resetPasswordRetryAfter("1.1.1.1"));
        }
        assertTrue(limiter.resetPasswordRetryAfter("1.1.1.1") > 0);
        assertEquals(0, limiter.resetPasswordRetryAfter("2.2.2.2"));
    }

    @Test
    void reportsHowLongUntilTheOldestAttemptAgesOut() {
        limiter.loginRetryAfter("1.1.1.1", "ada");
        clock.advance(Duration.ofMinutes(5));
        limiter.loginRetryAfter("1.1.1.1", "ada");
        limiter.loginRetryAfter("1.1.1.1", "ada");

        // 3 per account per 15 min: free when the first attempt (5 min ago) is 15 min old.
        assertEquals(10 * 60, limiter.loginRetryAfter("1.1.1.1", "ada"));
        clock.advance(Duration.ofSeconds(90));
        assertEquals(10 * 60 - 90, limiter.loginRetryAfter("1.1.1.1", "ada"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
