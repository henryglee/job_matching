package com.jobmatching.demo.scrape;

import com.jobmatching.demo.dto.ScrapeJobsRequest;
import com.jobmatching.demo.service.EmbeddingService;
import com.jobmatching.demo.service.JobListingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.jobmatching.demo.scrape.ScrapeOrchestrationService.TitleScrapeOutcome.FAILED;
import static com.jobmatching.demo.scrape.ScrapeOrchestrationService.TitleScrapeOutcome.SCRAPED;
import static com.jobmatching.demo.scrape.ScrapeOrchestrationService.TitleScrapeOutcome.SKIPPED_IN_PROGRESS;
import static com.jobmatching.demo.scrape.ScrapeOrchestrationService.TitleScrapeOutcome.SKIPPED_RECENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScrapeForTitleTest {

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-22T12:00:00Z"));
    private JobScrapeClient client;
    private ScrapeStateStore stateStore;
    private ScrapeOrchestrationService service;

    @BeforeEach
    void setUp() {
        client = mock(JobScrapeClient.class);
        when(client.scrape(any())).thenReturn(List.of());
        stateStore = new ScrapeStateStore(dir.resolve("state.json").toString(), JsonMapper.builder().build());
        service = new ScrapeOrchestrationService(client, mock(JobListingService.class), mock(EmbeddingService.class), stateStore, clock);
    }

    @Test
    void sameTermIsNotScrapedAgainWithin24Hours() {
        assertEquals(SCRAPED, service.scrapeForTitle("Software Engineer"));
        clock.advance(Duration.ofHours(23));
        assertEquals(SKIPPED_RECENT, service.scrapeForTitle("software engineer"));
        verify(client, times(1)).scrape(any());

        clock.advance(Duration.ofHours(2));
        assertEquals(SCRAPED, service.scrapeForTitle("software engineer"));
        verify(client, times(2)).scrape(any());
    }

    @Test
    void emptyResultStillStartsTheWindow() {
        // Like app.py: "no jobs found" is recorded, so it isn't retried every time.
        assertEquals(SCRAPED, service.scrapeForTitle("rare title"));
        assertTrue(stateStore.lastScraped("rare title").isPresent());
    }

    @Test
    void failureIsNotRecordedSoItRetries() {
        when(client.scrape(any())).thenThrow(new IllegalStateException("scrape service down"));

        assertEquals(FAILED, service.scrapeForTitle("data analyst"));
        assertTrue(stateStore.lastScraped("data analyst").isEmpty());
        assertEquals(FAILED, service.scrapeForTitle("data analyst"));
        verify(client, times(2)).scrape(any());
    }

    @Test
    void concurrentScrapeOfSameTermIsSkipped() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.scrape(any())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        CompletableFuture<ScrapeOrchestrationService.TitleScrapeOutcome> first =
                CompletableFuture.supplyAsync(() -> service.scrapeForTitle("devops"));
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertEquals(SKIPPED_IN_PROGRESS, service.scrapeForTitle("DevOps"));
        release.countDown();
        assertEquals(SCRAPED, first.get(5, TimeUnit.SECONDS));
    }

    @Test
    void usesDefaultSitesAndLocationForTheTerm() {
        service.scrapeForTitle("nurse");
        verify(client).scrape(new ScrapeJobsRequest(null, "nurse", null, null));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
