package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.Role;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Ports app.py's register/login/check-roles routes onto JWTs instead of Flask sessions.
 * Existing user-facing behavior (email/username uniqueness checks, role-membership
 * validation at login) is preserved; only the "you're now logged in" mechanism changes.
 */
@Service
public class AuthService {

    private final UserAccountService userAccountService;
    private final RegistrationService registrationService;
    private final JwtService jwtService;

    public AuthService(UserAccountService userAccountService, RegistrationService registrationService, JwtService jwtService) {
        this.userAccountService = userAccountService;
        this.registrationService = registrationService;
        this.jwtService = jwtService;
    }

    public record RegisterResult(UserAccount user, String token) {
    }

    public record LoginResult(UserAccount user, String activeRole, String token) {
    }

    // Mirrors POST /verify-email: the account only exists once the emailed code is confirmed
    // (see RegistrationService), at which point the user is logged straight in.
    public RegisterResult verifyEmail(String email, String code) {
        RegistrationService.VerificationResult result = registrationService.verify(email, code);
        switch (result.status()) {
            case VERIFIED -> {
                // Log straight in as the auto-assigned User role, so the new account can go
                // directly to profile setup. Falls back to no active role only if that role
                // is missing from the roles table (RegistrationService logs a warning).
                boolean hasDefaultRole = result.user().getRoles().stream()
                        .anyMatch(r -> RegistrationService.DEFAULT_ROLE.equals(r.getRoleName()));
                String token = jwtService.generateToken(result.user(), hasDefaultRole ? RegistrationService.DEFAULT_ROLE : null);
                return new RegisterResult(result.user(), token);
            }
            case NO_PENDING -> throw new IllegalArgumentException("No pending registration. Please register first.");
            case EXPIRED -> throw new IllegalArgumentException("Verification code expired. Please register again.");
            case INCORRECT_CODE -> throw new IllegalArgumentException("Incorrect code. Please try again.");
            case TOO_MANY_ATTEMPTS -> throw new IllegalArgumentException("Too many incorrect attempts. Please register again.");
            case EMAIL_TAKEN -> throw new IllegalArgumentException("An account with that email already exists.");
            case USERNAME_TAKEN -> throw new IllegalArgumentException("That username is already taken. Please register again.");
            default -> throw new IllegalStateException("Unhandled verification status: " + result.status());
        }
    }

    // Mirrors POST /api/auth/check-roles: verify credentials, return roles, no token issued.
    public List<Role> checkRoles(String login, String password) {
        UserAccount user = authenticate(login, password);
        return List.copyOf(user.getRoles());
    }

    public LoginResult login(String login, String password, String requestedRole) {
        UserAccount user = authenticate(login, password);

        String activeRole = null;
        if (requestedRole != null && !requestedRole.isBlank()) {
            boolean hasRole = user.getRoles().stream().anyMatch(r -> r.getRoleName().equals(requestedRole));
            if (!hasRole) {
                throw new IllegalArgumentException("Role not assigned to this user: " + requestedRole);
            }
            activeRole = requestedRole;
        }
        String token = jwtService.generateToken(user, activeRole);
        return new LoginResult(user, activeRole, token);
    }

    private UserAccount authenticate(String login, String password) {
        UserAccount user = userAccountService.getUserByLogin(login).orElse(null);
        if (user == null || !userAccountService.verifyPassword(user, password)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return user;
    }
}
