package com.jobmatching.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 128, message = "Password must be 8 to 128 characters") String password,
        @NotBlank @Size(max = 128, message = "Password must be 8 to 128 characters") String confirmPassword
) {
}
