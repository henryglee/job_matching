package com.jobmatching.demo.scrape;

import com.jobmatching.demo.dto.ScrapeJobsRequest;
import com.jobmatching.demo.entity.JobListing;

import java.util.Collection;
import java.util.List;

/**
 * Calls out to the JobSpy scraping microservice at ../scrape-service (see
 * scrape-service/scrape_service.py) — jobspy stays a Python service since
 * LinkedIn/Indeed/Glassdoor/etc. scraping has no Java equivalent. This interface is the
 * contract that service satisfies; {@link HttpJobScrapeClient} is the Spring Boot side.
 */
public interface JobScrapeClient {

    List<JobListing> scrape(ScrapeJobsRequest request);

    /**
     * Which of these Indeed job keys (the jk parameter of an Indeed job_url) no longer accept
     * applications: expired, or removed from Indeed altogether. Throws if Indeed can't be asked.
     */
    List<String> findExpiredIndeedJobKeys(Collection<String> jobKeys);
}
