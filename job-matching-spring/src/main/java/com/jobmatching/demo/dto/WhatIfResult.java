package com.jobmatching.demo.dto;

import java.util.List;
import java.util.UUID;

// What a hypothetical profile change would do to the candidate's match scores. before/after are
// the same numbers as the Job Matches bars (MatchBreakdown), for the current and the changed
// profile. Nothing is saved.
public record WhatIfResult(
        List<String> changesApplied,
        String note,
        List<JobWhatIf> jobs
) {

    public record JobWhatIf(
            UUID jobId,
            String jobTitle,
            String companyName,
            double overallBefore,
            double overallAfter,
            double overallChange,
            MatchBreakdown before,
            MatchBreakdown after
    ) {
    }
}
