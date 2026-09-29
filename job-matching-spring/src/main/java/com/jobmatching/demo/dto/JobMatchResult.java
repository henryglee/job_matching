package com.jobmatching.demo.dto;

import com.jobmatching.demo.entity.JobListing;

public record JobMatchResult(
        JobListing job,
        double similarityScore,
        MatchBreakdown breakdown
) {
}
