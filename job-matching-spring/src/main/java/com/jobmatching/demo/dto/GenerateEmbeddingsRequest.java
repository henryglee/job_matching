package com.jobmatching.demo.dto;

public record GenerateEmbeddingsRequest(Integer limit) {
    public int limitOrDefault() {
        // Each job is a paid OpenAI call, so bound what one request can spend.
        return limit == null ? 50 : Math.max(1, Math.min(limit, 500));
    }
}
