package com.jobmatching.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Saving a draft the candidate already has in hand, e.g. one the chat proposed. The recipient,
// if any, must still be one of the job's contact emails (EmailDraftService.createDraft), the
// same rule as MCP; the candidate can change it afterwards on the Email Drafts page.
public record CreateDraftRequest(
        @NotNull(message = "job_id is required.")
        UUID jobId,
        @Email(message = "Recipient must be a valid email address.")
        @Size(max = 320, message = "Recipient must be at most 320 characters.")
        String recipient,
        @NotBlank(message = "Subject is required.")
        @Size(max = 300, message = "Subject must be at most 300 characters.")
        String subject,
        @NotBlank(message = "Body is required.")
        @Size(max = 20000, message = "Body must be at most 20000 characters.")
        String body
) {
}
