package com.jobmatching.demo.service;

import com.jobmatching.demo.repository.JobListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobListingServiceTest {

    private JobListingRepository repository;
    private JobListingService service;

    @BeforeEach
    void setUp() {
        repository = mock(JobListingRepository.class);
        service = new JobListingService(repository);
    }

    @Test
    void oneUnsavableJobDoesntLoseTheRestOfTheScrape() {
        var good1 = job("https://x/1");
        var bad = job("https://x/2");
        var good2 = job("https://x/3");
        when(repository.save(bad)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("value too long"));

        assertEquals(2, service.saveJobs(java.util.List.of(good1, bad, good2)));

        verify(repository).save(good1);
        verify(repository).save(good2);
    }

    private static com.jobmatching.demo.entity.JobListing job(String url) {
        var j = new com.jobmatching.demo.entity.JobListing();
        j.setTitle("Engineer");
        j.setJobUrl(url);
        j.setSite("indeed");
        return j;
    }

    @Test
    void listingsLeaveOutExpiredJobs() {
        service.getJobs(null, Pageable.unpaged());
        verify(repository).findUnexpired(Pageable.unpaged());

        service.getJobs("indeed", Pageable.unpaged());
        verify(repository).findUnexpiredBySite("indeed", Pageable.unpaged());
    }

    @Test
    void oldJobsCleanupRemovesDismissalsBeforeJobs() {
        when(repository.deleteByDateScrapedBefore(any())).thenReturn(12L);

        assertEquals(12L, service.deleteOldJobs(30));

        var order = inOrder(repository);
        order.verify(repository).deleteDismissalsOfJobsScrapedBefore(any());
        order.verify(repository).deleteByDateScrapedBefore(any());
    }

    @Test
    void expiredCleanupRemovesDismissalsBeforeJobs() {
        when(repository.deleteExpiredJobsWithoutDrafts(any())).thenReturn(4);
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);

        assertEquals(4, service.deleteExpiredJobs(7));

        var order = inOrder(repository);
        order.verify(repository).deleteDismissalsOfExpiredJobsWithoutDrafts(any());
        order.verify(repository).deleteExpiredJobsWithoutDrafts(org.mockito.ArgumentMatchers.argThat(cutoff ->
                !cutoff.isBefore(before) && cutoff.isBefore(before.plusMinutes(1))));
    }
}
