package com.jobmatching.demo.scrape;

import com.jobmatching.demo.dto.ScrapeJobsRequest;
import com.jobmatching.demo.dto.ScrapeSummary;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.service.EmbeddingService;
import com.jobmatching.demo.service.JobListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wires scraping + save + embed together, for the on-demand POST /api/jobs/scrape endpoint
 * and for app.py's scrape_jobs_for_title: the per-title background scrape used both on
 * candidate creation and by the startup scrape (see BackgroundJobs).
 */
@Service
public class ScrapeOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ScrapeOrchestrationService.class);

    // Same as app.py's _SCRAPE_TTL_SECONDS, which matches the scrape's hours_old=24 window:
    // scraping a term again sooner would mostly just return the same listings.
    static final Duration SCRAPE_TTL = Duration.ofHours(24);

    public enum TitleScrapeOutcome {
        SCRAPED,
        SKIPPED_RECENT,
        SKIPPED_IN_PROGRESS,
        FAILED
    }

    private final JobScrapeClient jobScrapeClient;
    private final JobListingService jobListingService;
    private final EmbeddingService embeddingService;
    private final ScrapeStateStore scrapeStateStore;
    private final Clock clock;
    // Terms being scraped right now (keyed like ScrapeStateStore). The startup scrape and a
    // new candidate's scrape can ask for the same term at once; only one should run.
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    @Autowired
    public ScrapeOrchestrationService(JobScrapeClient jobScrapeClient,
                                       JobListingService jobListingService,
                                       EmbeddingService embeddingService,
                                       ScrapeStateStore scrapeStateStore) {
        this(jobScrapeClient, jobListingService, embeddingService, scrapeStateStore, Clock.systemUTC());
    }

    ScrapeOrchestrationService(JobScrapeClient jobScrapeClient,
                               JobListingService jobListingService,
                               EmbeddingService embeddingService,
                               ScrapeStateStore scrapeStateStore,
                               Clock clock) {
        this.jobScrapeClient = jobScrapeClient;
        this.jobListingService = jobListingService;
        this.embeddingService = embeddingService;
        this.scrapeStateStore = scrapeStateStore;
        this.clock = clock;
    }

    // On-demand scrape: always runs (no TTL), exactly like app.py's POST /api/jobs/scrape.
    public ScrapeSummary scrapeNow(ScrapeJobsRequest request) {
        List<JobListing> scraped = jobScrapeClient.scrape(request);
        int saved = jobListingService.saveJobs(scraped);
        int embedded = saved > 0 ? embeddingService.generateJobEmbeddings(saved) : 0;
        return new ScrapeSummary(scraped.size(), saved, embedded, request.siteNameOrDefault());
    }

    /**
     * Ports app.py's scrape_jobs_for_title: scrape one search term with the default
     * sites/location, unless it was already scraped within the last 24 hours. Success —
     * including a scrape that found nothing — starts a new 24-hour window; a failure
     * doesn't, so the term is retried next time. Never throws.
     */
    public TitleScrapeOutcome scrapeForTitle(String searchTerm) {
        return scrapeForTitle(searchTerm, SCRAPE_TTL);
    }

    // As above, but skipped only if scraped within skipIfScrapedWithin. A daily run needs less
    // than 24 hours: each scrape is timestamped when it finishes, so yesterday's run would
    // otherwise still count as "within 24 hours" when today's starts, and every other day's
    // would be skipped.
    public TitleScrapeOutcome scrapeForTitle(String searchTerm, Duration skipIfScrapedWithin) {
        String key = ScrapeStateStore.key(searchTerm);
        Optional<Instant> last = scrapeStateStore.lastScraped(searchTerm);
        if (last.isPresent() && last.get().plus(skipIfScrapedWithin).isAfter(clock.instant())) {
            log.info("Skipping scrape for '{}' — last scraped at {}", searchTerm, last.get());
            return TitleScrapeOutcome.SKIPPED_RECENT;
        }
        if (!inProgress.add(key)) {
            log.info("Skipping scrape for '{}' — already in progress", searchTerm);
            return TitleScrapeOutcome.SKIPPED_IN_PROGRESS;
        }
        try {
            ScrapeSummary summary = scrapeNow(new ScrapeJobsRequest(null, searchTerm, null, null));
            scrapeStateStore.recordScrape(searchTerm, clock.instant());
            log.info("Scrape for '{}' saved {} jobs, embedded {}",
                    searchTerm, summary.jobsSaved(), summary.embeddingsGenerated());
            return TitleScrapeOutcome.SCRAPED;
        } catch (Exception e) {
            log.warn("Scrape for '{}' failed: {}", searchTerm, e.getMessage());
            return TitleScrapeOutcome.FAILED;
        } finally {
            inProgress.remove(key);
        }
    }

    // Mirrors app.py spawning a background thread on candidate creation when
    // preferred_job_title is set. Best-effort: never blocks or fails candidate creation.
    @Async
    public void scrapeForTitleAsync(String searchTerm) {
        scrapeForTitle(searchTerm);
    }
}
