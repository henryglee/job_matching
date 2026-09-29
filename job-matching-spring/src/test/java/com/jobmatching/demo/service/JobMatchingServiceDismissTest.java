package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.AppliedJobView;
import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.entity.CandidateAppliedJob;
import com.jobmatching.demo.entity.CandidateDismissedJob;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.CandidateAppliedJobRepository;
import com.jobmatching.demo.repository.CandidateDismissedJobRepository;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.JobListingRepository;
import com.jobmatching.demo.scrape.JobExpiryService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobMatchingServiceDismissTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    private CandidateInfoRepository candidateRepository;
    private JobListingRepository jobRepository;
    private CandidateDismissedJobRepository dismissedRepository;
    private CandidateAppliedJobRepository appliedRepository;
    private JobMatchingService service;

    @BeforeEach
    void setUp() {
        candidateRepository = mock(CandidateInfoRepository.class);
        jobRepository = mock(JobListingRepository.class);
        dismissedRepository = mock(CandidateDismissedJobRepository.class);
        appliedRepository = mock(CandidateAppliedJobRepository.class);
        service = new JobMatchingService(candidateRepository, jobRepository, dismissedRepository, appliedRepository,
                mock(EmbeddingService.class), mock(JobExpiryService.class));
    }

    @Test
    void dismissRecordsCandidateAndJob() {
        UUID jobId = UUID.randomUUID();
        when(candidateRepository.existsById(CANDIDATE_ID)).thenReturn(true);
        when(jobRepository.existsById(jobId)).thenReturn(true);

        assertTrue(service.dismissJob(CANDIDATE_ID, jobId));

        ArgumentCaptor<CandidateDismissedJob> saved = ArgumentCaptor.forClass(CandidateDismissedJob.class);
        verify(dismissedRepository).save(saved.capture());
        assertEquals(CANDIDATE_ID, saved.getValue().getCandidateId());
        assertEquals(jobId, saved.getValue().getJobId());
    }

    @Test
    void dismissingTwiceDoesNotDuplicate() {
        UUID jobId = UUID.randomUUID();
        when(candidateRepository.existsById(CANDIDATE_ID)).thenReturn(true);
        when(jobRepository.existsById(jobId)).thenReturn(true);
        when(dismissedRepository.existsByCandidateIdAndJobId(CANDIDATE_ID, jobId)).thenReturn(true);

        assertTrue(service.dismissJob(CANDIDATE_ID, jobId));
        verify(dismissedRepository, never()).save(any());
    }

    @Test
    void dismissUnknownJobReturnsFalse() {
        when(candidateRepository.existsById(CANDIDATE_ID)).thenReturn(true);

        assertFalse(service.dismissJob(CANDIDATE_ID, UUID.randomUUID()));
        verify(dismissedRepository, never()).save(any());
    }

    @Test
    void markAppliedRecordsCandidateJobAndItsDetails() {
        JobListing job = job("Backend Engineer");
        job.setCompanyName("Acme");
        job.setLocation("Austin, TX");
        job.setJobUrl("https://board.example/1");
        job.setJobUrlDirect("https://acme.example/careers/1");
        when(candidateRepository.existsById(CANDIDATE_ID)).thenReturn(true);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertTrue(service.markApplied(CANDIDATE_ID, job.getId()));

        ArgumentCaptor<CandidateAppliedJob> saved = ArgumentCaptor.forClass(CandidateAppliedJob.class);
        verify(appliedRepository).save(saved.capture());
        assertEquals(CANDIDATE_ID, saved.getValue().getCandidateId());
        assertEquals(job.getId(), saved.getValue().getJobId());
        assertEquals("Backend Engineer", saved.getValue().getJobTitle());
        assertEquals("Acme", saved.getValue().getCompanyName());
        assertEquals("Austin, TX", saved.getValue().getLocation());
        assertEquals("https://acme.example/careers/1", saved.getValue().getJobUrl());
        verify(dismissedRepository, never()).save(any());
    }

    @Test
    void markingAppliedTwiceDoesNotDuplicate() {
        JobListing job = job("Backend Engineer");
        when(candidateRepository.existsById(CANDIDATE_ID)).thenReturn(true);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(appliedRepository.existsByCandidateIdAndJobId(CANDIDATE_ID, job.getId())).thenReturn(true);

        assertTrue(service.markApplied(CANDIDATE_ID, job.getId()));
        verify(appliedRepository, never()).save(any());
    }

    @Test
    void unmarkAppliedDeletesOnlyTheCandidatesOwnEntry() {
        CandidateAppliedJob applied = new CandidateAppliedJob();
        UUID appliedId = UUID.randomUUID();
        when(appliedRepository.findByIdAndCandidateId(appliedId, CANDIDATE_ID)).thenReturn(Optional.of(applied));

        assertTrue(service.unmarkApplied(CANDIDATE_ID, appliedId));
        verify(appliedRepository).delete(applied);

        assertFalse(service.unmarkApplied(UUID.randomUUID(), appliedId));
    }

    @Test
    void markAppliedUnknownJobReturnsFalse() {
        when(candidateRepository.existsById(CANDIDATE_ID)).thenReturn(true);

        assertFalse(service.markApplied(CANDIDATE_ID, UUID.randomUUID()));
        verify(appliedRepository, never()).save(any());
    }

    @Test
    void updateAppliedStatusSavesItAndWhenItChanged() {
        CandidateAppliedJob applied = new CandidateAppliedJob();
        UUID appliedId = UUID.randomUUID();
        when(appliedRepository.findByIdAndCandidateId(appliedId, CANDIDATE_ID)).thenReturn(Optional.of(applied));

        Optional<AppliedJobView> view = service.updateAppliedStatus(CANDIDATE_ID, appliedId,
                CandidateAppliedJob.Status.INTERVIEWING);

        assertTrue(view.isPresent());
        assertEquals("INTERVIEWING", view.get().status());
        assertNotNull(view.get().statusUpdatedAt());
        verify(appliedRepository).save(applied);
    }

    @Test
    void updateAppliedStatusToTheSameStatusChangesNothing() {
        CandidateAppliedJob applied = new CandidateAppliedJob();
        UUID appliedId = UUID.randomUUID();
        when(appliedRepository.findByIdAndCandidateId(appliedId, CANDIDATE_ID)).thenReturn(Optional.of(applied));

        Optional<AppliedJobView> view = service.updateAppliedStatus(CANDIDATE_ID, appliedId,
                CandidateAppliedJob.Status.APPLIED);

        assertEquals("APPLIED", view.orElseThrow().status());
        assertNull(view.get().statusUpdatedAt());
        verify(appliedRepository, never()).save(any());
    }

    @Test
    void updateAppliedStatusOfAnotherCandidatesEntryIsEmpty() {
        assertTrue(service.updateAppliedStatus(CANDIDATE_ID, UUID.randomUUID(),
                CandidateAppliedJob.Status.OFFER).isEmpty());
        verify(appliedRepository, never()).save(any());
    }

    @Test
    void matchesExcludeDismissedAndAppliedJobs() {
        CandidateInfo candidate = new CandidateInfo();
        candidate.setId(CANDIDATE_ID);
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(candidateRepository.findEmbeddingById(CANDIDATE_ID)).thenReturn(Optional.of("[0.1,0.2]"));
        UUID dismissed = UUID.randomUUID();
        UUID applied = UUID.randomUUID();
        when(dismissedRepository.findJobIdsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(dismissed));
        when(appliedRepository.findJobIdsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(applied));
        JobListing job = job("Fresh");
        when(jobRepository.findTopMatchesExcluding(anyString(), eq(List.of(dismissed, applied)), anyInt()))
                .thenReturn(List.of(match(job.getId(), 0.1)));
        when(jobRepository.findByIdIn(any())).thenReturn(List.of(job));

        List<JobMatchResult> matches = service.findMatchingJobs(CANDIDATE_ID, 10);

        assertEquals(1, matches.size());
        assertEquals("Fresh", matches.get(0).job().getTitle());
    }

    @Test
    void replacementIsTheMatchAtTheVacatedLastPosition() {
        // Client was showing 3 matches and dismissed one; re-matching (dismissals excluded)
        // yields the 2 still shown plus a new third — that third one is the replacement.
        List<JobListing> jobs = List.of(job("Kept A"), job("Kept B"), job("New C"));
        stubMatches(jobs);

        Optional<JobMatchResult> replacement = service.findReplacementMatch(CANDIDATE_ID, 3);

        assertTrue(replacement.isPresent());
        assertEquals("New C", replacement.get().job().getTitle());
    }

    @Test
    void noReplacementWhenMatchesRunOut() {
        stubMatches(List.of(job("Kept A"), job("Kept B")));

        assertTrue(service.findReplacementMatch(CANDIDATE_ID, 3).isEmpty());
    }

    @Test
    void nonPositiveCountHasNoReplacement() {
        assertTrue(service.findReplacementMatch(CANDIDATE_ID, 0).isEmpty());
    }

    private void stubMatches(List<JobListing> jobs) {
        CandidateInfo candidate = new CandidateInfo();
        candidate.setId(CANDIDATE_ID);
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(candidateRepository.findEmbeddingById(CANDIDATE_ID)).thenReturn(Optional.of("[0.1,0.2]"));
        UUID dismissed = UUID.randomUUID();
        when(dismissedRepository.findJobIdsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(dismissed));

        List<JobListingRepository.JobMatch> rows = jobs.stream().map(j -> match(j.getId(), 0.1)).toList();
        when(jobRepository.findTopMatchesExcluding(anyString(), eq(List.of(dismissed)), anyInt())).thenReturn(rows);
        when(jobRepository.findByIdIn(any())).thenReturn(jobs);
    }

    private static JobListing job(String title) {
        JobListing job = new JobListing();
        job.setId(UUID.randomUUID());
        job.setTitle(title);
        return job;
    }

    private static JobListingRepository.JobMatch match(UUID id, double distance) {
        return new JobListingRepository.JobMatch() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public Double getDistance() {
                return distance;
            }
        };
    }
}
