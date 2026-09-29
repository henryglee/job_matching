package com.jobmatching.demo.dto;

import java.util.List;
import java.util.UUID;

// A compact match for an AI model (MCP tools and the in-app chat), so it can find a job id
// without the full posting.
public record MatchSummary(UUID jobId, String title, String companyName, String location,
                           double matchPercent, List<String> matchedSkills) {

    public static MatchSummary of(JobMatchResult match) {
        return new MatchSummary(match.job().getId(), match.job().getTitle(), match.job().getCompanyName(),
                match.job().getLocation(), Math.round(match.similarityScore() * 1000) / 10.0,
                match.breakdown() != null ? match.breakdown().matchedSkills() : List.of());
    }
}
