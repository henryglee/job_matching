package com.jobmatching.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleNameRequest(@NotBlank @Size(max = 200, message = "Role name must be at most 200 characters") String roleName) {
}
