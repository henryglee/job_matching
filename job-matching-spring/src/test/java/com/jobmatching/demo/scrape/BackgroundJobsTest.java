package com.jobmatching.demo.scrape;

import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.JobListingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackgroundJobsTest {

    private CandidateService candidateService;
    private ScrapeOrchestrationService scrapeService;
    private JobListingService jobListingService;
    private BackgroundJobs jobs;

    @BeforeEach
    void setUp() {
        candidateService = mock(CandidateService.class);
        scrapeService = mock(ScrapeOrchestrationService.class);
        jobListingService = mock(JobListingService.class);
        jobs = new BackgroundJobs(candidateService, scrapeService, jobListingService, 30, 7);
    }

    @Test
    void startupScrapesEachCandidateTitleInTurn() {
        when(candidateService.getUniqueJobTitles()).thenReturn(Arrays.asList("Data Analyst", "  ", null, "Nurse"));

        jobs.scrapeOnStartup();

        var order = inOrder(scrapeService);
        order.verify(scrapeService).scrapeForTitle("Data Analyst", ScrapeOrchestrationService.SCRAPE_TTL);
        order.verify(scrapeService).scrapeForTitle("Nurse", ScrapeOrchestrationService.SCRAPE_TTL);
        order.verifyNoMoreInteractions();
    }

    @Test
    void dailyScrapeUsesAShorterSkipWindowThanADay() {
        when(candidateService.getUniqueJobTitles()).thenReturn(List.of("Nurse"));

        jobs.dailyScrape();

        verify(scrapeService).scrapeForTitle("Nurse", BackgroundJobs.DAILY_SKIP_WINDOW);
        assertTrue(BackgroundJobs.DAILY_SKIP_WINDOW.compareTo(ScrapeOrchestrationService.SCRAPE_TTL) < 0);
    }

    @Test
    void startupFallsBackToSoftwareEngineerWithNoTitles() {
        when(candidateService.getUniqueJobTitles()).thenReturn(List.of());

        assertEquals(List.of("software engineer"), jobs.startupTitles());
    }

    @Test
    void startupScrapeNeverThrows() {
        when(candidateService.getUniqueJobTitles()).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(jobs::scrapeOnStartup);
        verify(scrapeService, org.mockito.Mockito.never()).scrapeForTitle(anyString(), any());
    }

    @Test
    void nightlyCleanupDeletesJobsOlderThan30Days() {
        jobs.deleteOldJobs();
        verify(jobListingService).deleteOldJobs(30);
    }

    @Test
    void nightlyCleanupDeletesJobsExpiredOver7DaysAgo() {
        jobs.deleteOldJobs();
        verify(jobListingService).deleteExpiredJobs(7);
    }

    @Test
    void expiredCleanupStillRunsWhenOldJobsCleanupFails() {
        when(jobListingService.deleteOldJobs(anyInt())).thenThrow(new IllegalStateException("db down"));
        jobs.deleteOldJobs();
        verify(jobListingService).deleteExpiredJobs(7);
    }

    @Test
    void nightlyCleanupNeverThrows() {
        when(jobListingService.deleteOldJobs(anyInt())).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(jobs::deleteOldJobs);
    }

    @Test
    void defaultCronRunsDailyAt2am() throws Exception {
        Scheduled scheduled = BackgroundJobs.class.getMethod("deleteOldJobs").getAnnotation(Scheduled.class);
        String expr = scheduled.cron().replace("${app.cleanup.cron:", "").replaceAll("}$", "");

        CronExpression cron = CronExpression.parse(expr);
        assertEquals(LocalDateTime.of(2026, 9, 23, 2, 0), cron.next(LocalDateTime.of(2026, 9, 22, 14, 30)));
        assertEquals(LocalDateTime.of(2026, 9, 22, 2, 0), cron.next(LocalDateTime.of(2026, 9, 22, 1, 59)));
    }
}
