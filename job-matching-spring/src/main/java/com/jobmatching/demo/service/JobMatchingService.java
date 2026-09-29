package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.AppliedJobView;
import com.jobmatching.demo.dto.EmailDraftView;
import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.dto.MatchExplanation;
import com.jobmatching.demo.entity.CandidateAppliedJob;
import com.jobmatching.demo.entity.CandidateDismissedJob;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.CandidateAppliedJobRepository;
import com.jobmatching.demo.repository.CandidateDismissedJobRepository;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.JobListingRepository;
import com.jobmatching.demo.scrape.JobExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ports jobspy/database.py's find_matching_jobs: vector-similarity search plus the
 * same skills/title/location sub-score breakdown, so the numbers shown to users don't
 * change as part of this migration. The scoring lives in MatchScorer, shared with
 * explainMatch (the chat's and MCP's explain_match tools).
 */
@Service
public class JobMatchingService {

    private static final Logger log = LoggerFactory.getLogger(JobMatchingService.class);

    // Rounds of "check the top matches for expired listings, then re-query to fill their
    // places". Normally one; bounded so a mass expiry can't turn one request into many calls.
    static final int MAX_EXPIRY_PASSES = 3;
    // Callers pass client-supplied counts (?limit=, the dismiss body's current_count); each extra
    // match is another row scored and, for Indeed jobs, another expiry check.
    static final int MAX_MATCHES = 100;

    private final CandidateInfoRepository candidateInfoRepository;
    private final JobListingRepository jobListingRepository;
    private final CandidateDismissedJobRepository dismissedJobRepository;
    private final CandidateAppliedJobRepository appliedJobRepository;
    private final EmbeddingService embeddingService;
    private final JobExpiryService jobExpiryService;

    public JobMatchingService(CandidateInfoRepository candidateInfoRepository,
                               JobListingRepository jobListingRepository,
                               CandidateDismissedJobRepository dismissedJobRepository,
                               CandidateAppliedJobRepository appliedJobRepository,
                               EmbeddingService embeddingService,
                               JobExpiryService jobExpiryService) {
        this.candidateInfoRepository = candidateInfoRepository;
        this.jobListingRepository = jobListingRepository;
        this.dismissedJobRepository = dismissedJobRepository;
        this.appliedJobRepository = appliedJobRepository;
        this.embeddingService = embeddingService;
        this.jobExpiryService = jobExpiryService;
    }

    public List<JobMatchResult> findMatchingJobs(UUID candidateId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_MATCHES));
        CandidateInfo candidate = candidateInfoRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            log.error("Candidate not found: {}", candidateId);
            return List.of();
        }

        String embeddingStr = candidateEmbedding(candidateId);
        if (embeddingStr == null) {
            return List.of();
        }

        // Jobs the candidate dismissed or already applied to don't come back as matches.
        List<UUID> excludedIds = new ArrayList<>(dismissedJobRepository.findJobIdsByCandidateId(candidateId));
        excludedIds.addAll(appliedJobRepository.findJobIdsByCandidateId(candidateId));
        List<JobListingRepository.JobMatch> matches = List.of();
        Map<UUID, JobListing> jobsById = Map.of();
        Set<UUID> expiredIds = Set.of();
        // Listings found expired are marked, which drops them from the match query, so
        // re-running it moves the next-best jobs up into their places (and checks those too).
        for (int pass = 1; pass <= MAX_EXPIRY_PASSES; pass++) {
            matches = excludedIds.isEmpty()
                    ? jobListingRepository.findTopMatches(embeddingStr, limit)
                    : jobListingRepository.findTopMatchesExcluding(embeddingStr, excludedIds, limit);
            if (matches.isEmpty()) {
                return List.of();
            }
            jobsById = jobListingRepository.findByIdIn(matches.stream().map(JobListingRepository.JobMatch::getId).toList())
                    .stream().collect(Collectors.toMap(JobListing::getId, j -> j));
            expiredIds = jobExpiryService.markExpired(jobsById.values());
            if (expiredIds.isEmpty()) {
                break;
            }
        }

        List<UUID> orderedIds = matches.stream().map(JobListingRepository.JobMatch::getId).toList();
        Map<UUID, Double> distanceById = matches.stream()
                .collect(Collectors.toMap(JobListingRepository.JobMatch::getId, JobListingRepository.JobMatch::getDistance));

        List<JobMatchResult> results = new ArrayList<>();
        for (UUID jobId : orderedIds) {
            JobListing job = jobsById.get(jobId);
            if (job == null || expiredIds.contains(jobId)) {
                continue;
            }
            double similarity = 1 - distanceById.get(jobId);
            MatchExplanation explanation = MatchScorer.explain(candidate, job, similarity);
            results.add(new JobMatchResult(job, similarity, MatchScorer.breakdown(explanation)));
        }

        log.info("Found {} matching jobs for candidate {}", results.size(), candidateId);
        return results;
    }

    /**
     * Why one job matches the candidate the way it does, for the explain_match tools: the same
     * numbers as its Job Matches bars, plus how each was reached. Works for any job with an
     * embedding, not only the top matches. Empty if the candidate or job doesn't exist, or
     * either has no embedding yet (a job gets one shortly after it's scraped).
     */
    public Optional<MatchExplanation> explainMatch(UUID candidateId, UUID jobId) {
        CandidateInfo candidate = candidateInfoRepository.findById(candidateId).orElse(null);
        JobListing job = jobListingRepository.findById(jobId).orElse(null);
        if (candidate == null || job == null) {
            return Optional.empty();
        }
        String embeddingStr = candidateEmbedding(candidateId);
        if (embeddingStr == null) {
            return Optional.empty();
        }
        return jobListingRepository.findDistance(embeddingStr, jobId)
                .map(distance -> MatchScorer.explain(candidate, job, 1 - distance));
    }

    // The candidate's embedding, generating it first if it's missing (e.g. a profile saved
    // while OpenAI was unavailable). Null if that fails.
    private String candidateEmbedding(UUID candidateId) {
        String embeddingStr = candidateInfoRepository.findEmbeddingById(candidateId).orElse(null);
        if (embeddingStr != null) {
            return embeddingStr;
        }
        log.warn("No embedding for candidate {}, generating now...", candidateId);
        if (!embeddingService.generateCandidateEmbedding(candidateId)) {
            log.error("Failed to generate embedding for candidate {}", candidateId);
            return null;
        }
        return candidateInfoRepository.findEmbeddingById(candidateId).orElse(null);
    }

    /**
     * Mirrors JobDatabase.dismiss_job: records that the candidate isn't interested in a job,
     * so findMatchingJobs excludes it from then on. Idempotent — dismissing twice is a no-op.
     * Returns false if the candidate or job doesn't exist (app.py leaves that to the
     * foreign-key constraint and swallows the error).
     */
    @Transactional
    public boolean dismissJob(UUID candidateId, UUID jobId) {
        if (!candidateInfoRepository.existsById(candidateId) || !jobListingRepository.existsById(jobId)) {
            return false;
        }
        if (!dismissedJobRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            CandidateDismissedJob dismissal = new CandidateDismissedJob();
            dismissal.setCandidateId(candidateId);
            dismissal.setJobId(jobId);
            dismissedJobRepository.save(dismissal);
        }
        return true;
    }

    /**
     * Records that the candidate has applied to a job, so findMatchingJobs excludes it from
     * then on, like a dismissal, and it's listed on their Applied Jobs page. Copies the job's
     * details so that entry survives the listing being cleaned up. Idempotent. Returns false if
     * the candidate or job doesn't exist.
     */
    @Transactional
    public boolean markApplied(UUID candidateId, UUID jobId) {
        JobListing job = jobListingRepository.findById(jobId).orElse(null);
        if (!candidateInfoRepository.existsById(candidateId) || job == null) {
            return false;
        }
        if (!appliedJobRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            CandidateAppliedJob applied = new CandidateAppliedJob();
            applied.setCandidateId(candidateId);
            applied.setJobId(jobId);
            applied.setJobTitle(job.getTitle());
            applied.setCompanyName(job.getCompanyName());
            applied.setLocation(job.getLocation());
            applied.setJobUrl(EmailDraftView.applyUrl(job));
            appliedJobRepository.save(applied);
        }
        return true;
    }

    // The candidate's Applied Jobs page, most recently applied first.
    public List<AppliedJobView> listAppliedJobs(UUID candidateId) {
        return appliedJobRepository.findByCandidateIdOrderByAppliedAtDesc(candidateId).stream()
                .map(AppliedJobView::of)
                .toList();
    }

    /**
     * Sets where an application stands (from the Applied Jobs page's status dropdown). Empty
     * if the candidate has no such entry. Picking the status it already has changes nothing.
     */
    @Transactional
    public Optional<AppliedJobView> updateAppliedStatus(UUID candidateId, UUID appliedId,
                                                        CandidateAppliedJob.Status status) {
        return appliedJobRepository.findByIdAndCandidateId(appliedId, candidateId)
                .map(applied -> {
                    if (applied.getStatus() != status) {
                        applied.setStatus(status);
                        applied.setStatusUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                        appliedJobRepository.save(applied);
                    }
                    return AppliedJobView.of(applied);
                });
    }

    /**
     * Undoes marking a job applied (e.g. a mis-click): drops it from the Applied Jobs page, and
     * the job can show up in the candidate's matches again. False if the candidate has no such
     * entry.
     */
    @Transactional
    public boolean unmarkApplied(UUID candidateId, UUID appliedId) {
        return appliedJobRepository.findByIdAndCandidateId(appliedId, candidateId)
                .map(applied -> {
                    appliedJobRepository.delete(applied);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Mirrors app.py's dismiss route (also used after marking a job applied): after a dismissal, the client's list of
     * {@code currentCount} matches is one short. Re-running the match (which now excludes
     * the dismissed job) and taking the entry at position currentCount gives the next-best
     * job to fill the gap. Empty when there are no further matches to offer.
     */
    public Optional<JobMatchResult> findReplacementMatch(UUID candidateId, int currentCount) {
        if (currentCount < 1) {
            return Optional.empty();
        }
        List<JobMatchResult> matches = findMatchingJobs(candidateId, currentCount);
        return matches.size() >= currentCount ? Optional.of(matches.get(currentCount - 1)) : Optional.empty();
    }
}
