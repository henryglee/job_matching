package com.jobmatching.demo.dto;

public record DeleteOldJobsRequest(Integer daysOld) {
    public int daysOldOrDefault() {
        int days = daysOld == null ? 30 : daysOld;
        // 0 or a negative would put the cutoff at or after now and delete every listing.
        if (days < 1) {
            throw new IllegalArgumentException("days_old must be at least 1");
        }
        return days;
    }
}
