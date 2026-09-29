package com.jobmatching.demo.chat;

import com.jobmatching.demo.dto.DraftProposal;
import com.jobmatching.demo.dto.JobApplicationContext;
import com.jobmatching.demo.dto.MatchExplanation;
import com.jobmatching.demo.dto.MatchSummary;
import com.jobmatching.demo.dto.ProfileChange;
import com.jobmatching.demo.dto.WhatIfResult;
import com.jobmatching.demo.service.EmailDraftService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.WhatIfService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tools for the in-app "Ask about your matches" chat (MatchChatService): the same lookups the
 * MCP server offers (mcp/EmailDraftTools), for candidates who don't use an AI assistant of
 * their own.
 *
 * Nothing here writes anything. propose_email_draft only records a proposal, which the chat
 * shows as a card; saving takes the candidate's click. So instructions hidden in a scraped job
 * description can't make the model change data. The candidate comes from the ToolContext,
 * which MatchChatService fills from the JWT, never from a tool argument.
 */
@Component
public class MatchChatTools {

    static final String CANDIDATE_ID = "candidateId";
    static final String PROPOSALS = "proposals";
    // Optional AtomicInteger counting this reply's tool calls (see MatchChatService).
    static final String CALLS = "toolCalls";
    static final int MAX_TOOL_CALLS_PER_REPLY = 10;

    static final int MAX_DESCRIPTION_CHARS = 6000;
    private static final int MAX_MATCHES = 25;
    private static final int MAX_PROPOSALS_PER_REPLY = 3;
    private static final int MAX_SUBJECT_CHARS = 300;
    private static final int MAX_BODY_CHARS = 20000;

    private final JobMatchingService jobMatchingService;
    private final EmailDraftService emailDraftService;
    private final WhatIfService whatIfService;

    public MatchChatTools(JobMatchingService jobMatchingService, EmailDraftService emailDraftService,
                          WhatIfService whatIfService) {
        this.jobMatchingService = jobMatchingService;
        this.emailDraftService = emailDraftService;
        this.whatIfService = whatIfService;
    }

    @Tool(name = "list_my_matches",
            description = "List the candidate's best job matches, best first, with jobId, title, company, location, "
                    + "match percent and the candidate skills each job matched.")
    public List<MatchSummary> listMyMatches(
            @ToolParam(description = "How many matches to return (1-25, default 10)", required = false) Integer limit,
            ToolContext toolContext) {
        useToolCall(toolContext);
        int count = limit == null ? 10 : Math.max(1, Math.min(limit, MAX_MATCHES));
        return jobMatchingService.findMatchingJobs(candidateId(toolContext), count).stream()
                .map(MatchSummary::of)
                .toList();
    }

    @Tool(name = "get_job_details",
            description = "One job posting (title, company, location, description, contact emails, apply URL) and the "
                    + "candidate's profile, including the candidate skills the posting mentions. Use it to explain a "
                    + "match or before proposing an email.")
    public JobApplicationContext getJobDetails(
            @ToolParam(description = "jobId (a UUID) from list_my_matches") String jobId,
            ToolContext toolContext) {
        useToolCall(toolContext);
        return emailDraftService.getApplicationContext(candidateId(toolContext), parseJobId(jobId))
                .map(context -> context.withDescriptionLimit(MAX_DESCRIPTION_CHARS))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    @Tool(name = "explain_match",
            description = "Explain one job's match percentage: what the overall score measures, which profile and job fields "
                    + "were compared (and which profile fields are empty), and how the skills, title and location "
                    + "checks were reached, including which skills matched and which didn't. Parts marked estimated "
                    + "had nothing to compare and just repeat the overall score.")
    public MatchExplanation explainMatch(
            @ToolParam(description = "jobId (a UUID) from list_my_matches") String jobId,
            ToolContext toolContext) {
        useToolCall(toolContext);
        return jobMatchingService.explainMatch(candidateId(toolContext), parseJobId(jobId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No match score for that job yet (it may be missing or not scored yet): " + jobId));
    }

    @Tool(name = "what_if_score",
            description = "Hypothetical: how a profile change would change match scores, without saving anything. Give the "
                    + "change (skills to add or remove, or a new preferred job title, degree or location) and "
                    + "optionally up to 10 jobIds (default: the candidate's current top 10 matches). Returns the "
                    + "overall percent before and after for each job, plus the skills/title/location checks.")
    public WhatIfResult whatIfScore(
            @ToolParam(description = "Up to 10 jobIds (UUIDs); omit for the current top matches", required = false) List<String> jobIds,
            @ToolParam(description = "Skills to add", required = false) List<String> addSkills,
            @ToolParam(description = "Skills to remove", required = false) List<String> removeSkills,
            @ToolParam(description = "New preferred job title", required = false) String preferredJobTitle,
            @ToolParam(description = "New college degree", required = false) String collegeDegree,
            @ToolParam(description = "New city", required = false) String locationCity,
            @ToolParam(description = "New state", required = false) String locationState,
            @ToolParam(description = "New country", required = false) String locationCountry,
            ToolContext toolContext) {
        useToolCall(toolContext);
        List<UUID> ids = jobIds == null ? List.of() : jobIds.stream().map(MatchChatTools::parseJobId).toList();
        return whatIfService.whatIf(candidateId(toolContext), ids, new ProfileChange(addSkills, removeSkills,
                preferredJobTitle, collegeDegree, locationCity, locationState, locationCountry));
    }

    @Tool(name = "propose_email_draft",
            description = "Show the candidate an application email (or, if the job has no contact email, a cover "
                    + "letter) as a card they can review and save. This does NOT save or send anything: the "
                    + "candidate decides. The recipient is filled in from the job automatically.")
    public String proposeEmailDraft(
            @ToolParam(description = "jobId (a UUID)") String jobId,
            @ToolParam(description = "Subject line") String subject,
            @ToolParam(description = "Plain-text body") String body,
            ToolContext toolContext) {
        useToolCall(toolContext);
        if (subject == null || subject.isBlank() || subject.strip().length() > MAX_SUBJECT_CHARS) {
            throw new IllegalArgumentException("subject is required and must be at most " + MAX_SUBJECT_CHARS + " characters");
        }
        if (body == null || body.isBlank() || body.strip().length() > MAX_BODY_CHARS) {
            throw new IllegalArgumentException("body is required and must be at most " + MAX_BODY_CHARS + " characters");
        }
        List<DraftProposal> proposals = proposals(toolContext);
        if (proposals.size() >= MAX_PROPOSALS_PER_REPLY) {
            throw new IllegalArgumentException("Propose at most " + MAX_PROPOSALS_PER_REPLY + " drafts per reply");
        }
        JobApplicationContext job = emailDraftService.getApplicationContext(candidateId(toolContext), parseJobId(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        String recipient = job.contactEmails().isEmpty() ? null : job.contactEmails().get(0);
        proposals.add(new DraftProposal(job.jobId(), job.title(), job.companyName(), recipient,
                subject.strip(), body.strip()));
        return recipient == null
                ? "Shown to the candidate as a cover letter card with a Save button (the job has no contact email). "
                        + "Don't say it was saved or sent."
                : "Shown to the candidate as a card addressed to " + recipient + ", with a Save button. "
                        + "Don't say it was saved or sent.";
    }

    // The model decides how many tools to call, and every round is another paid model call. Past the
    // budget a tool refuses, which tells the model to answer with what it has.
    private static void useToolCall(ToolContext toolContext) {
        if (toolContext.getContext().get(CALLS) instanceof AtomicInteger calls
                && calls.incrementAndGet() > MAX_TOOL_CALLS_PER_REPLY) {
            throw new IllegalStateException("Tool call limit reached for this message; answer with what you already have.");
        }
    }

    private static UUID candidateId(ToolContext toolContext) {
        Object id = toolContext.getContext().get(CANDIDATE_ID);
        if (!(id instanceof UUID candidateId)) {
            throw new IllegalStateException("No candidate in the tool context");
        }
        return candidateId;
    }

    @SuppressWarnings("unchecked")
    private static List<DraftProposal> proposals(ToolContext toolContext) {
        Object proposals = toolContext.getContext().get(PROPOSALS);
        if (!(proposals instanceof List<?>)) {
            throw new IllegalStateException("No proposal list in the tool context");
        }
        return (List<DraftProposal>) proposals;
    }

    private static UUID parseJobId(String jobId) {
        try {
            return UUID.fromString(jobId.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("jobId must be a UUID from list_my_matches, got: " + jobId);
        }
    }
}
