package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.PasswordResetToken;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.repository.PasswordResetTokenRepository;
import com.jobmatching.demo.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Ports app.py's forgot-password / reset-password routes: a real token table instead of
 * a signed URL (see PasswordResetToken), same 1-hour expiry as the Flask implementation.
 */
@Service
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                 UserAccountRepository userAccountRepository,
                                 PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Returns empty when the email doesn't exist. The controller must respond with the
    // same success message either way, to avoid leaking which emails are registered
    // (app.py does this deliberately — see the forgot-password endpoint notes).
    @Transactional
    public Optional<String> createResetToken(String email) {
        return userAccountRepository.findByEmailIgnoreCase(email).map(user -> {
            tokenRepository.deleteByUserId(user.getId());
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setToken(token);
            resetToken.setUserId(user.getId());
            resetToken.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plus(TOKEN_TTL));
            tokenRepository.save(resetToken);
            return token;
        });
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token).orElse(null);
        if (resetToken == null || resetToken.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            return false;
        }
        UserAccount user = userAccountRepository.findById(resetToken.getUserId()).orElse(null);
        if (user == null) {
            return false;
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tokenRepository.delete(resetToken);
        return true;
    }
}
