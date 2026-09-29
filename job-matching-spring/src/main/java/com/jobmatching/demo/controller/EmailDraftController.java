package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.CreateDraftRequest;
import com.jobmatching.demo.dto.EmailDraftView;
import com.jobmatching.demo.dto.GenerateDraftRequest;
import com.jobmatching.demo.dto.UpdateEmailDraftRequest;
import com.jobmatching.demo.entity.EmailDraft;
import com.jobmatching.demo.security.JwtUserPrincipal;
import com.jobmatching.demo.service.AiUsageEvent;
import com.jobmatching.demo.service.AiUsageMonitor;
import com.jobmatching.demo.service.DraftGenerationLimiter;
import com.jobmatching.demo.service.EmailDraftGenerator;
import com.jobmatching.demo.service.EmailDraftService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The candidate's side of application email drafts: create, generate, review, edit, mark
 * sent, delete. Drafts come from POST /api/drafts/generate (the app's own model), from a draft
 * the in-app chat proposed and the candidate saved (POST /api/drafts), or from the candidate's
 * AI assistant through mcp/EmailDraftTools. No app.py equivalent.
 *
 * Every route acts on the caller's own linked candidate profile, so there's no candidate id
 * in the path to check. An account with no profile (typically Admin/Staff) gets a 403.
 */
@RestController
public class EmailDraftController {

    private final EmailDraftService emailDraftService;
    private final EmailDraftGenerator emailDraftGenerator;
    private final DraftGenerationLimiter draftGenerationLimiter;
    private final AiUsageMonitor aiUsageMonitor;

    public EmailDraftController(EmailDraftService emailDraftService,
                                EmailDraftGenerator emailDraftGenerator,
                                DraftGenerationLimiter draftGenerationLimiter,
                                AiUsageMonitor aiUsageMonitor) {
        this.emailDraftService = emailDraftService;
        this.emailDraftGenerator = emailDraftGenerator;
        this.draftGenerationLimiter = draftGenerationLimiter;
        this.aiUsageMonitor = aiUsageMonitor;
    }

    // status: DRAFT or SENT; omitted means both.
    @GetMapping("/api/drafts")
    public Map<String, List<EmailDraftView>> listDrafts(@RequestParam(required = false) EmailDraft.Status status,
                                                        @AuthenticationPrincipal JwtUserPrincipal principal) {
        return Map.of("drafts", emailDraftService.listDrafts(candidateId(principal), status));
    }

    // Saves a draft the candidate already has, e.g. one the chat proposed (a DraftProposal).
    // The recipient check in createDraft still applies, so it must be one of the job's contact
    // emails or empty; the candidate can change it afterwards.
    @PostMapping("/api/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailDraftView createDraft(@Valid @RequestBody CreateDraftRequest request,
                                      @AuthenticationPrincipal JwtUserPrincipal principal) {
        return emailDraftService.createDraft(candidateId(principal), request.jobId(),
                request.recipient(), request.subject(), request.body());
    }

    // Writes a draft with the app's OpenAI model and saves it for review. Takes a few seconds.
    // The error messages point at the MCP route because the React dialog offers it as the
    // fallback right below the Generate button.
    @PostMapping("/api/drafts/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailDraftView generateDraft(@Valid @RequestBody GenerateDraftRequest request,
                                        @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID candidateId = candidateId(principal);
        if (!draftGenerationLimiter.tryAcquire(candidateId)) {
            aiUsageMonitor.record(candidateId, AiUsageEvent.DRAFT_LIMITED);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've generated a lot of drafts in the last hour. Try again later, or use your own AI assistant.");
        }
        try {
            return emailDraftGenerator.generate(candidateId, request.jobId(), request.instructions())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        } catch (EmailDraftGenerator.InstructionsRejectedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Those instructions can't be used. Try rephrasing them, or leave them blank.");
        } catch (EmailDraftGenerator.GenerationFailedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Couldn't generate a draft right now. Try again, or use your own AI assistant.");
        }
    }

    @PutMapping("/api/drafts/{draftId}")
    public EmailDraftView updateDraft(@PathVariable UUID draftId,
                                      @Valid @RequestBody UpdateEmailDraftRequest request,
                                      @AuthenticationPrincipal JwtUserPrincipal principal) {
        return emailDraftService.updateDraft(candidateId(principal), draftId,
                        request.recipient(), request.subject(), request.body())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found or already sent"));
    }

    // Called after the candidate sends the email from their own mail app. The server never
    // sends application emails itself: coming from the platform's SES address, replies
    // wouldn't reach the candidate and the email would look like spam.
    @PostMapping("/api/drafts/{draftId}/mark-sent")
    public EmailDraftView markSent(@PathVariable UUID draftId,
                                   @AuthenticationPrincipal JwtUserPrincipal principal) {
        return emailDraftService.markSent(candidateId(principal), draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found or already sent"));
    }

    @DeleteMapping("/api/drafts/{draftId}")
    public Map<String, String> deleteDraft(@PathVariable UUID draftId,
                                           @AuthenticationPrincipal JwtUserPrincipal principal) {
        if (!emailDraftService.deleteDraft(candidateId(principal), draftId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found");
        }
        return Map.of("message", "Draft deleted");
    }

    private static UUID candidateId(JwtUserPrincipal principal) {
        if (principal.candidateProfileId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email drafts need a linked candidate profile");
        }
        return principal.candidateProfileId();
    }
}
