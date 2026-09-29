package com.jobmatching.demo.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Admin view of AI usage since the server started (service.AiUsageMonitor). Counts and token
// totals only; no message content is ever kept.
public record AiUsageReport(
        Instant since,
        Usage totals,
        List<CandidateUsage> candidates
) {

    public record Usage(
            long chatMessages,
            long draftsGenerated,
            long whatIfScores,
            long modelTokens,
            long moderationBlocked,
            long moderationUnavailable,
            long limitHits,
            long cutOffs,
            long failures
    ) {
    }

    // needsReview: blocked by moderation, or hit an hourly limit, at least once.
    public record CandidateUsage(
            UUID candidateId,
            String candidateName,
            Usage usage,
            Instant lastActivity,
            boolean needsReview
    ) {
    }
}
