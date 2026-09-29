package com.jobmatching.demo.repository;

import com.jobmatching.demo.entity.EmailDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailDraftRepository extends JpaRepository<EmailDraft, UUID> {

    List<EmailDraft> findByCandidateIdOrderByUpdatedAtDesc(UUID candidateId);

    List<EmailDraft> findByCandidateIdAndStatusOrderByUpdatedAtDesc(UUID candidateId, EmailDraft.Status status);

    long countByCandidateId(UUID candidateId);

    // Scoped by candidate so one candidate can never load another's draft by guessing an id.
    Optional<EmailDraft> findByIdAndCandidateId(UUID id, UUID candidateId);
}
