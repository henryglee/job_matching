package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.AppliedJobView;
import com.jobmatching.demo.dto.DismissJobRequest;
import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.dto.UpdateAppliedStatusRequest;
import com.jobmatching.demo.security.AccessControl;
import com.jobmatching.demo.security.JwtUserPrincipal;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmbeddingService;
import com.jobmatching.demo.service.JobEmbeddingBackfillService;
import com.jobmatching.demo.service.JobMatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ports app.py's GET /api/candidates/&lt;id&gt;/matches, the dismiss-and-replace route and
 * candidate embedding generation. Each match is the job's fields flattened alongside
 * similarity_score/match_breakdown (matching find_matching_jobs' job_dict shape) rather
 * than a nested "job" object.
 */
@RestController
public class JobMatchingController {

    private final JobMatchingService jobMatchingService;
    private final CandidateService candidateService;
    private final EmbeddingService embeddingService;
    private final JobEmbeddingBackfillService jobEmbeddingBackfillService;
    private final JsonMapper jsonMapper;

    public JobMatchingController(JobMatchingService jobMatchingService,
                                 CandidateService candidateService,
                                 EmbeddingService embeddingService,
                                 JobEmbeddingBackfillService jobEmbeddingBackfillService,
                                 JsonMapper jsonMapper) {
        this.jobMatchingService = jobMatchingService;
        this.candidateService = candidateService;
        this.embeddingService = embeddingService;
        this.jobEmbeddingBackfillService = jobEmbeddingBackfillService;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping("/api/candidates/{candidateId}/matches")
    public Map<String, Object> getMatches(@PathVariable UUID candidateId,
                                           @RequestParam(defaultValue = "10") int limit,
                                           @AuthenticationPrincipal JwtUserPrincipal principal) {
        AccessControl.requireOwnerOrStaff(principal, candidateId);

        // Like app.py: embed any jobs still missing one, in the background. Doesn't affect
        // this response; those jobs show up in later ones.
        jobEmbeddingBackfillService.backfillAsync();

        List<JobMatchResult> matches = jobMatchingService.findMatchingJobs(candidateId, Math.max(1, Math.min(limit, 100)));
        List<Map<String, Object>> flattened = matches.stream().map(this::flatten).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidate_id", candidateId);
        body.put("matches", flattened);
        body.put("total_matches", flattened.size());
        body.put("candidate_location", candidateLocation(candidateId));
        return body;
    }

    // Same as app.py: the candidate's city/state/country ("" when unset; {} if the candidate
    // doesn't exist), which the matches page uses to sort jobs by distance.
    private Map<String, String> candidateLocation(UUID candidateId) {
        return candidateService.getCandidateById(candidateId)
                .map(c -> {
                    Map<String, String> location = new LinkedHashMap<>();
                    location.put("city", c.getLocationCity() != null ? c.getLocationCity() : "");
                    location.put("state", c.getLocationState() != null ? c.getLocationState() : "");
                    location.put("country", c.getLocationCountry() != null ? c.getLocationCountry() : "");
                    return location;
                })
                .orElse(Map.of());
    }

    // Ports app.py's POST /api/candidates/<id>/jobs/<job_id>/dismiss. The replacement is
    // the next-best match to fill the dismissed card's slot, in the same flattened shape
    // as /matches entries, or null when there are no more matches.
    @PostMapping("/api/candidates/{candidateId}/jobs/{jobId}/dismiss")
    public Map<String, Object> dismissJob(@PathVariable UUID candidateId,
                                          @PathVariable UUID jobId,
                                          @RequestBody(required = false) DismissJobRequest request,
                                          @AuthenticationPrincipal JwtUserPrincipal principal) {
        AccessControl.requireOwnerOrStaff(principal, candidateId);

        if (!jobMatchingService.dismissJob(candidateId, jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate or job not found");
        }
        return replacementBody(candidateId, request);
    }

    // Marks a job as applied to, which takes it out of the candidate's matches like a
    // dismissal, and returns a replacement the same way. Takes the same current_count body.
    @PostMapping("/api/candidates/{candidateId}/jobs/{jobId}/applied")
    public Map<String, Object> markApplied(@PathVariable UUID candidateId,
                                           @PathVariable UUID jobId,
                                           @RequestBody(required = false) DismissJobRequest request,
                                           @AuthenticationPrincipal JwtUserPrincipal principal) {
        AccessControl.requireOwnerOrStaff(principal, candidateId);

        if (!jobMatchingService.markApplied(candidateId, jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate or job not found");
        }
        return replacementBody(candidateId, request);
    }

    // The jobs the candidate has marked applied, most recent first, for the Applied Jobs page.
    @GetMapping("/api/candidates/{candidateId}/applied-jobs")
    public Map<String, List<AppliedJobView>> listAppliedJobs(@PathVariable UUID candidateId,
                                                             @AuthenticationPrincipal JwtUserPrincipal principal) {
        AccessControl.requireOwnerOrStaff(principal, candidateId);
        return Map.of("applied_jobs", jobMatchingService.listAppliedJobs(candidateId));
    }

    // Sets an applied job's status (APPLIED, INTERVIEWING, WAITING, OFFER or REJECTED) and
    // returns the updated entry. PUT rather than PATCH: CORS only allows GET/POST/PUT/DELETE.
    // Only the candidate: Admin/Staff can view a candidate's applied jobs but not change them.
    @PutMapping("/api/candidates/{candidateId}/applied-jobs/{appliedId}/status")
    public AppliedJobView updateAppliedStatus(@PathVariable UUID candidateId,
                                              @PathVariable UUID appliedId,
                                              @Valid @RequestBody UpdateAppliedStatusRequest request,
                                              @AuthenticationPrincipal JwtUserPrincipal principal) {
        AccessControl.requireOwner(principal, candidateId);
        return jobMatchingService.updateAppliedStatus(candidateId, appliedId, request.status())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Applied job not found"));
    }

    // Takes a job back off the Applied Jobs page; it can then show up in the matches again.
    // appliedId is the entry's own id, since its job_id is null once the listing is cleaned up.
    // Only the candidate, like status changes.
    @DeleteMapping("/api/candidates/{candidateId}/applied-jobs/{appliedId}")
    public Map<String, String> unmarkApplied(@PathVariable UUID candidateId,
                                             @PathVariable UUID appliedId,
                                             @AuthenticationPrincipal JwtUserPrincipal principal) {
        AccessControl.requireOwner(principal, candidateId);
        if (!jobMatchingService.unmarkApplied(candidateId, appliedId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Applied job not found");
        }
        return Map.of("message", "Removed from applied jobs");
    }

    private Map<String, Object> replacementBody(UUID candidateId, DismissJobRequest request) {
        int currentCount = (request != null ? request : new DismissJobRequest(null)).currentCountOrDefault();
        Map<String, Object> replacement = jobMatchingService.findReplacementMatch(candidateId, currentCount)
                .map(this::flatten)
                .orElse(null);

        // Not Map.of: it rejects null values, and "replacement": null is a valid response.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("replacement", replacement);
        return body;
    }

    // Ports app.py's POST /api/candidates/<id>/generate-embedding: (re)builds a candidate's
    // embedding, e.g. for one saved while OpenAI was unavailable. app.py allows any logged-in
    // user and nothing in its UI calls it; restricted to Admin/Staff here like
    // /api/jobs/generate-embeddings, since each call is a paid OpenAI request.
    @PostMapping("/api/candidates/{candidateId}/generate-embedding")
    @PreAuthorize("hasAnyRole('Admin','Staff')")
    public Map<String, Object> generateCandidateEmbedding(@PathVariable UUID candidateId) {
        if (candidateService.getCandidateById(candidateId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found");
        }
        // app.py answers 400 for every failure; the candidate exists at this point, so a
        // failure here is the embedding call itself (see EmbeddingService's log for why).
        if (!embeddingService.generateCandidateEmbedding(candidateId)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to generate embedding — check OPENAI_API_KEY and the OpenAI service status");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Embedding generated successfully");
        body.put("candidate_id", candidateId);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flatten(JobMatchResult match) {
        Map<String, Object> jobMap = new LinkedHashMap<>(jsonMapper.convertValue(match.job(), Map.class));
        jobMap.put("similarity_score", match.similarityScore());
        jobMap.put("match_breakdown", match.breakdown());
        return jobMap;
    }
}
