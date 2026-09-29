package com.jobmatching.demo.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps paid AI calls per candidate over a sliding hour. Subclasses are the separate
 * allowances (draft generation, chat messages). In memory, so it resets on restart and isn't
 * shared between instances, which is enough to stop an accidental loop or a stuck button,
 * not a determined abuser.
 */
public abstract class HourlyLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int perHour;
    private final Clock clock;
    private final Map<UUID, Deque<Instant>> recent = new ConcurrentHashMap<>();

    protected HourlyLimiter(int perHour, Clock clock) {
        this.perHour = perHour;
        this.clock = clock;
    }

    // Records the attempt and returns true if the candidate is under the limit. Failed
    // attempts count too: they may still have cost a model call.
    public boolean tryAcquire(UUID candidateId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        Deque<Instant> attempts = recent.computeIfAbsent(candidateId, id -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= perHour) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }
}
