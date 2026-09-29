package com.jobmatching.demo.mcp;

import com.jobmatching.demo.dto.EmailDraftView;
import com.jobmatching.demo.dto.JobApplicationContext;
import com.jobmatching.demo.dto.MatchExplanation;
import com.jobmatching.demo.dto.MatchSummary;
import com.jobmatching.demo.dto.ProfileChange;
import com.jobmatching.demo.dto.WhatIfResult;
import com.jobmatching.demo.entity.EmailDraft;
import com.jobmatching.demo.security.JwtUserPrincipal;
import com.jobmatching.demo.service.DraftingGuidelines;
import com.jobmatching.demo.service.EmailDraftService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.WhatIfService;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * MCP tools for drafting application emails from an AI client (Claude Desktop, Claude Code,
 * ...). The client's model writes the email; these tools only supply context and store the
 * result for the candidate to review on the React Drafts page. There's deliberately no send
 * tool, so the worst a model can do is create a draft.
 *
 * The candidate is always the caller's own linked profile from the JWT, never a tool
 * argument. Otherwise the model, or a prompt injection hidden in a scraped job description,
 * could act on someone else's profile. That works because Spring AI runs sync tools on the
 * servlet request thread in a WebMVC app, where JwtAuthenticationFilter has already set the
 * SecurityContext.
 */
@Component
public class EmailDraftTools {

    private static final int MAX_MATCHES = 25;

    private final EmailDraftService emailDraftService;
    private final JobMatchingService jobMatchingService;
    private final WhatIfService whatIfService;
    private final String emailDraftsUrl;

    public EmailDraftTools(EmailDraftService emailDraftService,
                           JobMatchingService jobMatchingService,
                           WhatIfService whatIfService,
                           @Value("${app.email-drafts-url}") String emailDraftsUrl) {
        this.emailDraftService = emailDraftService;
        this.jobMatchingService = jobMatchingService;
        this.whatIfService = whatIfService;
        this.emailDraftsUrl = emailDraftsUrl;
    }

    // What save/update return. The candidate finishes in their AI assistant, so the app can't
    // redirect them to the draft; reviewUrl lets the assistant hand them a link instead. It
    // opens the Email Drafts page with this draft highlighted (resumed after login if needed).
    public record SavedDraft(EmailDraftView draft, String reviewUrl, String nextStep) {
    }

    @McpTool(name = "list_my_matches",
            description = "List the signed-in candidate's best job matches, best first, with the jobId each other tool needs.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<MatchSummary> listMyMatches(
            @McpToolParam(description = "How many matches to return (1-25, default 10)", required = false) Integer limit) {
        UUID candidateId = currentCandidateId();
        int count = limit == null ? 10 : Math.max(1, Math.min(limit, MAX_MATCHES));
        return jobMatchingService.findMatchingJobs(candidateId, count).stream().map(MatchSummary::of).toList();
    }

    @McpTool(name = "explain_match",
            description = "Explain one job's match percentage: what the overall score measures, which profile and job fields "
                    + "were compared (and which profile fields are empty), and how the skills, title and location "
                    + "checks were reached, including which skills matched and which didn't. Parts marked estimated "
                    + "had nothing to compare and just repeat the overall score.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public MatchExplanation explainMatch(
            @McpToolParam(description = "ID of the job listing (a UUID)") String jobId) {
        UUID candidateId = currentCandidateId();
        return jobMatchingService.explainMatch(candidateId, parseId(jobId, "jobId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No match score for that job yet (it may be missing or not scored yet): " + jobId));
    }

    @McpTool(name = "what_if_score",
            description = "Hypothetical: how a profile change would change match scores, without saving anything. Give the "
                    + "change (skills to add or remove, or a new preferred job title, degree or location) and "
                    + "optionally up to 10 jobIds (default: the candidate's current top 10 matches). Returns the "
                    + "overall percent before and after for each job, plus the skills/title/location checks.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public WhatIfResult whatIfScore(
            @McpToolParam(description = "Up to 10 jobIds (UUIDs); omit for the current top matches", required = false) List<String> jobIds,
            @McpToolParam(description = "Skills to add", required = false) List<String> addSkills,
            @McpToolParam(description = "Skills to remove", required = false) List<String> removeSkills,
            @McpToolParam(description = "New preferred job title", required = false) String preferredJobTitle,
            @McpToolParam(description = "New college degree", required = false) String collegeDegree,
            @McpToolParam(description = "New city", required = false) String locationCity,
            @McpToolParam(description = "New state", required = false) String locationState,
            @McpToolParam(description = "New country", required = false) String locationCountry) {
        UUID candidateId = currentCandidateId();
        List<UUID> ids = jobIds == null ? List.of() : jobIds.stream().map(id -> parseId(id, "jobId")).toList();
        return whatIfService.whatIf(candidateId, ids, new ProfileChange(addSkills, removeSkills,
                preferredJobTitle, collegeDegree, locationCity, locationState, locationCountry));
    }

    @McpTool(name = "get_application_context",
            description = "Get a job posting and the signed-in candidate's profile, for writing an application email. "
                    + "overlappingSkills are the candidate's skills the posting mentions. If contactEmails is empty, "
                    + "write a short cover letter for applyUrl instead of an email.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public JobApplicationContext getApplicationContext(
            @McpToolParam(description = "ID of the job listing (a UUID)") String jobId) {
        UUID candidateId = currentCandidateId();
        return emailDraftService.getApplicationContext(candidateId, parseId(jobId, "jobId"))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    @McpTool(name = "save_email_draft",
            description = "Save an application email as a draft for the candidate to review, edit and send from the "
                    + "Email Drafts page of the job matching app. This does NOT send anything. Only call it once the "
                    + "candidate has approved the text. Returns a reviewUrl: give it to the candidate so they can "
                    + "open the draft and send it.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public SavedDraft saveEmailDraft(
            @McpToolParam(description = "ID of the job listing (a UUID)") String jobId,
            @McpToolParam(description = "Recipient, which must be one of the job's contactEmails. Omit it if there are none.",
                    required = false) String recipient,
            @McpToolParam(description = "Subject line") String subject,
            @McpToolParam(description = "Plain-text body") String body) {
        UUID candidateId = currentCandidateId();
        return saved(emailDraftService.createDraft(candidateId, parseId(jobId, "jobId"), recipient, subject, body));
    }

    @McpTool(name = "update_email_draft",
            description = "Revise the subject and/or body of one of the candidate's unsent drafts. "
                    + "Leave out a field to keep it as it is. Returns the draft's reviewUrl for the candidate.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public SavedDraft updateEmailDraft(
            @McpToolParam(description = "ID of the draft (a UUID)") String draftId,
            @McpToolParam(description = "New subject line", required = false) String subject,
            @McpToolParam(description = "New plain-text body", required = false) String body) {
        UUID candidateId = currentCandidateId();
        return emailDraftService.reviseDraft(candidateId, parseId(draftId, "draftId"), subject, body)
                .map(this::saved)
                .orElseThrow(() -> new IllegalArgumentException("No unsent draft with id " + draftId));
    }

    @McpTool(name = "list_email_drafts",
            description = "List the candidate's unsent application email drafts, newest first.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<EmailDraftView> listEmailDrafts() {
        return emailDraftService.listDrafts(currentCandidateId(), EmailDraft.Status.DRAFT);
    }

    @McpPrompt(name = "draft_application_email",
            description = "Draft an application email for one of my job matches")
    public GetPromptResult draftApplicationEmail(
            @McpArg(name = "jobId", description = "ID of the job listing", required = true) String jobId,
            @McpArg(name = "tone", description = "For example formal, friendly or concise", required = false) String tone) {
        // Same rules as the in-app generator (EmailDraftGenerator), plus the tool steps.
        String instructions = """
                Draft an application email for job %s in a %s tone.
                1. Call get_application_context with this jobId.
                2. Write a subject line and body following these rules:
                %s
                3. If something I ask you to mention isn't in my profile, ask me about it instead
                   of guessing. If contactEmails is empty, leave the recipient out.
                4. Show me the draft and revise it until I approve it, then call save_email_draft
                   and give me the reviewUrl it returns so I can open the draft and send it.
                """.formatted(jobId, tone == null || tone.isBlank() ? "professional" : tone.trim(),
                DraftingGuidelines.RULES.indent(3).stripTrailing());
        return new GetPromptResult("Application email draft",
                List.of(new PromptMessage(Role.USER, new TextContent(instructions))));
    }

    private SavedDraft saved(EmailDraftView draft) {
        String reviewUrl = UriComponentsBuilder.fromUriString(emailDraftsUrl)
                .queryParam("highlight", draft.id())
                .toUriString();
        return new SavedDraft(draft, reviewUrl,
                "Saved as a draft; nothing was sent. Give the candidate this link to review, edit and send it from "
                        + "their own email: " + reviewUrl);
    }

    private static UUID currentCandidateId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new IllegalStateException("Not authenticated");
        }
        if (principal.candidateProfileId() == null) {
            throw new IllegalStateException(
                    "No candidate profile is linked to this account. Log in with the User role on an account that has one.");
        }
        return principal.candidateProfileId();
    }

    private static UUID parseId(String value, String name) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(name + " must be a UUID, got: " + value);
        }
    }
}
