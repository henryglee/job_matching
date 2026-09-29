package com.jobmatching.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Max lengths match email_drafts.subject (VARCHAR(300)) and recipient (VARCHAR(320)).
// A blank recipient clears it, for a draft that's a cover letter rather than an email.
public record UpdateEmailDraftRequest(
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
