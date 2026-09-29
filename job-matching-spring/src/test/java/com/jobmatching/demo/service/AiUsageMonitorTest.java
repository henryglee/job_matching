package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.AiUsageReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiUsageMonitorTest {

    private final AiUsageMonitor monitor = new AiUsageMonitor();
    private final UUID quiet = UUID.randomUUID();
    private final UUID busy = UUID.randomUUID();

    @Test
    void countsEventsAndTokensPerCandidateWithTotals() {
        monitor.record(quiet, AiUsageEvent.CHAT_MESSAGE, 400);
        monitor.record(busy, AiUsageEvent.CHAT_MESSAGE, 900);
        monitor.record(busy, AiUsageEvent.CHAT_CUT_OFF);
        monitor.record(busy, AiUsageEvent.DRAFT_GENERATED, 1200);
        monitor.record(busy, AiUsageEvent.WHAT_IF);
        monitor.record(busy, AiUsageEvent.CHAT_LIMITED);
        monitor.record(busy, AiUsageEvent.WHAT_IF_LIMITED);
        monitor.record(busy, AiUsageEvent.DRAFT_FAILED);
        monitor.record(quiet, AiUsageEvent.MODERATION_UNAVAILABLE);

        AiUsageReport report = monitor.report(ids -> Map.of(busy, "Sam Lee"));

        assertNotNull(report.since());
        AiUsageReport.Usage totals = report.totals();
        assertEquals(2, totals.chatMessages());
        assertEquals(2500, totals.modelTokens());
        assertEquals(1, totals.draftsGenerated());
        assertEquals(1, totals.whatIfScores());
        assertEquals(2, totals.limitHits());
        assertEquals(1, totals.cutOffs());
        assertEquals(1, totals.failures());
        assertEquals(1, totals.moderationUnavailable());

        // Busiest first, with names where known.
        List<AiUsageReport.CandidateUsage> candidates = report.candidates();
        assertEquals(busy, candidates.get(0).candidateId());
        assertEquals("Sam Lee", candidates.get(0).candidateName());
        assertEquals(2100, candidates.get(0).usage().modelTokens());
        assertTrue(candidates.get(0).needsReview(), "hit limits");
        assertEquals(quiet, candidates.get(1).candidateId());
        assertEquals(null, candidates.get(1).candidateName());
        assertFalse(candidates.get(1).needsReview());
        assertNotNull(candidates.get(1).lastActivity());
    }

    @Test
    void aModerationBlockFlagsTheCandidateForReview() {
        monitor.record(quiet, AiUsageEvent.MODERATION_BLOCKED);

        AiUsageReport.CandidateUsage usage = monitor.report(ids -> Map.of()).candidates().get(0);

        assertEquals(1, usage.usage().moderationBlocked());
        assertTrue(usage.needsReview());
    }

    @Test
    void emptyReportBeforeAnyUse() {
        AiUsageReport report = monitor.report(ids -> Map.of());
        assertTrue(report.candidates().isEmpty());
        assertEquals(0, report.totals().chatMessages());
    }
}
