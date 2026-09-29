package com.jobmatching.demo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// instructions: optional extra guidance from the candidate, e.g. "friendly tone, mention my
// capstone project". Capped to keep the prompt small.
public record GenerateDraftRequest(
        @NotNull(message = "job_id is required.")
        UUID jobId,
        @Size(max = 500, message = "Instructions must be at most 500 characters.")
        String instructions
) {
}
