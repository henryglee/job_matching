package com.jobmatching.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestEmailRequest(@NotBlank @Size(max = 500, message = "Email is too long") String email) {
}
