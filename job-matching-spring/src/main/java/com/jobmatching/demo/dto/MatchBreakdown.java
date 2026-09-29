package com.jobmatching.demo.dto;

import java.util.List;

// The bars under "Why this match?" on the Job Matches page. The *Estimated flags mark a part
// with nothing to compare (e.g. a profile with no skills): its percent then just repeats the
// overall score, and the page shows it as n/a. See service.MatchScorer.
public record MatchBreakdown(
        double overall,
        double skills,
        double titleRelevance,
        double location,
        List<String> matchedSkills,
        boolean skillsEstimated,
        boolean titleRelevanceEstimated,
        boolean locationEstimated
) {
}
