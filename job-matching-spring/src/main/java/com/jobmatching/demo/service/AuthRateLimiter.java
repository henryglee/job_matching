package com.jobmatching.demo.service;

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
 * Throttles the unauthenticated sign-in and sign-up endpoints, to slow password guessing and
 * to stop /register and /forgot-password being used to mail a code or link to someone over and over. Sliding
 * windows keyed by client address and by the account being targeted. In memory, so it resets
 * on restart and isn't shared between instances (same trade-off as {@link HourlyLimiter}).
 */
@Component
public class AuthRateLimiter {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final int PRUNE_THRESHOLD = 10_000;

    private final int loginPerIp;
    private final int loginPerAccount;
    private final int registerPerIp;
    private final int registerPerEmail;
    private final int verifyPerIp;
    private final int verifyPerEmail;
    private final int resetPerIp;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimiter(@Value("${app.rate-limit.login-per-ip:30}") int loginPerIp,
                           @Value("${app.rate-limit.login-per-account:10}") int loginPerAccount,
                           @Value("${app.rate-limit.register-per-ip:10}") int registerPerIp,
                           @Value("${app.rate-limit.register-per-email:3}") int registerPerEmail,
                           @Value("${app.rate-limit.verify-per-ip:30}") int verifyPerIp,
                           @Value("${app.rate-limit.verify-per-email:10}") int verifyPerEmail,
                           @Value("${app.rate-limit.reset-per-ip:20}") int resetPerIp) {
        this(loginPerIp, loginPerAccount, registerPerIp, registerPerEmail, verifyPerIp, verifyPerEmail, resetPerIp,
                Clock.systemUTC());
    }

    AuthRateLimiter(int loginPerIp, int loginPerAccount, int registerPerIp, int registerPerEmail,
                    int verifyPerIp, int verifyPerEmail, int resetPerIp, Clock clock) {
        this.loginPerIp = loginPerIp;
        this.loginPerAccount = loginPerAccount;
        this.registerPerIp = registerPerIp;
        this.registerPerEmail = registerPerEmail;
        this.verifyPerIp = verifyPerIp;
        this.verifyPerEmail = verifyPerEmail;
        this.resetPerIp = resetPerIp;
        this.clock = clock;
    }

    // The three checks below record the attempt and return 0 if it may go ahead, otherwise the
    // seconds until it could (the longer wait if both the address and the account are over).
    public long loginRetryAfter(String clientIp, String login) {
        return Math.max(acquire("login-ip:" + clientIp, loginPerIp, LOGIN_WINDOW),
                acquire(loginAccountKey(login), loginPerAccount, LOGIN_WINDOW));
    }

    // A successful login clears the account's failures, so a legitimate user isn't locked
    // out by their own earlier typos.
    public void loginSucceeded(String login) {
        attempts.remove(loginAccountKey(login));
    }

    public long registerRetryAfter(String clientIp, String email) {
        return Math.max(acquire("register-ip:" + clientIp, registerPerIp, REGISTER_WINDOW),
                acquire("register-email:" + email.strip().toLowerCase(), registerPerEmail, REGISTER_WINDOW));
    }

    // RegistrationService already caps guesses at 5 per emailed code; this stops one address
    // from working through many pending sign-ups, and bounds a code that keeps being reissued.
    public long verifyEmailRetryAfter(String clientIp, String email) {
        return Math.max(acquire("verify-ip:" + clientIp, verifyPerIp, REGISTER_WINDOW),
                acquire("verify-email:" + email.strip().toLowerCase(), verifyPerEmail, REGISTER_WINDOW));
    }

    // Per address only: there's no account to key on before the token is checked, and keying on
    // the token would let a guesser rotate through fresh ones.
    public long resetPasswordRetryAfter(String clientIp) {
        return acquire("reset-ip:" + clientIp, resetPerIp, REGISTER_WINDOW);
    }

    // Keyed on the address typed in, whether or not it's registered, so hitting the limit
    // says nothing about which emails have accounts.
    public long forgotPasswordRetryAfter(String clientIp, String email) {
        return Math.max(acquire("forgot-ip:" + clientIp, registerPerIp, REGISTER_WINDOW),
                acquire("forgot-email:" + email.strip().toLowerCase(), registerPerEmail, REGISTER_WINDOW));
    }

    private static String loginAccountKey(String login) {
        return "login-account:" + login.strip().toLowerCase();
    }

    private long acquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        if (attempts.size() > PRUNE_THRESHOLD) {
            attempts.values().removeIf(d -> {
                synchronized (d) {
                    return d.isEmpty() || d.peekLast().isBefore(now.minus(REGISTER_WINDOW));
                }
            });
        }
        Deque<Instant> deque = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                // Free again once the oldest attempt in the window ages out.
                long wait = Duration.between(now, deque.peekFirst().plus(window)).toMillis();
                return Math.max(1, (wait + 999) / 1000);
            }
            deque.addLast(now);
            return 0;
        }
    }
}
