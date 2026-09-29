package com.jobmatching.demo.chat;

import com.jobmatching.demo.dto.DraftProposal;
import com.jobmatching.demo.dto.JobApplicationContext;
import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.dto.MatchBreakdown;
import com.jobmatching.demo.dto.MatchExplanation;
import com.jobmatching.demo.dto.MatchSummary;
import com.jobmatching.demo.dto.ProfileChange;
import com.jobmatching.demo.dto.WhatIfResult;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.service.EmailDraftService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.WhatIfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchChatToolsTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    private JobMatchingService jobMatchingService;
    private EmailDraftService emailDraftService;
    private WhatIfService whatIfService;
    private MatchChatTools tools;
    private List<DraftProposal> proposals;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        jobMatchingService = mock(JobMatchingService.class);
        emailDraftService = mock(EmailDraftService.class);
        whatIfService = mock(WhatIfService.class);
        tools = new MatchChatTools(jobMatchingService, emailDraftService, whatIfService);
        proposals = new ArrayList<>();
        context = new ToolContext(Map.of(MatchChatTools.CANDIDATE_ID, CANDIDATE_ID, MatchChatTools.PROPOSALS, proposals));
    }

    @Test
    void listsTheContextCandidatesMatchesWithAClampedLimit() {
        JobListing job = new JobListing();
        job.setId(JOB_ID);
        job.setTitle("Backend Engineer");
        when(jobMatchingService.findMatchingJobs(CANDIDATE_ID, 25)).thenReturn(List.of(
                new JobMatchResult(job, 0.8234, new MatchBreakdown(82, 90, 70, 100, List.of("Java"), false, false, false))));

        List<MatchSummary> matches = tools.listMyMatches(500, context);

        assertEquals(1, matches.size());
        assertEquals(JOB_ID, matches.get(0).jobId());
        assertEquals(82.3, matches.get(0).matchPercent());
        verify(jobMatchingService).findMatchingJobs(CANDIDATE_ID, 25);
    }

    @Test
    void jobDetailsAreForTheContextCandidateWithTheDescriptionCut() {
        givenJob(List.of("jobs@acme.example"), "x".repeat(MatchChatTools.MAX_DESCRIPTION_CHARS + 500));

        JobApplicationContext details = tools.getJobDetails(JOB_ID.toString(), context);

        assertTrue(details.description().length() < MatchChatTools.MAX_DESCRIPTION_CHARS + 10);
        verify(emailDraftService).getApplicationContext(CANDIDATE_ID, JOB_ID);
    }

    @Test
    void explainMatchIsForTheContextCandidate() {
        MatchExplanation explanation = new MatchExplanation(JOB_ID, "Backend Engineer", "Acme", 80.2, "how", List.of(),
                List.of("Skills"), List.of("Title"),
                new MatchExplanation.Part(80.2, true, "The profile lists no skills", List.of(), List.of()),
                new MatchExplanation.Part(100, false, "2 of 2 words", List.of("backend", "engineer"), List.of()),
                new MatchExplanation.Part(80.2, true, "no location", List.of(), List.of()));
        when(jobMatchingService.explainMatch(CANDIDATE_ID, JOB_ID)).thenReturn(Optional.of(explanation));

        assertEquals(explanation, tools.explainMatch(JOB_ID.toString(), context));

        UUID unscored = UUID.randomUUID();
        when(jobMatchingService.explainMatch(CANDIDATE_ID, unscored)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> tools.explainMatch(unscored.toString(), context));
    }

    @Test
    void whatIfIsForTheContextCandidateWithParsedJobIds() {
        WhatIfResult result = new WhatIfResult(List.of("Added skill: Java"), "note", List.of());
        when(whatIfService.whatIf(any(), any(), any())).thenReturn(result);

        assertEquals(result, tools.whatIfScore(List.of(JOB_ID.toString()), List.of("Java"), null, null, null, null, null, null, context));

        verify(whatIfService).whatIf(CANDIDATE_ID, List.of(JOB_ID),
                new ProfileChange(List.of("Java"), null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> tools.whatIfScore(List.of("not-a-uuid"), List.of("Java"), null, null, null, null, null, null, context));
    }

    @Test
    void proposingRecordsACardAndSavesNothing() {
        givenJob(List.of("jobs@acme.example", "hr@acme.example"), "Build APIs.");

        String result = tools.proposeEmailDraft(JOB_ID.toString(), " Application ", " Hi Acme ", context);

        assertEquals(1, proposals.size());
        DraftProposal proposal = proposals.get(0);
        assertEquals(JOB_ID, proposal.jobId());
        assertEquals("jobs@acme.example", proposal.recipient(), "recipient comes from the job, not the model");
        assertEquals("Application", proposal.subject());
        assertEquals("Hi Acme", proposal.body());
        assertTrue(result.contains("Don't say it was saved or sent"));
        verify(emailDraftService, never()).createDraft(any(), any(), any(), any(), any());
    }

    @Test
    void jobWithoutContactEmailBecomesACoverLetterCard() {
        givenJob(List.of(), "Build APIs.");

        String result = tools.proposeEmailDraft(JOB_ID.toString(), "Cover letter", "Dear hiring manager", context);

        assertNull(proposals.get(0).recipient());
        assertTrue(result.contains("cover letter"));
    }

    @Test
    void badInputIsReportedBackToTheModel() {
        givenJob(List.of(), "Build APIs.");
        when(emailDraftService.getApplicationContext(any(), any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> tools.getJobDetails("not-a-uuid", context));
        assertThrows(IllegalArgumentException.class, () -> tools.getJobDetails(UUID.randomUUID().toString(), context));
        assertThrows(IllegalArgumentException.class, () -> tools.proposeEmailDraft(JOB_ID.toString(), " ", "body", context));
        assertTrue(proposals.isEmpty());
    }

    @Test
    void atMostThreeProposalsPerReply() {
        givenJob(List.of(), "Build APIs.");
        for (int i = 0; i < 3; i++) {
            tools.proposeEmailDraft(JOB_ID.toString(), "s" + i, "b" + i, context);
        }
        assertThrows(IllegalArgumentException.class, () -> tools.proposeEmailDraft(JOB_ID.toString(), "s", "b", context));
        assertEquals(3, proposals.size());
    }

    @Test
    void refusesToRunWithoutACandidateInTheContext() {
        ToolContext empty = new ToolContext(Map.of());
        assertThrows(IllegalStateException.class, () -> tools.listMyMatches(5, empty));
        verify(jobMatchingService, never()).findMatchingJobs(any(), anyInt());
    }

    private void givenJob(List<String> contactEmails, String description) {
        when(emailDraftService.getApplicationContext(CANDIDATE_ID, JOB_ID)).thenReturn(Optional.of(new JobApplicationContext(
                JOB_ID, "Backend Engineer", "Acme", "Austin, TX", description, null, contactEmails,
                "https://acme.example/jobs/1", "Sam Lee", "BS Computer Science", "Backend Engineer",
                List.of("Java"), List.of("Java"))));
    }
}
