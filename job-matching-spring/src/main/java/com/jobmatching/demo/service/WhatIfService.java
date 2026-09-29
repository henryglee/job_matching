package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.dto.MatchBreakdown;
import com.jobmatching.demo.dto.ProfileChange;
import com.jobmatching.demo.dto.WhatIfResult;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.JobListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What-if scores: how a hypothetical profile change ("add Java", "set my title to Data
 * Engineer") would change the candidate's match scores, for the what_if_score tools in the
 * chat and over MCP. The overall score is embedding similarity and can't be split into
 * points per field, so the only honest way to answer is to score a changed copy of the
 * profile for real.
 *
 * Both the current and the changed profile are embedded fresh, in one OpenAI call, so the
 * difference reflects only the change and not a stored embedding that may be out of date.
 * The copy is a new object: the candidate's saved profile and embedding are never touched.
 */
@Service
public class WhatIfService {

    private static final Logger log = LoggerFactory.getLogger(WhatIfService.class);

    static final int MAX_JOBS = 10;
    private static final int MAX_SKILLS_CHANGED = 20;
    private static final int MAX_SKILL_CHARS = 60;
    private static final int MAX_FIELD_CHARS = 200;

    static final String NOTE = "Hypothetical: nothing was saved. \"Before\" is recomputed from the current profile so the "
            + "difference reflects only this change; it can differ slightly from the percent on the Job Matches page. "
            + "To make the change real, update the profile on Edit Profile.";

    public static class WhatIfLimitException extends RuntimeException {
        WhatIfLimitException() {
            super("Too many what-if scores in the last hour. Try again later.");
        }
    }

    public static class WhatIfFailedException extends RuntimeException {
        WhatIfFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final CandidateInfoRepository candidateInfoRepository;
    private final JobListingRepository jobListingRepository;
    private final JobMatchingService jobMatchingService;
    private final EmbeddingService embeddingService;
    private final WhatIfLimiter limiter;
    private final AiUsageMonitor usage;

    public WhatIfService(CandidateInfoRepository candidateInfoRepository,
                         JobListingRepository jobListingRepository,
                         JobMatchingService jobMatchingService,
                         EmbeddingService embeddingService,
                         WhatIfLimiter limiter,
                         AiUsageMonitor usage) {
        this.usage = usage;
        this.candidateInfoRepository = candidateInfoRepository;
        this.jobListingRepository = jobListingRepository;
        this.jobMatchingService = jobMatchingService;
        this.embeddingService = embeddingService;
        this.limiter = limiter;
    }

    /**
     * Scores the change against jobIds, or the candidate's current top matches if none are given.
     *
     * @throws IllegalArgumentException for an empty or invalid change, or too many jobs
     * @throws WhatIfLimitException     over the hourly limit
     * @throws WhatIfFailedException    if the embedding call fails
     */
    public WhatIfResult whatIf(UUID candidateId, List<UUID> jobIds, ProfileChange change) {
        CandidateInfo current = candidateInfoRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
        List<String> changesApplied = new ArrayList<>();
        CandidateInfo changed = applyChange(current, change, changesApplied);
        if (changesApplied.isEmpty()) {
            throw new IllegalArgumentException("Nothing to change: give skills to add or remove, or a new title, "
                    + "degree or location that differs from the current profile");
        }
        List<UUID> ids = jobIds == null || jobIds.isEmpty() ? topMatchIds(candidateId) : jobIds.stream().distinct().toList();
        if (ids.size() > MAX_JOBS) {
            throw new IllegalArgumentException("Ask about at most " + MAX_JOBS + " jobs at a time");
        }
        if (ids.isEmpty()) {
            return new WhatIfResult(changesApplied, NOTE, List.of());
        }
        if (!limiter.tryAcquire(candidateId)) {
            usage.record(candidateId, AiUsageEvent.WHAT_IF_LIMITED);
            throw new WhatIfLimitException();
        }

        List<String> vectors;
        try {
            vectors = embeddingService.embedAsVectorLiterals(List.of(
                    embeddingService.buildCandidateProfileText(current),
                    embeddingService.buildCandidateProfileText(changed)));
        } catch (RuntimeException e) {
            log.error("What-if embedding failed for candidate {}", candidateId, e);
            usage.record(candidateId, AiUsageEvent.WHAT_IF_FAILED);
            throw new WhatIfFailedException("The embedding call failed", e);
        }
        Map<UUID, Double> before = distances(vectors.get(0), ids);
        Map<UUID, Double> after = distances(vectors.get(1), ids);
        Map<UUID, JobListing> jobs = jobListingRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(JobListing::getId, Function.identity()));

        List<WhatIfResult.JobWhatIf> results = new ArrayList<>();
        for (UUID id : ids) {
            JobListing job = jobs.get(id);
            if (job == null || !before.containsKey(id) || !after.containsKey(id)) {
                continue; // unknown job, or not embedded yet
            }
            MatchBreakdown was = MatchScorer.breakdown(MatchScorer.explain(current, job, 1 - before.get(id)));
            MatchBreakdown would = MatchScorer.breakdown(MatchScorer.explain(changed, job, 1 - after.get(id)));
            results.add(new WhatIfResult.JobWhatIf(id, job.getTitle(), job.getCompanyName(),
                    was.overall(), would.overall(), Math.round((would.overall() - was.overall()) * 10) / 10.0,
                    was, would));
        }
        usage.record(candidateId, AiUsageEvent.WHAT_IF);
        return new WhatIfResult(changesApplied, NOTE, results);
    }

    private List<UUID> topMatchIds(UUID candidateId) {
        return jobMatchingService.findMatchingJobs(candidateId, MAX_JOBS).stream()
                .map(JobMatchResult::job)
                .map(JobListing::getId)
                .toList();
    }

    private Map<UUID, Double> distances(String vector, List<UUID> ids) {
        return jobListingRepository.findDistances(vector, ids).stream()
                .collect(Collectors.toMap(JobListingRepository.JobMatch::getId, JobListingRepository.JobMatch::getDistance));
    }

    // A detached copy of the profile with the change applied, recording each real change in
    // plain words. Only the fields that go into the embedding and the scoring are copied.
    static CandidateInfo applyChange(CandidateInfo current, ProfileChange change, List<String> changesApplied) {
        CandidateInfo copy = new CandidateInfo();
        copy.setId(current.getId());
        copy.setCandidateName(current.getCandidateName());
        copy.setCollegeDegree(current.getCollegeDegree());
        copy.setPreferredJobTitle(current.getPreferredJobTitle());
        copy.setLocationCity(current.getLocationCity());
        copy.setLocationState(current.getLocationState());
        copy.setLocationCountry(current.getLocationCountry());
        List<String> skills = new ArrayList<>(current.getSkills() == null ? List.of() : current.getSkills());
        if (change == null) {
            copy.setSkills(skills);
            return copy;
        }

        List<String> add = cleanSkills(change.addSkills());
        List<String> remove = cleanSkills(change.removeSkills());
        if (add.size() + remove.size() > MAX_SKILLS_CHANGED) {
            throw new IllegalArgumentException("Change at most " + MAX_SKILLS_CHANGED + " skills at a time");
        }
        for (String skill : remove) {
            if (skills.removeIf(s -> s != null && s.equalsIgnoreCase(skill))) {
                changesApplied.add("Removed skill: " + skill);
            }
        }
        for (String skill : add) {
            if (skills.stream().noneMatch(s -> s != null && s.equalsIgnoreCase(skill))) {
                skills.add(skill);
                changesApplied.add("Added skill: " + skill);
            }
        }
        copy.setSkills(skills);

        setField(change.preferredJobTitle(), current.getPreferredJobTitle(), copy::setPreferredJobTitle, "Preferred job title", changesApplied);
        setField(change.collegeDegree(), current.getCollegeDegree(), copy::setCollegeDegree, "College degree", changesApplied);
        setField(change.locationCity(), current.getLocationCity(), copy::setLocationCity, "City", changesApplied);
        setField(change.locationState(), current.getLocationState(), copy::setLocationState, "State", changesApplied);
        setField(change.locationCountry(), current.getLocationCountry(), copy::setLocationCountry, "Country", changesApplied);
        return copy;
    }

    private static List<String> cleanSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) {
                continue;
            }
            String trimmed = skill.strip();
            if (trimmed.length() > MAX_SKILL_CHARS) {
                throw new IllegalArgumentException("Skills must be at most " + MAX_SKILL_CHARS + " characters");
            }
            if (cleaned.stream().noneMatch(s -> s.toLowerCase(Locale.ROOT).equals(trimmed.toLowerCase(Locale.ROOT)))) {
                cleaned.add(trimmed);
            }
        }
        return cleaned;
    }

    // null leaves the field; blank clears it; anything else replaces it if it differs.
    private static void setField(String requested, String currentValue, Consumer<String> setter,
                                 String label, List<String> changesApplied) {
        if (requested == null) {
            return;
        }
        String value = requested.isBlank() ? null : requested.strip();
        if (value != null && value.length() > MAX_FIELD_CHARS) {
            throw new IllegalArgumentException(label + " must be at most " + MAX_FIELD_CHARS + " characters");
        }
        String existing = currentValue == null || currentValue.isBlank() ? null : currentValue.strip();
        if (value == null ? existing == null : value.equals(existing)) {
            return;
        }
        setter.accept(value);
        changesApplied.add(value == null ? label + " cleared" : label + ": " + (existing == null ? "(none)" : existing) + " → " + value);
    }
}
