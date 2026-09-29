package com.jobmatching.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// The conversation so far, oldest first, ending with the candidate's new message. Capped so a
// long chat can't turn into an expensive prompt; the panel sends only the most recent turns.
public record ChatRequest(
        @NotEmpty(message = "messages is required.")
        @Size(max = 20, message = "Send at most the 20 most recent messages.")
        List<@Valid ChatMessage> messages
) {
}
