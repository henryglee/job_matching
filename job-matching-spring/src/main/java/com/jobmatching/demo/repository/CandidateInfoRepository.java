package com.jobmatching.demo.repository;

import com.jobmatching.demo.entity.CandidateInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateInfoRepository extends JpaRepository<CandidateInfo, UUID> {

    // Mirrors JobDatabase.get_unclaimed_candidates: profiles with no linked user_accounts row.
    @Query("SELECT c FROM CandidateInfo c WHERE c.id NOT IN "
            + "(SELECT u.candidateProfile.id FROM UserAccount u WHERE u.candidateProfile IS NOT NULL) "
            + "ORDER BY c.candidateName")
    List<CandidateInfo> findUnclaimed();

    // Like JobDatabase.find_unclaimed_candidate_by_email, but all matches rather than
    // LIMIT 1: nothing stops two unclaimed profiles sharing an email.
    @Query("SELECT c FROM CandidateInfo c WHERE LOWER(c.email) = LOWER(:email) AND c.id NOT IN "
            + "(SELECT u.candidateProfile.id FROM UserAccount u WHERE u.candidateProfile IS NOT NULL) "
            + "ORDER BY c.candidateName")
    List<CandidateInfo> findUnclaimedByEmailIgnoreCase(@Param("email") String email);

    // Mirrors JobDatabase.get_unique_job_titles.
    @Query("SELECT DISTINCT c.preferredJobTitle FROM CandidateInfo c WHERE c.preferredJobTitle IS NOT NULL")
    List<String> findDistinctPreferredJobTitles();

    // `embedding` is not a mapped JPA field (see CandidateInfo) — go straight to SQL for it.

    @Query(value = "SELECT embedding::text FROM candidate_info WHERE id = :id", nativeQuery = true)
    Optional<String> findEmbeddingById(@Param("id") UUID id);

    // @Transactional is required: without it this fails with "No active transaction for
    // update or delete query" unless the caller already opened a transaction, and
    // EmbeddingService deliberately doesn't (it would hold one open across OpenAI calls).
    @Modifying
    @Transactional
    @Query(value = "UPDATE candidate_info SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    int updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);
}
