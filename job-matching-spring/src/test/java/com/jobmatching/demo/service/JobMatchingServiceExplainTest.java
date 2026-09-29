package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.MatchExplanation;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.CandidateAppliedJobRepository;
import com.jobmatching.demo.repository.CandidateDismissedJobRepository;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.JobListingRepository;
import com.jobmatching.demo.scrape.JobExpiryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobMatchingServiceExplainTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final String EMBEDDING = "[0.1,0.2]";

    private CandidateInfoRepository candidateRepository;
    private JobListingRepository jobRepository;
    private EmbeddingService embeddingService;
    private JobMatchingService service;

    @BeforeEach
    void setUp() {
        candidateRepository = mock(CandidateInfoRepository.class);
        jobRepository = mock(JobListingRepository.class);
        embeddingService = mock(EmbeddingService.class);
        service = new JobMatchingService(candidateRepository, jobRepository, mock(CandidateDismissedJobRepository.class), mock(CandidateAppliedJobRepository.class), embeddingService, mock(JobExpiryService.class));

        CandidateInfo candidate = new CandidateInfo();
        candidate.setId(CANDIDATE_ID);
        candidate.setSkills(List.of("Java"));
        candidate.setPreferredJobTitle("Software Engineer");
        JobListing job = new JobListing();
        job.setId(JOB_ID);
        job.setTitle("Software Engineer");
        job.setDescription("Java services");
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    }

    @Test
    void explainsAnyJobFromItsDistance() {
        when(candidateRepository.findEmbeddingById(CANDIDATE_ID)).thenReturn(Optional.of(EMBEDDING));
        when(jobRepository.findDistance(EMBEDDING, JOB_ID)).thenReturn(Optional.of(0.198));

        MatchExplanation e = service.explainMatch(CANDIDATE_ID, JOB_ID).orElseThrow();

        assertEquals(80.2, e.overallPercent());
        assertEquals(100.0, e.skills().percent());
        assertEquals(JOB_ID, e.jobId());
    }

    @Test
    void jobWithoutAnEmbeddingHasNoExplanation() {
        when(candidateRepository.findEmbeddingById(CANDIDATE_ID)).thenReturn(Optional.of(EMBEDDING));
        when(jobRepository.findDistance(EMBEDDING, JOB_ID)).thenReturn(Optional.empty());

        assertTrue(service.explainMatch(CANDIDATE_ID, JOB_ID).isEmpty());
    }

    @Test
    void missingCandidateEmbeddingIsGeneratedFirst() {
        when(candidateRepository.findEmbeddingById(CANDIDATE_ID)).thenReturn(Optional.empty(), Optional.of(EMBEDDING));
        when(embeddingService.generateCandidateEmbedding(CANDIDATE_ID)).thenReturn(true);
        when(jobRepository.findDistance(EMBEDDING, JOB_ID)).thenReturn(Optional.of(0.5));

        assertEquals(50.0, service.explainMatch(CANDIDATE_ID, JOB_ID).orElseThrow().overallPercent());
        verify(embeddingService).generateCandidateEmbedding(CANDIDATE_ID);
    }

    @Test
    void unknownJobOrCandidateHasNoExplanation() {
        UUID unknown = UUID.randomUUID();
        when(jobRepository.findById(unknown)).thenReturn(Optional.empty());
        when(candidateRepository.findById(unknown)).thenReturn(Optional.empty());

        assertTrue(service.explainMatch(CANDIDATE_ID, unknown).isEmpty());
        assertTrue(service.explainMatch(unknown, JOB_ID).isEmpty());
        verify(jobRepository, never()).findDistance(any(), any());
    }
}
