package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.util.DateParsing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ports the candidate-profile side of jobspy/database.py. Field names in
 * {@link #updateCandidate} match the JSON keys the existing PUT /api/candidates/&lt;id&gt;
 * endpoint accepts (see API_DOCUMENTATION.md) and the whitelist in
 * JobDatabase.update_candidate — not just the subset the doc calls out.
 */
@Service
public class CandidateService {

    // Fields that feed EmbeddingService.buildCandidateProfileText; changing one makes the stored
    // embedding (and so the matches) stale.
    private static final Set<String> EMBEDDED_FIELDS = Set.of(
            "candidate_name", "college_degree", "preferred_job_title", "skills",
            "location_city", "location_state", "location_country"
    );

    private static final Set<String> UPDATABLE_FIELDS = Set.of(
            "candidate_name", "email", "college_degree", "graduation_date", "preferred_job_title",
            "skills", "location_city", "location_state", "location_country",
            "requires_sponsorship", "sponsorship_type"
    );

    private final CandidateInfoRepository candidateInfoRepository;
    private final EmbeddingService embeddingService;

    public CandidateService(CandidateInfoRepository candidateInfoRepository, EmbeddingService embeddingService) {
        this.candidateInfoRepository = candidateInfoRepository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public CandidateInfo createCandidate(CandidateInfo candidate) {
        CandidateLimits.validate(candidate);
        return candidateInfoRepository.save(candidate);
    }

    // Deliberately not @Transactional: the embedding is an OpenAI call, and holding a pooled DB
    // connection across it could exhaust the pool under load (see EmbeddingService). The profile is
    // committed first; if embedding fails it's regenerated on demand (JobMatchingService).
    public CandidateInfo createCandidateWithEmbedding(CandidateInfo candidate) {
        CandidateLimits.validate(candidate);
        CandidateInfo saved = candidateInfoRepository.save(candidate);
        embeddingService.generateCandidateEmbedding(saved.getId());
        return saved;
    }

    public Page<CandidateInfo> getCandidates(Pageable pageable) {
        return candidateInfoRepository.findAll(pageable);
    }

    public Optional<CandidateInfo> getCandidateById(UUID candidateId) {
        return candidateInfoRepository.findById(candidateId);
    }

    @Transactional
    public boolean deleteCandidate(UUID candidateId) {
        if (!candidateInfoRepository.existsById(candidateId)) {
            return false;
        }
        candidateInfoRepository.deleteById(candidateId);
        return true;
    }

    @Transactional
    public boolean updateCandidate(UUID candidateId, Map<String, Object> candidateData) {
        CandidateInfo candidate = candidateInfoRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            return false;
        }
        for (String field : UPDATABLE_FIELDS) {
            if (candidateData.containsKey(field)) {
                try {
                    applyField(candidate, field, candidateData.get(field));
                } catch (ClassCastException | NullPointerException e) {
                    throw new IllegalArgumentException("Invalid value for '" + field + "'");
                }
            }
        }
        CandidateLimits.validate(candidate); // before saving, so an over-long value rolls the whole update back
        candidateInfoRepository.save(candidate);
        return true;
    }

    /** True if the update changes something the candidate's embedding is built from. */
    public boolean affectsEmbedding(Map<String, Object> candidateData) {
        return candidateData.keySet().stream().anyMatch(EMBEDDED_FIELDS::contains);
    }

    // Called after updateCandidate has committed, so the embedding is built from the saved profile.
    public void refreshEmbedding(UUID candidateId) {
        embeddingService.generateCandidateEmbedding(candidateId);
    }

    public List<CandidateInfo> getUnclaimedCandidates() {
        return candidateInfoRepository.findUnclaimed();
    }

    public List<CandidateInfo> findUnclaimedCandidatesByEmail(String email) {
        return candidateInfoRepository.findUnclaimedByEmailIgnoreCase(email);
    }

    public List<String> getUniqueJobTitles() {
        return candidateInfoRepository.findDistinctPreferredJobTitles();
    }

    @SuppressWarnings("unchecked")
    private void applyField(CandidateInfo candidate, String field, Object value) {
        switch (field) {
            case "candidate_name" -> candidate.setCandidateName((String) value);
            case "email" -> candidate.setEmail((String) value);
            case "college_degree" -> candidate.setCollegeDegree((String) value);
            case "graduation_date" -> candidate.setGraduationDate(
                    value instanceof String raw ? DateParsing.parseIsoOrDate(raw) : null);
            case "preferred_job_title" -> candidate.setPreferredJobTitle((String) value);
            case "skills" -> candidate.setSkills((List<String>) value);
            case "location_city" -> candidate.setLocationCity((String) value);
            case "location_state" -> candidate.setLocationState((String) value);
            case "location_country" -> candidate.setLocationCountry((String) value);
            case "requires_sponsorship" -> candidate.setRequiresSponsorship((Boolean) value);
            case "sponsorship_type" -> candidate.setSponsorshipType((List<String>) value);
            default -> throw new IllegalStateException("Unhandled updatable field: " + field);
        }
    }
}
