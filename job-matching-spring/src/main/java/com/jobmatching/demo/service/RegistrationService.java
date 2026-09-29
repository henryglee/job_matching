package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.PendingRegistration;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.repository.PendingRegistrationRepository;
import com.jobmatching.demo.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Ports app.py's two-step sign-up (/register then /verify-email): the account is only
 * created once the 6-digit code emailed at registration is confirmed. Same 10-minute
 * code lifetime and same user-facing messages as the Flask version.
 */
@Service
public class RegistrationService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    // Not in app.py (which allows unlimited guesses) — a 6-digit code is only as strong as
    // the number of tries allowed against it.
    static final int MAX_ATTEMPTS = 5;

    // Role given to every account on successful verification.
    public static final String DEFAULT_ROLE = "User";

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PendingRegistrationRepository pendingRepository;
    private final UserAccountService userAccountService;
    private final RoleRepository roleRepository;

    public RegistrationService(PendingRegistrationRepository pendingRepository,
                               UserAccountService userAccountService,
                               RoleRepository roleRepository) {
        this.pendingRepository = pendingRepository;
        this.userAccountService = userAccountService;
        this.roleRepository = roleRepository;
    }

    public enum Status {
        VERIFIED,
        NO_PENDING,
        EXPIRED,
        INCORRECT_CODE,
        TOO_MANY_ATTEMPTS,
        EMAIL_TAKEN,
        USERNAME_TAKEN
    }

    public record VerificationResult(Status status, UserAccount user) {
    }

    /**
     * Validates the sign-up, replaces any earlier pending sign-up for the same email, and
     * returns the verification code for the caller to email.
     */
    @Transactional
    public String startRegistration(String email, String username, String rawPassword, String firstName, String lastName) {
        if (rawPassword.contains(" ")) {
            throw new IllegalArgumentException("Password cannot contain spaces.");
        }
        if (userAccountService.emailExists(email)) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }
        if (userAccountService.usernameExists(username)) {
            throw new IllegalArgumentException("That username is already taken.");
        }

        LocalDateTime now = now();
        pendingRepository.deleteExpired(now);
        pendingRepository.deleteByEmailIgnoreCase(email);

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        PendingRegistration pending = new PendingRegistration();
        pending.setEmail(email.toLowerCase());
        pending.setUsername(username);
        pending.setFirstName(firstName);
        pending.setLastName(lastName);
        pending.setPasswordHash(userAccountService.hashPassword(rawPassword));
        pending.setCode(code);
        pending.setAttempts(0);
        pending.setExpiresAt(now.plus(CODE_TTL));
        pendingRepository.save(pending);
        return code;
    }

    // Returns a status rather than throwing on a wrong code, so the attempt-counter
    // increment commits instead of being rolled back with the exception.
    @Transactional
    public VerificationResult verify(String email, String code) {
        PendingRegistration pending = pendingRepository.findByEmailIgnoreCase(email).orElse(null);
        if (pending == null) {
            return new VerificationResult(Status.NO_PENDING, null);
        }
        if (pending.getExpiresAt().isBefore(now())) {
            pendingRepository.delete(pending);
            return new VerificationResult(Status.EXPIRED, null);
        }
        if (!codesMatch(pending.getCode(), code.strip())) {
            int attempts = pending.getAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                pendingRepository.delete(pending);
                return new VerificationResult(Status.TOO_MANY_ATTEMPTS, null);
            }
            pending.setAttempts(attempts);
            return new VerificationResult(Status.INCORRECT_CODE, null);
        }

        // Re-check: someone else may have claimed the email/username while this one was pending.
        if (userAccountService.emailExists(pending.getEmail())) {
            pendingRepository.delete(pending);
            return new VerificationResult(Status.EMAIL_TAKEN, null);
        }
        if (userAccountService.usernameExists(pending.getUsername())) {
            pendingRepository.delete(pending);
            return new VerificationResult(Status.USERNAME_TAKEN, null);
        }

        UserAccount user = userAccountService.createUserWithPasswordHash(
                pending.getEmail(), pending.getUsername(), pending.getPasswordHash(),
                pending.getFirstName(), pending.getLastName());
        // Not in app.py, which leaves new accounts role-less and treats "no role" as a
        // regular user. Here every User-facing route and check keys off the User role, so
        // a role-less account couldn't even reach profile setup until an admin stepped in.
        roleRepository.findByRoleName(DEFAULT_ROLE).ifPresentOrElse(
                user.getRoles()::add,
                () -> log.warn("Role '{}' not found; new user {} was created without a role", DEFAULT_ROLE, user.getUsername()));
        pendingRepository.delete(pending);
        return new VerificationResult(Status.VERIFIED, user);
    }

    private static boolean codesMatch(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
