package com.jobmatching.demo.service;

// Things AiUsageMonitor counts per candidate. Never the text of a message or draft.
public enum AiUsageEvent {
    CHAT_MESSAGE,           // a chat answer from the model (with its tokens)
    CHAT_CUT_OFF,           // ...that hit the output cap
    CHAT_FAILED,            // model call failed or returned nothing usable
    CHAT_LIMITED,           // refused by the hourly message limit
    DRAFT_GENERATED,        // "Generate draft" (with its tokens)
    DRAFT_FAILED,
    DRAFT_LIMITED,
    WHAT_IF,                // a what-if score (one embedding call)
    WHAT_IF_FAILED,
    WHAT_IF_LIMITED,
    MODERATION_BLOCKED,     // input flagged by moderation; never reached the chat model
    MODERATION_UNAVAILABLE  // moderation check failed, input let through
}
