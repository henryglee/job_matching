package com.jobmatching.demo.dto;

import java.util.UUID;

// An application email the chat model proposed. Shown as a card with a Save button; nothing
// is saved until the candidate clicks it (POST /api/drafts). The recipient comes from the
// job's scraped contact emails, never from the model.
public record DraftProposal(
        UUID jobId,
        String jobTitle,
        String companyName,
        String recipient,
        String subject,
        String body
) {
}
