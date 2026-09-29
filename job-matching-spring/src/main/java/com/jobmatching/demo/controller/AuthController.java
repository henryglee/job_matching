package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.CheckRolesRequest;
import com.jobmatching.demo.dto.ForgotPasswordRequest;
import com.jobmatching.demo.dto.LoginRequest;
import com.jobmatching.demo.dto.RegisterRequest;
import com.jobmatching.demo.dto.ResetPasswordRequest;
import com.jobmatching.demo.dto.VerifyEmailRequest;
import com.jobmatching.demo.entity.Role;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.service.AuthRateLimiter;
import com.jobmatching.demo.service.AuthService;
import com.jobmatching.demo.service.EmailService;
import com.jobmatching.demo.service.PasswordResetService;
import com.jobmatching.demo.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ports app.py's /register, /verify-email, /login, /api/auth/check-roles, /forgot-password and
 * /reset-password/&lt;token&gt; routes from Flask sessions onto JWTs (see
 * API_DOCUMENTATION.md's Authentication Model / Spring Boot notes on each route).
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final AuthRateLimiter rateLimiter;
    private final String resetPasswordBaseUrl;

    public AuthController(AuthService authService,
                           RegistrationService registrationService,
                           PasswordResetService passwordResetService,
                           EmailService emailService,
                           AuthRateLimiter rateLimiter,
                           @Value("${app.reset-password-url}") String resetPasswordBaseUrl) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.resetPasswordBaseUrl = resetPasswordBaseUrl;
    }

    // Step 1 of sign-up: nothing is created yet and no token is issued — the account only
    // exists once the emailed code is confirmed via POST /verify-email.
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request,
                                                        HttpServletRequest http) {
        throwIfLimited(rateLimiter.registerRetryAfter(http.getRemoteAddr(), request.email()));
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        String code = registrationService.startRegistration(
                request.email(), request.username(), request.password(), request.firstName(), request.lastName());
        // app.py only logs a send failure and still shows the verify page, leaving the user
        // waiting for a code that will never arrive; surface it so they can retry instead.
        if (!emailService.sendVerificationCode(request.email(), code)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send the verification email. Please try again.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Verification code sent. It expires in 10 minutes.");
        body.put("email", request.email().toLowerCase());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    // Step 2 of sign-up: confirms the code, creates the account and logs the user in.
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                                                           HttpServletRequest http) {
        throwIfLimited(rateLimiter.verifyEmailRetryAfter(http.getRemoteAddr(), request.email()));
        AuthService.RegisterResult result = authService.verifyEmail(request.email(), request.code());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Registration successful");
        body.put("token", result.token());
        body.put("user", userSummary(result.user()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/api/auth/check-roles")
    public Map<String, Object> checkRoles(@Valid @RequestBody CheckRolesRequest request, HttpServletRequest http) {
        // Takes a password just like /login, so it shares its limits.
        throwIfLimited(rateLimiter.loginRetryAfter(http.getRemoteAddr(), request.login()));
        List<Role> roles = authService.checkRoles(request.login(), request.password());
        rateLimiter.loginSucceeded(request.login());
        return Map.of("roles", roles.stream().map(AuthController::roleSummary).toList());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        throwIfLimited(rateLimiter.loginRetryAfter(http.getRemoteAddr(), request.login()));
        AuthService.LoginResult result = authService.login(request.login(), request.password(), request.role());
        rateLimiter.loginSucceeded(request.login());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", result.token());
        body.put("active_role", result.activeRole());
        body.put("candidate_profile_id", result.user().getCandidateProfile() != null
                ? result.user().getCandidateProfile().getId()
                : null);
        body.put("user", userSummary(result.user()));
        return body;
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        // Stateless JWT: nothing to invalidate server-side, the client just discards the token.
        return Map.of("message", "Logged out");
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        throwIfLimited(rateLimiter.forgotPasswordRetryAfter(http.getRemoteAddr(), request.email()));
        passwordResetService.createResetToken(request.email())
                .ifPresent(token -> emailService.sendPasswordReset(request.email(), resetPasswordBaseUrl + "/" + token));
        // Same response regardless of whether the email exists, to avoid enumeration.
        return Map.of("message", "If that email is registered, a reset link has been sent.");
    }

    @PostMapping("/reset-password/{token}")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String token,
                                                               @Valid @RequestBody ResetPasswordRequest request,
                                                               HttpServletRequest http) {
        throwIfLimited(rateLimiter.resetPasswordRetryAfter(http.getRemoteAddr()));
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        boolean success = passwordResetService.resetPassword(token, request.password());
        if (!success) {
            throw new IllegalArgumentException("Invalid or expired token.");
        }
        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }

    private static void throwIfLimited(long retryAfterSeconds) {
        if (retryAfterSeconds > 0) {
            throw new TooManyAttemptsException(retryAfterSeconds);
        }
    }

    private static Map<String, Object> userSummary(UserAccount user) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", user.getId());
        summary.put("email", user.getEmail());
        summary.put("username", user.getUsername());
        summary.put("first_name", user.getFirstName());
        summary.put("last_name", user.getLastName());
        return summary;
    }

    private static Map<String, Object> roleSummary(Role role) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("role_id", role.getRoleId());
        summary.put("role_name", role.getRoleName());
        return summary;
    }
}
