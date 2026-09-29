package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.JobListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Ports the job-listing side of jobspy/database.py: scrape-result persistence with
 * (job_url, site) dedup, retrieval and retention cleanup. Per-candidate dismissal lives in
 * JobMatchingService, next to the matching it affects.
 */
@Service
public class JobListingService {

    private static final Logger log = LoggerFactory.getLogger(JobListingService.class);

    private final JobListingRepository jobListingRepository;

    public JobListingService(JobListingRepository jobListingRepository) {
        this.jobListingRepository = jobListingRepository;
    }

    // Not one transaction: each job is saved (and committed) on its own, so a single listing that
    // breaks a column limit is skipped instead of rolling back everything else in the scrape, which
    // would fail identically every time that search term is scraped again.
    public int saveJobs(List<JobListing> jobs) {
        if (jobs.isEmpty()) {
            log.warn("Empty job list provided, no jobs to save");
            return 0;
        }
        int savedCount = 0;
        int skipped = 0;
        for (JobListing job : jobs) {
            if (jobListingRepository.existsByJobUrlAndSite(job.getJobUrl(), job.getSite())) {
                log.debug("Job already exists: {} at {}", job.getTitle(), job.getCompanyName());
                continue;
            }
            try {
                jobListingRepository.save(job);
                savedCount++;
            } catch (RuntimeException e) {
                skipped++;
                log.warn("Skipped a job that couldn't be saved (site {}): {}", job.getSite(), e.getMessage());
            }
        }
        if (skipped > 0) {
            log.warn("Skipped {} of {} scraped jobs", skipped, jobs.size());
        }
        log.info("Successfully saved {} jobs to database", savedCount);
        return savedCount;
    }

    // Expired jobs (see JobExpiryService) are left out, as they are from matches.
    public Page<JobListing> getJobs(String site, Pageable pageable) {
        if (site != null && !site.isBlank()) {
            return jobListingRepository.findUnexpiredBySite(site, pageable);
        }
        return jobListingRepository.findUnexpired(pageable);
    }

    @Transactional
    public long deleteOldJobs(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(daysOld);
        jobListingRepository.deleteDismissalsOfJobsScrapedBefore(cutoff);
        long deletedCount = jobListingRepository.deleteByDateScrapedBefore(cutoff);
        log.info("Deleted {} jobs older than {} days", deletedCount, daysOld);
        return deletedCount;
    }

    /**
     * Deletes jobs that stopped accepting applications more than daysExpired days ago, sooner
     * than the date-scraped cleanup above would. Jobs a candidate has an email draft for are
     * kept (deleting the job would delete the draft) until that cleanup removes them.
     */
    @Transactional
    public int deleteExpiredJobs(int daysExpired) {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(daysExpired);
        jobListingRepository.deleteDismissalsOfExpiredJobsWithoutDrafts(cutoff);
        int deletedCount = jobListingRepository.deleteExpiredJobsWithoutDrafts(cutoff);
        log.info("Deleted {} jobs expired more than {} days ago", deletedCount, daysExpired);
        return deletedCount;
    }
}
