package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.JobMatchResult;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobMatchingServiceExpiryTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    private JobListingRepository jobRepository;
    private JobExpiryService expiryService;
    private JobMatchingService service;

    @BeforeEach
    void setUp() {
        CandidateInfoRepository candidateRepository = mock(CandidateInfoRepository.class);
        jobRepository = mock(JobListingRepository.class);
        expiryService = mock(JobExpiryService.class);
        service = new JobMatchingService(candidateRepository, jobRepository,
                mock(CandidateDismissedJobRepository.class), mock(CandidateAppliedJobRepository.class),
                mock(EmbeddingService.class), expiryService);

        CandidateInfo candidate = new CandidateInfo();
        candidate.setId(CANDIDATE_ID);
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(candidateRepository.findEmbeddingById(CANDIDATE_ID)).thenReturn(Optional.of("[0.1,0.2]"));
    }

    @Test
    void expiredMatchesAreReplacedByTheNextBest() {
        JobListing a = job("A");
        JobListing expired = job("Expired");
        JobListing c = job("C");
        // The first query returns the expired job; once it's marked, the query moves C up.
        when(jobRepository.findTopMatches(anyString(), anyInt()))
                .thenReturn(List.of(match(a), match(expired)), List.of(match(a), match(c)));
        when(jobRepository.findByIdIn(any())).thenReturn(List.of(a, expired), List.of(a, c));
        when(expiryService.markExpired(any())).thenReturn(Set.of(expired.getId()), Set.of());

        List<JobMatchResult> results = service.findMatchingJobs(CANDIDATE_ID, 2);

        assertEquals(List.of("A", "C"), results.stream().map(r -> r.job().getTitle()).toList());
    }

    @Test
    void stopsAfterBoundedPassesAndStillLeavesExpiredJobsOut() {
        JobListing a = job("A");
        JobListing expired = job("Expired");
        when(jobRepository.findTopMatches(anyString(), anyInt())).thenReturn(List.of(match(a), match(expired)));
        when(jobRepository.findByIdIn(any())).thenReturn(List.of(a, expired));
        when(expiryService.markExpired(any())).thenReturn(Set.of(expired.getId()));

        List<JobMatchResult> results = service.findMatchingJobs(CANDIDATE_ID, 2);

        assertEquals(List.of("A"), results.stream().map(r -> r.job().getTitle()).toList());
        verify(jobRepository, times(JobMatchingService.MAX_EXPIRY_PASSES)).findTopMatches(anyString(), anyInt());
    }

    private static JobListing job(String title) {
        JobListing job = new JobListing();
        job.setId(UUID.randomUUID());
        job.setTitle(title);
        return job;
    }

    private static JobListingRepository.JobMatch match(JobListing job) {
        return new JobListingRepository.JobMatch() {
            @Override
            public UUID getId() {
                return job.getId();
            }

            @Override
            public Double getDistance() {
                return 0.1;
            }
        };
    }
}
