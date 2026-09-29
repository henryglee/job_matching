package com.jobmatching.demo.scrape;

import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.JobListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ports app.py's _start_background_jobs: a one-off scrape at startup for every candidate's
 * preferred job title, and a daily cleanup of old job listings (APScheduler cron, 2 AM).
 * Unlike app.py, the title scrape also re-runs daily, so matches keep getting fresh listings
 * to replace ones that expire (see JobExpiryService) without waiting for a restart.
 *
 * Disable with app.background-jobs.enabled=false (e.g. on extra instances, since each
 * instance would otherwise run both — under gunicorn app.py ran them once per worker).
 */
@Component
@ConditionalOnProperty(name = "app.background-jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobs {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);
    private static final String FALLBACK_TITLE = "software engineer";
    // Skip window for the daily scrape: short enough that yesterday's run never counts (see
    // ScrapeOrchestrationService.scrapeForTitle), long enough to skip a title a new candidate
    // triggered this evening.
    static final Duration DAILY_SKIP_WINDOW = Duration.ofHours(12);

    private final CandidateService candidateService;
    private final ScrapeOrchestrationService scrapeOrchestrationService;
    private final JobListingService jobListingService;
    private final int cleanupDaysOld;
    private final int cleanupExpiredDaysOld;

    public BackgroundJobs(CandidateService candidateService,
                          ScrapeOrchestrationService scrapeOrchestrationService,
                          JobListingService jobListingService,
                          @Value("${app.cleanup.days-old:30}") int cleanupDaysOld,
                          @Value("${app.cleanup.expired-days-old:7}") int cleanupExpiredDaysOld) {
        this.candidateService = candidateService;
        this.scrapeOrchestrationService = scrapeOrchestrationService;
        this.jobListingService = jobListingService;
        this.cleanupDaysOld = cleanupDaysOld;
        this.cleanupExpiredDaysOld = cleanupExpiredDaysOld;
    }

    /**
     * Ports auto_scrape_jobs: scrape each distinct preferred job title in turn (or
     * "software engineer" if no candidate has one). Runs off the startup thread so the app
     * serves requests meanwhile; titles scraped in the last 24 hours are skipped, so a
     * restart doesn't re-scrape everything.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void scrapeOnStartup() {
        scrapeAllTitles("Startup", ScrapeOrchestrationService.SCRAPE_TTL);
    }

    // The same scrape, daily (by default an hour after the nightly cleanup).
    @Scheduled(cron = "${app.scrape.refresh-cron:0 0 3 * * *}", zone = "${app.cleanup.zone:}")
    public void dailyScrape() {
        scrapeAllTitles("Daily", DAILY_SKIP_WINDOW);
    }

    private void scrapeAllTitles(String which, Duration skipIfScrapedWithin) {
        try {
            List<String> titles = startupTitles();
            log.info("{} scrape for titles: {}", which, titles);
            Map<ScrapeOrchestrationService.TitleScrapeOutcome, Integer> counts = new LinkedHashMap<>();
            for (String title : titles) {
                counts.merge(scrapeOrchestrationService.scrapeForTitle(title, skipIfScrapedWithin), 1, Integer::sum);
            }
            log.info("{} scrape finished: {}", which, counts);
        } catch (Exception e) {
            log.error("{} scrape failed", which, e);
        }
    }

    /**
     * Ports the APScheduler 'delete_old_jobs' job, and also deletes jobs that expired over
     * app.cleanup.expired-days-old days ago. Like APScheduler's default, the cron runs in the
     * server's local time zone unless app.cleanup.zone is set.
     */
    @Scheduled(cron = "${app.cleanup.cron:0 0 2 * * *}", zone = "${app.cleanup.zone:}")
    public void deleteOldJobs() {
        try {
            long deleted = jobListingService.deleteOldJobs(cleanupDaysOld);
            log.info("Nightly cleanup deleted {} jobs older than {} days", deleted, cleanupDaysOld);
        } catch (Exception e) {
            log.error("Nightly cleanup of old jobs failed", e);
        }
        try {
            jobListingService.deleteExpiredJobs(cleanupExpiredDaysOld);
        } catch (Exception e) {
            log.error("Nightly cleanup of expired jobs failed", e);
        }
    }

    List<String> startupTitles() {
        List<String> titles = candidateService.getUniqueJobTitles().stream()
                .filter(t -> t != null && !t.isBlank())
                .toList();
        return titles.isEmpty() ? List.of(FALLBACK_TITLE) : titles;
    }
}
