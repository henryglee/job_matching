package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.CandidateIdRequest;
import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.scrape.ScrapeOrchestrationService;
import com.jobmatching.demo.security.AccessControl;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.security.JwtUserPrincipal;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmailService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.UserAccountService;
import com.jobmatching.demo.util.DateParsing;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ports app.py's candidate-profile routes. Role enforcement mirrors the inline checks in
 * app.py exactly: a User may only ever see/edit their own linked profile; Admin/Staff see
 * everything (see AccessControl).
 */
@RestController
public class CandidateController {

    private static final Logger log = LoggerFactory.getLogger(CandidateController.class);

    private final CandidateService candidateService;
    private final UserAccountService userAccountService;
    private final JobMatchingService jobMatchingService;
    private final EmailService emailService;
    private final ScrapeOrchestrationService scrapeOrchestrationService;
    private final JsonMapper jsonMapper;
    private final JwtService jwtService;

    public CandidateController(CandidateService candidateService,
                                UserAccountService userAccountService,
                                JobMatchingService jobMatchingService,
                                EmailService emailService,
                                ScrapeOrchestrationService scrapeOrchestrationService,
                                JsonMapper jsonMapper,
                                JwtService jwtService) {
        this.candidateService = candidateService;
        this.userAccountService = userAccountService;
        this.jobMatchingService = jobMatchingService;
        this.emailService = emailService;
        this.scrapeOrchestrationService = scrapeOrchestrationService;
        this.jsonMapper = jsonMapper;
        this.jwtService = jwtService;
    }

    @PostMapping(value = "/api/candidates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createCandidate(
            @RequestParam(value = "candidate_name", required = false) String candidateName,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "college_degree", required = false) String collegeDegree,
            @RequestParam(value = "graduation_date", required = false) String graduationDate,
            @RequestParam(value = "preferred_job_title", required = false) String preferredJobTitle,
            @RequestParam(value = "skills", required = false) List<String> skills,
            @RequestParam(value = "location_city", required = false) String locationCity,
            @RequestParam(value = "location_state", required = false) String locationState,
            @RequestParam(value = "location_country", required = false) String locationCountry,
            @RequestParam(value = "requires_sponsorship", required = false) String requiresSponsorship,
            @RequestParam(value = "sponsorship_type", required = false) List<String> sponsorshipType,
            @RequestParam(value = "resume", required = false) MultipartFile resume,
            @AuthenticationPrincipal JwtUserPrincipal principal) throws IOException {

        // A User's profile email is always their account email: a blank one defaults to it.
        if ((email == null || email.isBlank()) && !AccessControl.isStaffOrAdmin(principal)) {
            email = principal.email();
        }

        // A User has one profile; a second would replace the link and leave the first orphaned
        // and claimable by anyone with a matching email.
        if ("User".equals(principal.activeRole()) && principal.candidateProfileId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a candidate profile");
        }

        // Same rule as editing: a User's profile email must be their own account email.
        if (!AccessControl.isStaffOrAdmin(principal) && email != null && !email.isBlank()
                && !mayEmailMatchesTo(email, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profile email must match your account email");
        }

        // Like app.py: a User who leaves the name blank gets their account name instead.
        if ((candidateName == null || candidateName.isBlank()) && !AccessControl.isStaffOrAdmin(principal)) {
            candidateName = userAccountService.getUserById(principal.userId())
                    .map(CandidateController::accountDisplayName)
                    .orElse(null);
        }
        if (candidateName == null || candidateName.isBlank()) {
            throw new IllegalArgumentException("Candidate name is required");
        }

        CandidateInfo candidate = new CandidateInfo();
        candidate.setCandidateName(candidateName.strip());
        candidate.setEmail(email);
        candidate.setCollegeDegree(collegeDegree);
        candidate.setGraduationDate(DateParsing.parseIsoOrDate(graduationDate));
        candidate.setPreferredJobTitle(preferredJobTitle);
        candidate.setSkills(skills);
        candidate.setLocationCity(blankToNull(locationCity));
        candidate.setLocationState(blankToNull(locationState));
        candidate.setLocationCountry(blankToNull(locationCountry));
        // Same parsing as app.py: only the literal "true" counts, and sponsorship types are
        // kept only when sponsorship is required (the form hides them otherwise).
        boolean needsSponsorship = "true".equals(requiresSponsorship);
        candidate.setRequiresSponsorship(needsSponsorship);
        candidate.setSponsorshipType(needsSponsorship && sponsorshipType != null ? sponsorshipType : List.of());

        if (resume != null && !resume.isEmpty() && hasPdfExtension(resume.getOriginalFilename())) {
            candidate.setResumeFilename(resume.getOriginalFilename());
            candidate.setResumeContentType(resume.getContentType());
            candidate.setResumeData(Base64.getEncoder().encodeToString(resume.getBytes()));
        }

        CandidateInfo saved = candidateService.createCandidateWithEmbedding(candidate);

        if ("User".equals(principal.activeRole())) {
            userAccountService.associateCandidateProfile(principal.userId(), saved.getId());
        }
        if (preferredJobTitle != null && !preferredJobTitle.isBlank()) {
            scrapeOrchestrationService.scrapeForTitleAsync(preferredJobTitle);
        }

        boolean emailSent = false;
        // Only Staff/Admin can reach here with an email other than the account's own (checked above).
        if (email != null && !email.isBlank()) {
            List<JobMatchResult> matches = jobMatchingService.findMatchingJobs(saved.getId(), 10);
            // The profile is already saved by now, so a failed send (SES down, unverified
            // address) must not turn the whole request into an error the user would retry.
            try {
                emailSent = emailService.sendJobMatches(candidateName, email, matches);
            } catch (RuntimeException e) {
                log.warn("Could not email matches for new candidate {}: {}", saved.getId(), e.getMessage());
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Candidate information saved successfully");
        body.put("candidate_id", saved.getId());
        body.put("email_sent", emailSent);
        if ("User".equals(principal.activeRole())) {
            addRefreshedToken(body, principal);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/api/candidates")
    public Map<String, Object> getCandidates(@RequestParam(required = false) Integer limit,
                                              @AuthenticationPrincipal JwtUserPrincipal principal) {
        List<CandidateInfo> candidates;
        if (AccessControl.isStaffOrAdmin(principal)) {
            Pageable pageable = limit != null ? PageRequest.of(0, limit) : Pageable.unpaged();
            candidates = candidateService.getCandidates(pageable).getContent();
        } else if (principal.candidateProfileId() != null) {
            candidates = candidateService.getCandidateById(principal.candidateProfileId()).map(List::of).orElse(List.of());
        } else {
            candidates = List.of();
        }
        return Map.of("candidates", withLinkedAccounts(candidates));
    }

    @GetMapping("/api/candidates/{candidateId}")
    public Map<String, Object> getCandidate(@PathVariable UUID candidateId,
                                             @AuthenticationPrincipal JwtUserPrincipal principal) {
        AccessControl.requireOwnerOrStaff(principal, candidateId);
        CandidateInfo candidate = candidateService.getCandidateById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
        return withLinkedAccounts(List.of(candidate)).get(0);
    }

    @PutMapping("/api/candidates/{candidateId}")
    public Map<String, String> updateCandidate(@PathVariable UUID candidateId,
                                                @RequestBody Map<String, Object> candidateData,
                                                @AuthenticationPrincipal JwtUserPrincipal principal) {
        AccessControl.requireOwnerOrStaff(principal, candidateId);
        // A User's profile email must stay their own (sign-up-verified) address, or it could be
        // pointed at someone else's. Clearing it is fine, and so is sending back the address the
        // profile already has: the edit page submits the whole form on every save, so a profile
        // saved before this rule existed would otherwise be un-saveable.
        if (!AccessControl.isStaffOrAdmin(principal) && candidateData.get("email") instanceof String email
                && !email.isBlank() && !mayEmailMatchesTo(email, principal) && !isCurrentEmail(candidateId, email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profile email must match your account email");
        }
        boolean updated = candidateService.updateCandidate(candidateId, candidateData);
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found");
        }
        // Without this the matches keep reflecting the old skills/title/location.
        if (candidateService.affectsEmbedding(candidateData)) {
            candidateService.refreshEmbedding(candidateId);
        }
        return Map.of("message", "Candidate updated successfully");
    }

    // Admin or Staff, as in app.py.
    @DeleteMapping("/api/candidates/{candidateId}")
    @PreAuthorize("hasAnyRole('Admin','Staff')")
    public Map<String, String> deleteCandidate(@PathVariable UUID candidateId) {
        boolean deleted = candidateService.deleteCandidate(candidateId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found");
        }
        return Map.of("message", "Candidate deleted successfully");
    }

    // Admin/Staff see every unclaimed profile. Anyone else only sees profiles whose email
    // matches their own account email (verified at sign-up, taken from the signed JWT):
    // app.py listed every unclaimed profile's name and email to any logged-in user.
    @GetMapping("/api/candidates/unclaimed")
    public Map<String, Object> getUnclaimedCandidates(@AuthenticationPrincipal JwtUserPrincipal principal) {
        // Mirrors get_unclaimed_candidates' trimmed raw-SQL projection — only these 5
        // fields, not the full profile (no skills/resume/location leaking to anyone
        // authenticated who's just browsing the unclaimed list).
        List<CandidateInfo> visible = AccessControl.isStaffOrAdmin(principal)
                ? candidateService.getUnclaimedCandidates()
                : claimableBy(principal);
        List<Map<String, Object>> candidates = visible.stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("candidate_name", c.getCandidateName());
                    m.put("email", c.getEmail());
                    m.put("preferred_job_title", c.getPreferredJobTitle());
                    m.put("college_degree", c.getCollegeDegree());
                    return m;
                })
                .toList();
        return Map.of("candidates", candidates);
    }

    @PostMapping("/api/user/claim-profile")
    @PreAuthorize("hasRole('User')")
    public Map<String, Object> claimProfile(@Valid @RequestBody CandidateIdRequest request,
                                             @AuthenticationPrincipal JwtUserPrincipal principal) {
        // Same rule as the list: only an unclaimed profile with the user's own email, so
        // knowing (or guessing) another profile's id isn't enough to take it over.
        boolean claimable = claimableBy(principal).stream()
                .anyMatch(c -> c.getId().equals(request.candidateId()));
        if (!claimable) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found or already claimed");
        }
        userAccountService.associateCandidateProfile(principal.userId(), request.candidateId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidate_id", request.candidateId());
        body.put("message", "Profile claimed successfully");
        addRefreshedToken(body, principal);
        return body;
    }

    // Mirrors get_candidates / get_candidate_by_id's outer join onto user_accounts: each
    // candidate's own fields plus linked_user_id / account_first_name / account_last_name
    // (all null when no account is linked), which the UI needs to edit the account's name.
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> withLinkedAccounts(List<CandidateInfo> candidates) {
        Map<UUID, UserAccount> accounts = userAccountService.getLinkedAccounts(
                candidates.stream().map(CandidateInfo::getId).toList());
        return candidates.stream().map(candidate -> {
            Map<String, Object> m = new LinkedHashMap<>(jsonMapper.convertValue(candidate, Map.class));
            UserAccount account = accounts.get(candidate.getId());
            m.put("linked_user_id", account != null ? account.getId() : null);
            m.put("account_first_name", account != null ? account.getFirstName() : null);
            m.put("account_last_name", account != null ? account.getLastName() : null);
            return m;
        }).toList();
    }

    // The profile id is a claim in the login token, so the token the client holds doesn't know
    // about a profile linked after login. Send a new one (same role) so it survives a reload.
    private void addRefreshedToken(Map<String, Object> body, JwtUserPrincipal principal) {
        userAccountService.getUserWithProfile(principal.userId())
                .ifPresent(user -> body.put("token", jwtService.generateToken(user, principal.activeRole())));
    }

    private boolean isCurrentEmail(UUID candidateId, String email) {
        return candidateService.getCandidateById(candidateId)
                .map(CandidateInfo::getEmail)
                .filter(current -> current.strip().equalsIgnoreCase(email.strip()))
                .isPresent();
    }

    private static boolean mayEmailMatchesTo(String email, JwtUserPrincipal principal) {
        return AccessControl.isStaffOrAdmin(principal)
                || (principal.email() != null && principal.email().equalsIgnoreCase(email.strip()));
    }

    private List<CandidateInfo> claimableBy(JwtUserPrincipal principal) {
        if (principal.email() == null || principal.email().isBlank()) {
            return List.of();
        }
        return candidateService.findUnclaimedCandidatesByEmail(principal.email());
    }

    // "First Last" from the account, or null when neither part is set.
    private static String accountDisplayName(UserAccount user) {
        String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).strip();
        return name.isEmpty() ? null : name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean hasPdfExtension(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }
}
