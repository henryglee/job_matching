package com.jobmatching.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 500, message = "Invalid credentials") String login,
        // Far above any real password, but bounds the hashing work an unauthenticated caller can cause.
        @NotBlank @Size(max = 1024, message = "Invalid credentials") String password,
        String role
) {
}
