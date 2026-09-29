package com.jobmatching.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320, message = "Email must be at most 320 characters") String email,
        @NotBlank @Size(max = 200, message = "Username must be at most 200 characters")
        // Login accepts an email or a username, so a username that looks like an email could collide
        // with (and lock out) the account that owns that email.
        @Pattern(regexp = "[^@]*", message = "Username cannot contain @") String username,
        @NotBlank @Size(max = 200, message = "First name must be at most 200 characters") String firstName,
        @NotBlank @Size(max = 200, message = "Last name must be at most 200 characters") String lastName,
        @NotBlank @Size(min = 8, max = 128, message = "Password must be 8 to 128 characters") String password,
        @NotBlank @Size(max = 128, message = "Password must be 8 to 128 characters") String confirmPassword
) {
}
