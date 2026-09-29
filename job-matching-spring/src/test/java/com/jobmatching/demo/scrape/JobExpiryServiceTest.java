package com.jobmatching.demo.scrape;

import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.JobListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private JobScrapeClient client;
    private JobListingRepository repository;
    private MutableClock clock;
    private JobExpiryService service;

    @BeforeEach
    void setUp() {
        client = mock(JobScrapeClient.class);
        repository = mock(JobListingRepository.class);
        clock = new MutableClock(NOW);
        service = new JobExpiryService(client, repository, clock);
    }

    @Test
    void marksTheIndeedJobsIndeedReportsExpired() {
        JobListing live = indeedJob("aaa111");
        JobListing expired = indeedJob("bbb222");
        when(client.findExpiredIndeedJobKeys(anyCollection())).thenReturn(List.of("bbb222"));

        assertEquals(Set.of(expired.getId()), service.markExpired(List.of(live, expired)));

        verify(client).findExpiredIndeedJobKeys(Set.of("aaa111", "bbb222"));
        verify(repository).markExpired(eq(Set.of(expired.getId())), any());
    }

    @Test
    void onlyIndeedJobsAreChecked() {
        JobListing google = new JobListing();
        google.setId(UUID.randomUUID());
        google.setSite("google");
        google.setJobUrl("https://www.google.com/search?jk=zzz");

        assertTrue(service.markExpired(List.of(google)).isEmpty());
        verify(client, never()).findExpiredIndeedJobKeys(anyCollection());
    }

    @Test
    void aJobConfirmedLiveIsNotRecheckedWithinTheHour() {
        JobListing job = indeedJob("aaa111");
        when(client.findExpiredIndeedJobKeys(anyCollection())).thenReturn(List.of());

        service.markExpired(List.of(job));
        clock.now = NOW.plus(JobExpiryService.RECHECK_AFTER).minusSeconds(1);
        service.markExpired(List.of(job));
        verify(client, times(1)).findExpiredIndeedJobKeys(anyCollection());

        clock.now = NOW.plus(JobExpiryService.RECHECK_AFTER);
        service.markExpired(List.of(job));
        verify(client, times(2)).findExpiredIndeedJobKeys(anyCollection());
    }

    @Test
    void failsOpenWhenIndeedCannotBeAsked() {
        when(client.findExpiredIndeedJobKeys(anyCollection())).thenThrow(new IllegalStateException("502"));

        assertTrue(service.markExpired(List.of(indeedJob("aaa111"))).isEmpty());
        verify(repository, never()).markExpired(anyCollection(), any());
    }

    @Test
    void readsTheJobKeyFromIndeedUrls() {
        assertEquals(Optional.of("5b59db61290564de"),
                JobExpiryService.indeedJobKey("https://www.indeed.com/viewjob?jk=5b59db61290564de"));
        assertEquals(Optional.of("abc"), JobExpiryService.indeedJobKey("https://ca.indeed.com/viewjob?from=x&jk=abc"));
        assertTrue(JobExpiryService.indeedJobKey("https://www.indeed.com/cmp/acme").isEmpty());
        assertTrue(JobExpiryService.indeedJobKey(null).isEmpty());
    }

    private static JobListing indeedJob(String key) {
        JobListing job = new JobListing();
        job.setId(UUID.randomUUID());
        job.setSite("indeed");
        job.setJobUrl("https://www.indeed.com/viewjob?jk=" + key);
        return job;
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
