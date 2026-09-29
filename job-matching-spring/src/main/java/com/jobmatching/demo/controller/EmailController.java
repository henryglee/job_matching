package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.dto.MatchBreakdown;
import com.jobmatching.demo.dto.TestEmailRequest;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmailService;
import com.jobmatching.demo.service.JobMatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ports app.py's POST /api/candidates/&lt;id&gt;/send-matches and POST /api/email/test.
 */
@RestController
public class EmailController {

    private static final int TOP_MATCHES_FOR_EMAIL = 10;

    private final CandidateService candidateService;
    private final JobMatchingService jobMatchingService;
    private final EmailService emailService;

    public EmailController(CandidateService candidateService, JobMatchingService jobMatchingService, EmailService emailService) {
        this.candidateService = candidateService;
        this.jobMatchingService = jobMatchingService;
        this.emailService = emailService;
    }

    @PostMapping("/api/candidates/{candidateId}/send-matches")
    @PreAuthorize("hasAnyRole('Admin','Staff')")
    public Map<String, String> sendMatches(@PathVariable UUID candidateId) {
        CandidateInfo candidate = candidateService.getCandidateById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
        if (candidate.getEmail() == null || candidate.getEmail().isBlank()) {
            throw new IllegalArgumentException("Candidate has no email on file");
        }

        List<JobMatchResult> matches = jobMatchingService.findMatchingJobs(candidateId, TOP_MATCHES_FOR_EMAIL);
        // false means there was nothing to send (no matches yet), not that it went out.
        if (!emailService.sendJobMatches(candidate.getCandidateName(), candidate.getEmail(), matches)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No job matches to send yet");
        }
        return Map.of("message", "Match email sent to " + candidate.getEmail());
    }

    // app.py allows any logged-in user, which lets any account send mail through the SES
    // sender to arbitrary addresses; it's an ops check, so Admin/Staff only here.
    @PostMapping("/api/email/test")
    @PreAuthorize("hasAnyRole('Admin','Staff')")
    public Map<String, String> sendTestEmail(@Valid @RequestBody TestEmailRequest request) {
        emailService.sendJobMatches("Test User", request.email(), fakeMatches());
        return Map.of("message", "Test email sent to " + request.email());
    }

    private static List<JobMatchResult> fakeMatches() {
        return List.of(
                fakeMatch("Software Engineer", "Acme Corp", "https://example.com/jobs/1", 0.95),
                fakeMatch("Backend Developer", "Globex Inc", "https://example.com/jobs/2", 0.89)
        );
    }

    private static JobMatchResult fakeMatch(String title, String companyName, String jobUrl, double similarity) {
        JobListing job = new JobListing();
        job.setTitle(title);
        job.setCompanyName(companyName);
        job.setJobUrl(jobUrl);
        MatchBreakdown breakdown = new MatchBreakdown(similarity * 100, 0, 0, 0, List.of(), false, false, false);
        return new JobMatchResult(job, similarity, breakdown);
    }
}
