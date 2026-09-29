package com.jobmatching.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Max lengths match user_accounts.first_name / last_name (VARCHAR(200)).
public record UpdateNameRequest(
        @NotBlank(message = "First name and last name are required.")
        @Size(max = 200, message = "First name must be at most 200 characters.")
        String firstName,
        @NotBlank(message = "First name and last name are required.")
        @Size(max = 200, message = "Last name must be at most 200 characters.")
        String lastName
) {
}
