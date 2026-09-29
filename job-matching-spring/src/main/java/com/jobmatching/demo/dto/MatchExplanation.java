package com.jobmatching.demo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Why a job matched a candidate the way it did, for an AI model to explain (the explain_match
 * tool in the chat and over MCP). Built by service.MatchScorer, the same code behind the
 * MatchBreakdown bars on the Job Matches page, so the two always agree.
 *
 * The overall percent is embedding similarity and can't be split into points per field; the
 * three parts are separate keyword checks, each saying how it was reached (basis) and whether
 * it's a stand-in that just repeats the overall score (estimated) because there was nothing to
 * compare.
 */
public record MatchExplanation(
        UUID jobId,
        String jobTitle,
        String companyName,
        double overallPercent,
        String howOverallWorks,
        List<String> profileFieldsCompared,
        List<String> emptyProfileFields,
        List<String> jobFieldsCompared,
        Part skills,
        Part titleRelevance,
        Part location
) {

    public record Part(
            double percent,
            boolean estimated,
            String basis,
            List<String> matched,
            List<String> notMatched
    ) {
    }
}
