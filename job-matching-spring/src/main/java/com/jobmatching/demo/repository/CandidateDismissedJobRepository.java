package com.jobmatching.demo.repository;

import com.jobmatching.demo.entity.CandidateDismissedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CandidateDismissedJobRepository extends JpaRepository<CandidateDismissedJob, UUID> {

    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);

    @Query("SELECT d.jobId FROM CandidateDismissedJob d WHERE d.candidateId = :candidateId")
    List<UUID> findJobIdsByCandidateId(@Param("candidateId") UUID candidateId);
}
