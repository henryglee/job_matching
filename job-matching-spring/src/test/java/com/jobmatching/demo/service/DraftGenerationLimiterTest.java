package com.jobmatching.demo.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftGenerationLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-23T12:00:00Z"));
    private final DraftGenerationLimiter limiter = new DraftGenerationLimiter(3, clock);

    @Test
    void allowsUpToTheLimitPerCandidate() {
        UUID candidate = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(candidate));
        assertTrue(limiter.tryAcquire(candidate));
        assertTrue(limiter.tryAcquire(candidate));
        assertFalse(limiter.tryAcquire(candidate));

        assertTrue(limiter.tryAcquire(UUID.randomUUID()), "another candidate has their own allowance");
    }

    @Test
    void slotsFreeUpAnHourAfterEachAttempt() {
        UUID candidate = UUID.randomUUID();
        limiter.tryAcquire(candidate);
        clock.advance(Duration.ofMinutes(30));
        limiter.tryAcquire(candidate);
        limiter.tryAcquire(candidate);
        assertFalse(limiter.tryAcquire(candidate));

        clock.advance(Duration.ofMinutes(30));
        assertTrue(limiter.tryAcquire(candidate), "the first attempt is now an hour old");
        assertFalse(limiter.tryAcquire(candidate));
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
