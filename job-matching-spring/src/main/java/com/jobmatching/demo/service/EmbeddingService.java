package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.JobListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates and persists OpenAI embeddings for candidates and jobs. Mirrors
 * jobspy/database.py's _generate_candidate_embedding / generate_job_embeddings, including
 * the exact profile-text templates so existing embeddings stay comparable to new ones.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final CandidateInfoRepository candidateInfoRepository;
    private final JobListingRepository jobListingRepository;

    public EmbeddingService(EmbeddingModel embeddingModel,
                             CandidateInfoRepository candidateInfoRepository,
                             JobListingRepository jobListingRepository) {
        this.embeddingModel = embeddingModel;
        this.candidateInfoRepository = candidateInfoRepository;
        this.jobListingRepository = jobListingRepository;
    }

    public boolean generateCandidateEmbedding(UUID candidateId) {
        CandidateInfo candidate = candidateInfoRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            log.error("Candidate not found: {}", candidateId);
            return false;
        }
        try {
            float[] embedding = embeddingModel.embed(buildCandidateProfileText(candidate));
            candidateInfoRepository.updateEmbedding(candidateId, toVectorLiteral(embedding));
            log.info("Saved embedding for candidate: {}", candidateId);
            return true;
        } catch (Exception e) {
            log.error("Error generating candidate embedding for {}: {}", candidateId, e.getMessage());
            return false;
        }
    }

    public int generateJobEmbeddings(int limit) {
        List<JobListing> jobs = jobListingRepository.findJobsMissingEmbedding(limit);
        int processedCount = 0;
        for (JobListing job : jobs) {
            try {
                float[] embedding = embeddingModel.embed(buildJobText(job));
                jobListingRepository.updateEmbedding(job.getId(), toVectorLiteral(embedding));
                processedCount++;
            } catch (Exception e) {
                log.error("Error generating embedding for job {}: {}", job.getId(), e.getMessage());
            }
        }
        log.info("Generated embeddings for {} jobs", processedCount);
        return processedCount;
    }

    // Embeds several texts in one OpenAI call, as pgvector literals, without saving anything.
    // Used for what-if scores (WhatIfService), which compare a hypothetical profile with the
    // current one.
    public List<String> embedAsVectorLiterals(List<String> texts) {
        return embeddingModel.embed(texts).stream().map(EmbeddingService::toVectorLiteral).toList();
    }

    String buildCandidateProfileText(CandidateInfo candidate) {
        String location = Stream.of(candidate.getLocationCity(), candidate.getLocationState(), candidate.getLocationCountry())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
        String skills = candidate.getSkills() == null ? "" : String.join(", ", candidate.getSkills());
        return """
                Candidate Name: %s
                College Degree: %s
                Preferred Job Title: %s
                Skills: %s
                Location: %s
                """.formatted(
                nullToEmpty(candidate.getCandidateName()),
                nullToEmpty(candidate.getCollegeDegree()),
                nullToEmpty(candidate.getPreferredJobTitle()),
                skills,
                location
        ).strip();
    }

    // ada-002 accepts 8191 tokens; a scraped description can be far longer, and a job whose text is
    // over the limit fails every time (and is retried on every matches request). Well under it.
    static final int MAX_DESCRIPTION_CHARS = 12_000;

    String buildJobText(JobListing job) {
        return """
                Job Title: %s
                Company: %s
                Description: %s
                Skills: %s
                Job Type: %s
                Location: %s
                """.formatted(
                nullToEmpty(job.getTitle()),
                nullToEmpty(job.getCompanyName()),
                truncate(nullToEmpty(job.getDescription()), MAX_DESCRIPTION_CHARS),
                nullToEmpty(job.getSkills()),
                nullToEmpty(job.getJobType()),
                nullToEmpty(job.getLocation())
        ).strip();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 10 + 2);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
