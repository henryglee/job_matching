package com.jobmatching.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// One turn of the "Ask about your matches" chat. The React panel holds the conversation and
// sends it with every message; nothing is stored server-side. Assistant turns can include the
// text of a draft the model proposed, so the model can revise it on the next message.
public record ChatMessage(
        @NotBlank(message = "Each message needs a role.")
        @Pattern(regexp = "user|assistant", message = "Message role must be 'user' or 'assistant'.")
        String role,
        @NotBlank(message = "Messages can't be empty.")
        @Size(max = 8000, message = "Each message must be at most 8000 characters.")
        String content
) {
}
