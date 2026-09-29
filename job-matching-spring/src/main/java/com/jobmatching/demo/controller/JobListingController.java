package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.DeleteOldJobsRequest;
import com.jobmatching.demo.dto.GenerateEmbeddingsRequest;
import com.jobmatching.demo.dto.ScrapeJobsRequest;
import com.jobmatching.demo.dto.ScrapeSummary;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.scrape.ScrapeOrchestrationService;
import com.jobmatching.demo.service.EmbeddingService;
import com.jobmatching.demo.service.JobListingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ports app.py's /api/jobs/* routes. Doc notes the scrape/delete-old/generate-embeddings
 * routes are open to any authenticated user in the current Flask code but recommends
 * restricting them to Admin/Staff in Spring Boot — applied here.
 */
@RestController
public class JobListingController {

    static final int DEFAULT_JOBS_LIMIT = 100;
    static final int MAX_JOBS_LIMIT = 500;

    private final JobListingService jobListingService;
    private final EmbeddingService embeddingService;
    private final ScrapeOrchestrationService scrapeOrchestrationService;

    public JobListingController(JobListingService jobListingService,
                                 EmbeddingService embeddingService,
                                 ScrapeOrchestrationService scrapeOrchestrationService) {
        this.jobListingService = jobListingService;
        this.embeddingService = embeddingService;
        this.scrapeOrchestrationService = scrapeOrchestrationService;
    }

    @PostMapping("/api/jobs/scrape")
    @PreAuthorize("hasAnyRole('Admin','Staff')")
    public ResponseEntity<Map<String, Object>> scrapeJobs(@RequestBody(required = false) ScrapeJobsRequest request) {
        ScrapeJobsRequest effective = request != null ? request : new ScrapeJobsRequest(null, null, null, null);
        ScrapeSummary summary = scrapeOrchestrationService.scrapeNow(effective);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Successfully scraped and saved " + summary.jobsSaved() + " jobs");
        body.put("jobs_scraped", summary.jobsScraped());
        body.put("jobs_saved", summary.jobsSaved());
        body.put("embeddings_generated", summary.embeddingsGenerated());
        body.put("sites", summary.sites());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // Paged: the table grows with every scrape, so an unbounded list gets slower every day.
    // Newest first, with the id as a tie-breaker so pages don't overlap. `total` is the full
    // count, so a client can tell whether to ask for the next page.
    @GetMapping("/api/jobs")
    public Map<String, Object> getJobs(@RequestParam(required = false) String site,
                                        @RequestParam(required = false) Integer limit,
                                        @RequestParam(defaultValue = "0") int page) {
        int pageSize = limit == null ? DEFAULT_JOBS_LIMIT : Math.max(1, Math.min(limit, MAX_JOBS_LIMIT));
        Pageable pageable = PageRequest.of(Math.max(0, page), pageSize,
                Sort.by(Sort.Direction.DESC, "dateScraped").and(Sort.by("id")));
        Page<JobListing> result = jobListingService.getJobs(site, pageable);
        return Map.of("jobs", result.getContent(), "total", result.getTotalElements(),
                "page", result.getNumber(), "limit", pageSize);
    }

    @PostMapping("/api/jobs/delete-old")
    @PreAuthorize("hasAnyRole('Admin','Staff')")
    public Map<String, Object> deleteOldJobs(@RequestBody(required = false) DeleteOldJobsRequest request) {
        int daysOld = (request != null ? request : new DeleteOldJobsRequest(null)).daysOldOrDefault();
        long deleted = jobListingService.deleteOldJobs(daysOld);
        return Map.of(
                "message", "Deleted " + deleted + " jobs older than " + daysOld + " days",
                "jobs_deleted", deleted
        );
    }

    @PostMapping("/api/jobs/generate-embeddings")
    @PreAuthorize("hasAnyRole('Admin','Staff')")
    public Map<String, Object> generateEmbeddings(@RequestBody(required = false) GenerateEmbeddingsRequest request) {
        int limit = (request != null ? request : new GenerateEmbeddingsRequest(null)).limitOrDefault();
        int processed = embeddingService.generateJobEmbeddings(limit);
        return Map.of(
                "message", "Generated embeddings for " + processed + " jobs",
                "jobs_processed", processed
        );
    }
}
