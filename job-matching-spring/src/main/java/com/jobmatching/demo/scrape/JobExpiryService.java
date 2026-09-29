package com.jobmatching.demo.scrape;

import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.JobListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Keeps job listings that no longer accept applications out of matches. Scrapes only fetch
 * jobs posted in the last 24 hours, but they're kept for 30 days, and Indeed postings often
 * close well before then — so the top matches, ranked purely on similarity, drift towards
 * listings whose link says "This job has expired".
 *
 * Only Indeed jobs are checked: the scrape service can ask Indeed's API about a job by key,
 * and there's no equivalent for the other sites.
 */
@Service
public class JobExpiryService {

    private static final Logger log = LoggerFactory.getLogger(JobExpiryService.class);

    // How long a job Indeed said was live is trusted before asking again. Matches are viewed
    // far more often than listings close, so most match views skip the round trip.
    static final Duration RECHECK_AFTER = Duration.ofHours(1);

    private static final Pattern INDEED_JOB_KEY = Pattern.compile("[?&]jk=([0-9A-Za-z]+)");

    private final JobScrapeClient jobScrapeClient;
    private final JobListingRepository jobListingRepository;
    private final Clock clock;
    private final Map<UUID, Instant> confirmedLiveAt = new ConcurrentHashMap<>();

    @Autowired
    public JobExpiryService(JobScrapeClient jobScrapeClient, JobListingRepository jobListingRepository) {
        this(jobScrapeClient, jobListingRepository, Clock.systemUTC());
    }

    JobExpiryService(JobScrapeClient jobScrapeClient, JobListingRepository jobListingRepository, Clock clock) {
        this.jobScrapeClient = jobScrapeClient;
        this.jobListingRepository = jobListingRepository;
        this.clock = clock;
    }

    /**
     * Asks Indeed whether any of these jobs have expired and marks those that have, so the
     * match queries stop returning them. Returns the ids found expired. Fails open: if Indeed
     * can't be asked, nothing is marked and the result is empty.
     */
    public Set<UUID> markExpired(Collection<JobListing> jobs) {
        Instant now = clock.instant();
        Map<String, UUID> idByKey = new HashMap<>();
        for (JobListing job : jobs) {
            Instant liveAt = confirmedLiveAt.get(job.getId());
            if (!"indeed".equals(job.getSite()) || (liveAt != null && liveAt.plus(RECHECK_AFTER).isAfter(now))) {
                continue;
            }
            indeedJobKey(job.getJobUrl()).ifPresent(key -> idByKey.put(key, job.getId()));
        }
        if (idByKey.isEmpty()) {
            return Set.of();
        }

        List<String> expiredKeys;
        try {
            expiredKeys = jobScrapeClient.findExpiredIndeedJobKeys(idByKey.keySet());
        } catch (Exception e) {
            log.warn("Could not check {} Indeed jobs for expiry: {}", idByKey.size(), e.getMessage());
            return Set.of();
        }

        Set<UUID> expiredIds = expiredKeys.stream().map(idByKey::get).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        confirmedLiveAt.values().removeIf(liveAt -> !liveAt.plus(RECHECK_AFTER).isAfter(now));
        idByKey.values().stream().filter(id -> !expiredIds.contains(id))
                .forEach(id -> confirmedLiveAt.put(id, now));
        if (!expiredIds.isEmpty()) {
            jobListingRepository.markExpired(expiredIds, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            log.info("Marked {} of {} checked Indeed jobs expired", expiredIds.size(), idByKey.size());
        }
        return expiredIds;
    }

    static Optional<String> indeedJobKey(String jobUrl) {
        if (jobUrl == null) {
            return Optional.empty();
        }
        Matcher m = INDEED_JOB_KEY.matcher(jobUrl);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
