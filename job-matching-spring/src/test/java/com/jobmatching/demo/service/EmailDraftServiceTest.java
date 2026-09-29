package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.EmailDraftView;
import com.jobmatching.demo.dto.JobApplicationContext;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.EmailDraft;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.EmailDraftRepository;
import com.jobmatching.demo.repository.JobListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailDraftServiceTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    private EmailDraftRepository draftRepository;
    private CandidateInfoRepository candidateRepository;
    private JobListingRepository jobRepository;
    private EmailDraftService service;
    private JobListing job;

    @BeforeEach
    void setUp() {
        draftRepository = mock(EmailDraftRepository.class);
        candidateRepository = mock(CandidateInfoRepository.class);
        jobRepository = mock(JobListingRepository.class);
        service = new EmailDraftService(draftRepository, candidateRepository, jobRepository);

        job = new JobListing();
        job.setId(JOB_ID);
        job.setTitle("Backend Engineer");
        job.setEmails("Jobs@Acme.example, hr@acme.example");
        job.setSkills("Java, PostgreSQL");
        job.setDescription("We use Spring Boot and C++ tooling. JavaScript is a plus.");
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(draftRepository.save(any(EmailDraft.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createDraftRefusesOnceTheCandidateHasTheMaximumNumberOfDrafts() {
        when(draftRepository.countByCandidateId(CANDIDATE_ID)).thenReturn((long) EmailDraftService.MAX_DRAFTS_PER_CANDIDATE);

        assertThrows(IllegalArgumentException.class, () -> service.createDraft(CANDIDATE_ID, JOB_ID, null, "Hi", "Body"));
        org.mockito.Mockito.verify(draftRepository, org.mockito.Mockito.never()).save(any(EmailDraft.class));

        when(draftRepository.countByCandidateId(CANDIDATE_ID)).thenReturn(EmailDraftService.MAX_DRAFTS_PER_CANDIDATE - 1L);
        service.createDraft(CANDIDATE_ID, JOB_ID, null, "Hi", "Body");
        org.mockito.Mockito.verify(draftRepository).save(any(EmailDraft.class));
    }

    @Test
    void createDraftAcceptsOneOfTheJobsContactEmails() {
        EmailDraftView view = service.createDraft(CANDIDATE_ID, JOB_ID, " jobs@acme.example ", "Hi", "Body");

        ArgumentCaptor<EmailDraft> saved = ArgumentCaptor.forClass(EmailDraft.class);
        verify(draftRepository).save(saved.capture());
        assertEquals(CANDIDATE_ID, saved.getValue().getCandidateId());
        assertEquals("jobs@acme.example", saved.getValue().getRecipient());
        assertEquals(EmailDraft.Status.DRAFT, saved.getValue().getStatus());
        assertEquals("Backend Engineer", view.jobTitle());
    }

    @Test
    void createDraftRejectsAnInventedRecipient() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createDraft(CANDIDATE_ID, JOB_ID, "ceo@acme.example", "Hi", "Body"));
        verify(draftRepository, never()).save(any());
    }

    @Test
    void createDraftWithoutRecipientIsACoverLetter() {
        service.createDraft(CANDIDATE_ID, JOB_ID, "  ", "Hi", "Body");

        ArgumentCaptor<EmailDraft> saved = ArgumentCaptor.forClass(EmailDraft.class);
        verify(draftRepository).save(saved.capture());
        assertNull(saved.getValue().getRecipient());
    }

    @Test
    void createDraftRequiresSubjectAndBody() {
        assertThrows(IllegalArgumentException.class, () -> service.createDraft(CANDIDATE_ID, JOB_ID, null, " ", "Body"));
        assertThrows(IllegalArgumentException.class, () -> service.createDraft(CANDIDATE_ID, JOB_ID, null, "Hi", null));
    }

    @Test
    void createDraftForUnknownJobFails() {
        UUID unknown = UUID.randomUUID();
        when(jobRepository.findById(unknown)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.createDraft(CANDIDATE_ID, unknown, null, "Hi", "Body"));
    }

    @Test
    void sentDraftsCannotBeEditedOrResent() {
        EmailDraft sent = draft(EmailDraft.Status.SENT);
        when(draftRepository.findByIdAndCandidateId(sent.getId(), CANDIDATE_ID)).thenReturn(Optional.of(sent));

        assertTrue(service.updateDraft(CANDIDATE_ID, sent.getId(), null, "New", "New").isEmpty());
        assertTrue(service.reviseDraft(CANDIDATE_ID, sent.getId(), "New", null).isEmpty());
        assertTrue(service.markSent(CANDIDATE_ID, sent.getId()).isEmpty());
        assertEquals("Hi", sent.getSubject());
    }

    @Test
    void reviseDraftKeepsFieldsLeftOut() {
        EmailDraft draft = draft(EmailDraft.Status.DRAFT);
        when(draftRepository.findByIdAndCandidateId(draft.getId(), CANDIDATE_ID)).thenReturn(Optional.of(draft));

        service.reviseDraft(CANDIDATE_ID, draft.getId(), null, "Shorter body");

        assertEquals("Hi", draft.getSubject());
        assertEquals("Shorter body", draft.getBody());
    }

    @Test
    void markSentRecordsStatusAndTime() {
        EmailDraft draft = draft(EmailDraft.Status.DRAFT);
        when(draftRepository.findByIdAndCandidateId(draft.getId(), CANDIDATE_ID)).thenReturn(Optional.of(draft));

        EmailDraftView view = service.markSent(CANDIDATE_ID, draft.getId()).orElseThrow();

        assertEquals("SENT", view.status());
        assertNotNull(draft.getSentAt());
    }

    @Test
    void anotherCandidatesDraftIsNotFound() {
        UUID draftId = UUID.randomUUID();
        when(draftRepository.findByIdAndCandidateId(draftId, CANDIDATE_ID)).thenReturn(Optional.empty());

        assertFalse(service.deleteDraft(CANDIDATE_ID, draftId));
        assertTrue(service.updateDraft(CANDIDATE_ID, draftId, null, "Hi", "Body").isEmpty());
        verify(draftRepository, never()).delete(any());
    }

    @Test
    void applicationContextMatchesSkillsAsWholeWords() {
        CandidateInfo candidate = new CandidateInfo();
        candidate.setId(CANDIDATE_ID);
        candidate.setSkills(List.of("Java", "C++", "C", "Spring Boot", "Go", "postgresql"));
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));

        JobApplicationContext context = service.getApplicationContext(CANDIDATE_ID, JOB_ID).orElseThrow();

        // "C" and "Go" only appear inside other words; "Java" also appears inside JavaScript
        // but is listed on its own in the job's skills.
        assertEquals(List.of("Java", "C++", "Spring Boot", "postgresql"), context.overlappingSkills());
        assertEquals(List.of("jobs@acme.example", "hr@acme.example"), context.contactEmails());
    }

    private EmailDraft draft(EmailDraft.Status status) {
        EmailDraft draft = new EmailDraft();
        draft.setId(UUID.randomUUID());
        draft.setCandidateId(CANDIDATE_ID);
        draft.setJobId(JOB_ID);
        draft.setSubject("Hi");
        draft.setBody("Body");
        draft.setStatus(status);
        return draft;
    }
}
