package com.jobmatching.demo.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Passwords are bounded, so an unauthenticated caller can't make the server hash megabytes. */
class PasswordLengthTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static RegisterRequest register(String password) {
        return new RegisterRequest("a@example.com", "ada", "Ada", "Lovelace", password, password);
    }

    @Test
    void registrationAcceptsEightToOneTwentyEightCharacters() {
        assertTrue(validator.validate(register("x".repeat(8))).isEmpty());
        assertTrue(validator.validate(register("x".repeat(128))).isEmpty());
    }

    @Test
    void registrationRefusesTooShortOrTooLong() {
        assertEquals(1, validator.validate(register("x".repeat(7))).size());
        var tooLong = validator.validate(register("x".repeat(129)));
        // The password and its confirmation are both checked.
        assertEquals(2, tooLong.size());
        assertTrue(tooLong.stream().allMatch(v -> v.getMessage().equals("Password must be 8 to 128 characters")));
    }

    @Test
    void resetRefusesAnOversizedPassword() {
        assertTrue(validator.validate(new ResetPasswordRequest("x".repeat(128), "x".repeat(128))).isEmpty());
        assertEquals(2, validator.validate(new ResetPasswordRequest("x".repeat(129), "x".repeat(129))).size());
    }

    @Test
    void loginBoundsTheWorkButStillAcceptsLongLegacyPasswords() {
        assertTrue(validator.validate(new LoginRequest("ada", "x".repeat(1024), null)).isEmpty());
        assertEquals(1, validator.validate(new LoginRequest("ada", "x".repeat(1025), null)).size());
        assertEquals(1, validator.validate(new CheckRolesRequest("ada", "x".repeat(1025))).size());
    }
}
