package com.jobmatching.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank @Email @Size(max = 500, message = "Email is too long") String email,
        @NotBlank(message = "Verification code is required") @Size(max = 20, message = "Verification code is too long") String code
) {
}
