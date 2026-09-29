package com.jobmatching.demo.dto;

// current_count: how many matches the client is showing (before the dismissal), so the
// server knows which position the replacement fills. Same default as app.py.
public record DismissJobRequest(Integer currentCount) {
    public int currentCountOrDefault() {
        return currentCount == null ? 10 : currentCount;
    }
}
