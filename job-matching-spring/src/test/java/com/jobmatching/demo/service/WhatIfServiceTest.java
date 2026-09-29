package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.dto.ProfileChange;
import com.jobmatching.demo.dto.WhatIfResult;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.JobListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatIfServiceTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final String NOW_VECTOR = "[1,0]";
    private static final String CHANGED_VECTOR = "[0,1]";

    private CandidateInfoRepository candidateRepository;
    private JobListingRepository jobRepository;
    private JobMatchingService jobMatchingService;
    private EmbeddingService embeddingService;
    private WhatIfLimiter limiter;
    private AiUsageMonitor usage;
    private WhatIfService service;
    private CandidateInfo candidate;

    @BeforeEach
    void setUp() {
        candidateRepository = mock(CandidateInfoRepository.class);
        jobRepository = mock(JobListingRepository.class);
        jobMatchingService = mock(JobMatchingService.class);
        embeddingService = mock(EmbeddingService.class);
        limiter = mock(WhatIfLimiter.class);
        usage = mock(AiUsageMonitor.class);
        service = new WhatIfService(candidateRepository, jobRepository, jobMatchingService, embeddingService, limiter, usage);

        candidate = new CandidateInfo();
        candidate.setId(CANDIDATE_ID);
        candidate.setCandidateName("Sam Lee");
        candidate.setPreferredJobTitle("Software Engineer");
        candidate.setSkills(new ArrayList<>(List.of("SQL")));
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));

        JobListing job = new JobListing();
        job.setId(JOB_ID);
        job.setTitle("Backend Engineer");
        job.setDescription("Java and SQL services");
        when(jobRepository.findByIdIn(any())).thenReturn(List.of(job));

        when(limiter.tryAcquire(CANDIDATE_ID)).thenReturn(true);
        when(embeddingService.buildCandidateProfileText(any())).thenCallRealMethod();
        when(embeddingService.embedAsVectorLiterals(anyList())).thenReturn(List.of(NOW_VECTOR, CHANGED_VECTOR));
        when(jobRepository.findDistances(eq(NOW_VECTOR), any())).thenReturn(List.of(distance(JOB_ID, 0.25)));
        when(jobRepository.findDistances(eq(CHANGED_VECTOR), any())).thenReturn(List.of(distance(JOB_ID, 0.21)));
    }

    @Test
    void scoresAChangedCopyAgainstTheCurrentProfile() {
        WhatIfResult result = service.whatIf(CANDIDATE_ID, List.of(JOB_ID), change(List.of("Java"), null, null));

        assertEquals(List.of("Added skill: Java"), result.changesApplied());
        assertTrue(result.note().startsWith("Hypothetical: nothing was saved"));
        WhatIfResult.JobWhatIf job = result.jobs().get(0);
        assertEquals(75.0, job.overallBefore());
        assertEquals(79.0, job.overallAfter());
        assertEquals(4.0, job.overallChange());
        assertEquals(100.0, job.before().skills(), "SQL of [SQL]");
        assertEquals(100.0, job.after().skills(), "SQL and Java of [SQL, Java]");
        assertEquals(List.of("SQL", "Java"), job.after().matchedSkills());

        // Both profiles are embedded fresh, in one call; the hypothetical text has the new skill.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(embeddingService).embedAsVectorLiterals(texts.capture());
        assertTrue(texts.getValue().get(0).contains("Skills: SQL") && !texts.getValue().get(0).contains("Java"));
        assertTrue(texts.getValue().get(1).contains("Skills: SQL, Java"));
        verify(usage).record(CANDIDATE_ID, AiUsageEvent.WHAT_IF);
    }

    @Test
    void theSavedProfileIsNeverChanged() {
        service.whatIf(CANDIDATE_ID, List.of(JOB_ID), change(List.of("Java"), List.of("SQL"), "Data Engineer"));

        assertEquals(List.of("SQL"), candidate.getSkills());
        assertEquals("Software Engineer", candidate.getPreferredJobTitle());
        verify(candidateRepository, never()).save(any());
        verify(candidateRepository, never()).updateEmbedding(any(), any());
    }

    @Test
    void describesEachRealChangeAndIgnoresNoOps() {
        List<String> applied = new ArrayList<>();
        CandidateInfo changed = WhatIfService.applyChange(candidate,
                new ProfileChange(List.of("java", "SQL", " "), List.of("sql", "Rust"), "Software Engineer", "", "Austin", null, null),
                applied);

        assertEquals(List.of("Removed skill: sql", "Added skill: java", "Added skill: SQL", "City: (none) → Austin"), applied,
                "removing SQL then re-adding it counts both; unchanged title and blank-on-empty degree are no-ops");
        assertEquals(List.of("java", "SQL"), changed.getSkills());
    }

    @Test
    void defaultsToTheCurrentTopMatches() {
        JobListing top = new JobListing();
        top.setId(JOB_ID);
        when(jobMatchingService.findMatchingJobs(CANDIDATE_ID, WhatIfService.MAX_JOBS))
                .thenReturn(List.of(new JobMatchResult(top, 0.75, null)));

        WhatIfResult result = service.whatIf(CANDIDATE_ID, null, change(List.of("Java"), null, null));

        assertEquals(1, result.jobs().size());
        verify(jobRepository).findDistances(eq(NOW_VECTOR), eq(List.of(JOB_ID)));
    }

    @Test
    void rejectsEmptyChangesAndTooManyJobsBeforeSpendingAnything() {
        assertThrows(IllegalArgumentException.class,
                () -> service.whatIf(CANDIDATE_ID, List.of(JOB_ID), change(List.of(), null, "Software Engineer")));
        List<UUID> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) eleven.add(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> service.whatIf(CANDIDATE_ID, eleven, change(List.of("Java"), null, null)));
        verify(embeddingService, never()).embedAsVectorLiterals(anyList());
        verify(limiter, never()).tryAcquire(any());
    }

    @Test
    void overTheLimitOrEmbeddingFailureIsReported() {
        when(limiter.tryAcquire(CANDIDATE_ID)).thenReturn(false);
        assertThrows(WhatIfService.WhatIfLimitException.class,
                () -> service.whatIf(CANDIDATE_ID, List.of(JOB_ID), change(List.of("Java"), null, null)));
        verify(usage).record(CANDIDATE_ID, AiUsageEvent.WHAT_IF_LIMITED);

        when(limiter.tryAcquire(CANDIDATE_ID)).thenReturn(true);
        when(embeddingService.embedAsVectorLiterals(anyList())).thenThrow(new RuntimeException("OpenAI down"));
        assertThrows(WhatIfService.WhatIfFailedException.class,
                () -> service.whatIf(CANDIDATE_ID, List.of(JOB_ID), change(List.of("Java"), null, null)));
    }

    @Test
    void jobWithoutAnEmbeddingIsLeftOut() {
        when(jobRepository.findDistances(eq(CHANGED_VECTOR), any())).thenReturn(List.of());
        assertFalse(service.whatIf(CANDIDATE_ID, List.of(JOB_ID), change(List.of("Java"), null, null)).jobs().iterator().hasNext());
    }

    private static ProfileChange change(List<String> add, List<String> remove, String title) {
        return new ProfileChange(add, remove, title, null, null, null, null);
    }

    private static JobListingRepository.JobMatch distance(UUID id, double distance) {
        return new JobListingRepository.JobMatch() {
            public UUID getId() {
                return id;
            }

            public Double getDistance() {
                return distance;
            }
        };
    }
}
