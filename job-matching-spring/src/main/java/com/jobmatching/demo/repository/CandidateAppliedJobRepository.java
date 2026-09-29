package com.jobmatching.demo.repository;

import com.jobmatching.demo.entity.CandidateAppliedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateAppliedJobRepository extends JpaRepository<CandidateAppliedJob, UUID> {

    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);

    List<CandidateAppliedJob> findByCandidateIdOrderByAppliedAtDesc(UUID candidateId);

    Optional<CandidateAppliedJob> findByIdAndCandidateId(UUID id, UUID candidateId);

    // Only rows whose job still exists: the others (job_id null) have nothing left to exclude.
    @Query("SELECT a.jobId FROM CandidateAppliedJob a WHERE a.candidateId = :candidateId AND a.jobId IS NOT NULL")
    List<UUID> findJobIdsByCandidateId(@Param("candidateId") UUID candidateId);
}
