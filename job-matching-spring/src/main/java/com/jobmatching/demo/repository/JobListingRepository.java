package com.jobmatching.demo.repository;

import com.jobmatching.demo.entity.JobListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobListingRepository extends JpaRepository<JobListing, UUID> {

    // Mirrors JobDatabase.save_jobs' per-row dedup check on (job_url, site).
    boolean existsByJobUrlAndSite(String jobUrl, String site);

    // Mirrors JobDatabase.get_jobs(site=...), minus jobs that no longer accept applications.
    @Query("SELECT j FROM JobListing j WHERE j.expiredAt IS NULL")
    Page<JobListing> findUnexpired(Pageable pageable);

    @Query("SELECT j FROM JobListing j WHERE j.site = :site AND j.expiredAt IS NULL")
    Page<JobListing> findUnexpiredBySite(@Param("site") String site, Pageable pageable);

    List<JobListing> findByIdIn(Collection<UUID> ids);

    // Mirrors JobDatabase.delete_old_jobs. Spring Data handles `deleteBy...` derived
    // queries natively — no `@Modifying`/`@Query` needed.
    long deleteByDateScrapedBefore(LocalDateTime cutoff);

    // Has to run first: candidate_dismissed_jobs references job_listings without a cascade,
    // so one dismissed job among them fails the whole delete above (app.py's did too).
    @Modifying
    @Query(value = """
            DELETE FROM candidate_dismissed_jobs
            WHERE job_id IN (SELECT id FROM job_listings WHERE date_scraped < :cutoff)
            """, nativeQuery = true)
    int deleteDismissalsOfJobsScrapedBefore(@Param("cutoff") LocalDateTime cutoff);

    // Deleting jobs that expired before the cutoff (JobListingService.deleteExpiredJobs) skips
    // any a candidate has an email draft for, since email_drafts cascades on delete. Their
    // dismissals go first: candidate_dismissed_jobs references job_listings without a cascade.
    @Modifying
    @Query(value = """
            DELETE FROM candidate_dismissed_jobs WHERE job_id IN (
                SELECT j.id FROM job_listings j
                WHERE j.expired_at < :cutoff
                  AND NOT EXISTS (SELECT 1 FROM email_drafts d WHERE d.job_id = j.id))
            """, nativeQuery = true)
    int deleteDismissalsOfExpiredJobsWithoutDrafts(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query(value = """
            DELETE FROM job_listings j
            WHERE j.expired_at < :cutoff
              AND NOT EXISTS (SELECT 1 FROM email_drafts d WHERE d.job_id = j.id)
            """, nativeQuery = true)
    int deleteExpiredJobsWithoutDrafts(@Param("cutoff") LocalDateTime cutoff);

    // `embedding` is not a mapped JPA field (see JobListing) — go straight to SQL for it.

    @Query(value = "SELECT * FROM job_listings WHERE embedding IS NULL LIMIT :limit", nativeQuery = true)
    List<JobListing> findJobsMissingEmbedding(@Param("limit") int limit);

    // @Transactional is required: without it this fails with "No active transaction for
    // update or delete query" unless the caller already opened a transaction, and
    // EmbeddingService deliberately doesn't (it would hold one open across OpenAI calls).
    @Modifying
    @Transactional
    @Query(value = "UPDATE job_listings SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    int updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    // Marks jobs as no longer accepting applications, so the match queries below skip them.
    // Already-expired ones keep their original timestamp.
    @Modifying
    @Transactional
    @Query(value = "UPDATE job_listings SET expired_at = :expiredAt WHERE id IN (:ids) AND expired_at IS NULL", nativeQuery = true)
    int markExpired(@Param("ids") Collection<UUID> ids, @Param("expiredAt") LocalDateTime expiredAt);

    // Mirrors JobDatabase.find_matching_jobs' cosine-distance query, split into the two
    // branches the Python code uses (with/without a dismissed-jobs exclusion) since a
    // native `NOT IN ()` with an empty list is a SQL syntax error. Expired jobs are left out.

    interface JobMatch {
        UUID getId();

        Double getDistance();
    }

    @Query(value = """
            SELECT id, embedding <=> CAST(:embedding AS vector) AS distance
            FROM job_listings
            WHERE embedding IS NOT NULL AND expired_at IS NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<JobMatch> findTopMatches(@Param("embedding") String embedding, @Param("limit") int limit);

    @Query(value = """
            SELECT id, embedding <=> CAST(:embedding AS vector) AS distance
            FROM job_listings
            WHERE embedding IS NOT NULL AND expired_at IS NULL AND id NOT IN (:excludedIds)
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<JobMatch> findTopMatchesExcluding(@Param("embedding") String embedding,
                                           @Param("excludedIds") Collection<UUID> excludedIds,
                                           @Param("limit") int limit);

    // Cosine distance from a candidate's embedding to one job, for explaining a single match
    // (JobMatchingService.explainMatch). Empty if the job has no embedding yet.
    @Query(value = """
            SELECT embedding <=> CAST(:embedding AS vector)
            FROM job_listings
            WHERE id = :id AND embedding IS NOT NULL
            """, nativeQuery = true)
    Optional<Double> findDistance(@Param("embedding") String embedding, @Param("id") UUID id);

    // Cosine distance from an embedding to several jobs (those with an embedding), for what-if
    // scores (WhatIfService). ids must not be empty: a native IN () is a SQL syntax error.
    @Query(value = """
            SELECT id, embedding <=> CAST(:embedding AS vector) AS distance
            FROM job_listings
            WHERE id IN (:ids) AND embedding IS NOT NULL
            """, nativeQuery = true)
    List<JobMatch> findDistances(@Param("embedding") String embedding, @Param("ids") Collection<UUID> ids);
}
