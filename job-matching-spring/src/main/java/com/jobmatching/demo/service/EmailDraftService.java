package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.EmailDraftView;
import com.jobmatching.demo.dto.JobApplicationContext;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.EmailDraft;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.EmailDraftRepository;
import com.jobmatching.demo.repository.JobListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application email drafts. The drafting itself happens in an AI client over MCP (see
 * mcp/EmailDraftTools): this service only hands it job/profile context and stores what it
 * writes. Nothing here sends email. The candidate sends from their own mail app via the
 * React Drafts page and then marks the draft sent.
 *
 * Every method takes the candidate id from the caller's JWT and scopes lookups by it.
 */
@Service
public class EmailDraftService {

    private static final int MAX_SUBJECT_LENGTH = 300;
    private static final int MAX_BODY_LENGTH = 20000;
    // Drafts can be created by an AI assistant over MCP with no other limit, so a looping client
    // could otherwise fill the table.
    static final int MAX_DRAFTS_PER_CANDIDATE = 200;

    private final EmailDraftRepository emailDraftRepository;
    private final CandidateInfoRepository candidateInfoRepository;
    private final JobListingRepository jobListingRepository;

    public EmailDraftService(EmailDraftRepository emailDraftRepository,
                             CandidateInfoRepository candidateInfoRepository,
                             JobListingRepository jobListingRepository) {
        this.emailDraftRepository = emailDraftRepository;
        this.candidateInfoRepository = candidateInfoRepository;
        this.jobListingRepository = jobListingRepository;
    }

    public Optional<JobApplicationContext> getApplicationContext(UUID candidateId, UUID jobId) {
        CandidateInfo candidate = candidateInfoRepository.findById(candidateId).orElse(null);
        JobListing job = jobListingRepository.findById(jobId).orElse(null);
        if (candidate == null || job == null) {
            return Optional.empty();
        }
        List<String> skills = candidate.getSkills() != null ? candidate.getSkills() : List.of();
        return Optional.of(new JobApplicationContext(
                job.getId(), job.getTitle(), job.getCompanyName(), job.getLocation(),
                job.getDescription(), job.getCompanyDescription(),
                contactEmails(job), EmailDraftView.applyUrl(job),
                candidate.getCandidateName(), candidate.getCollegeDegree(), candidate.getPreferredJobTitle(),
                skills, overlappingSkills(skills, job)));
    }

    /**
     * Saves a new draft written by an AI client. The recipient has to be one of the job's
     * scraped contact emails so the model can't invent an address; the candidate can still
     * change it by hand on the Drafts page.
     */
    @Transactional
    public EmailDraftView createDraft(UUID candidateId, UUID jobId, String recipient, String subject, String body) {
        JobListing job = jobListingRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        String normalizedRecipient = blankToNull(recipient);
        if (normalizedRecipient != null && !contactEmails(job).contains(normalizedRecipient.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Recipient must be one of the job's contact emails; leave it empty if the job has none");
        }

        if (emailDraftRepository.countByCandidateId(candidateId) >= MAX_DRAFTS_PER_CANDIDATE) {
            throw new IllegalArgumentException("You have " + MAX_DRAFTS_PER_CANDIDATE
                    + " drafts, the most allowed. Delete some you no longer need first.");
        }

        EmailDraft draft = new EmailDraft();
        draft.setCandidateId(candidateId);
        draft.setJobId(jobId);
        draft.setRecipient(normalizedRecipient);
        draft.setSubject(requireText(subject, "Subject", MAX_SUBJECT_LENGTH));
        draft.setBody(requireText(body, "Body", MAX_BODY_LENGTH));
        return EmailDraftView.of(emailDraftRepository.save(draft), job);
    }

    // An AI revision: null leaves that field as it is. Only unsent drafts can change.
    @Transactional
    public Optional<EmailDraftView> reviseDraft(UUID candidateId, UUID draftId, String subject, String body) {
        return findUnsent(candidateId, draftId).map(draft -> {
            if (subject != null) {
                draft.setSubject(requireText(subject, "Subject", MAX_SUBJECT_LENGTH));
            }
            if (body != null) {
                draft.setBody(requireText(body, "Body", MAX_BODY_LENGTH));
            }
            return view(draft);
        });
    }

    // The candidate's own edit from the Drafts page, which may set any recipient.
    @Transactional
    public Optional<EmailDraftView> updateDraft(UUID candidateId, UUID draftId, String recipient, String subject, String body) {
        return findUnsent(candidateId, draftId).map(draft -> {
            draft.setRecipient(blankToNull(recipient));
            draft.setSubject(requireText(subject, "Subject", MAX_SUBJECT_LENGTH));
            draft.setBody(requireText(body, "Body", MAX_BODY_LENGTH));
            return view(draft);
        });
    }

    // status null means every draft, newest first.
    public List<EmailDraftView> listDrafts(UUID candidateId, EmailDraft.Status status) {
        List<EmailDraft> drafts = status == null
                ? emailDraftRepository.findByCandidateIdOrderByUpdatedAtDesc(candidateId)
                : emailDraftRepository.findByCandidateIdAndStatusOrderByUpdatedAtDesc(candidateId, status);
        if (drafts.isEmpty()) {
            return List.of();
        }
        Map<UUID, JobListing> jobs = jobListingRepository
                .findByIdIn(drafts.stream().map(EmailDraft::getJobId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(JobListing::getId, Function.identity()));
        return drafts.stream().map(d -> EmailDraftView.of(d, jobs.get(d.getJobId()))).toList();
    }

    @Transactional
    public Optional<EmailDraftView> markSent(UUID candidateId, UUID draftId) {
        return findUnsent(candidateId, draftId).map(draft -> {
            draft.setStatus(EmailDraft.Status.SENT);
            draft.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
            return view(draft);
        });
    }

    @Transactional
    public boolean deleteDraft(UUID candidateId, UUID draftId) {
        Optional<EmailDraft> draft = emailDraftRepository.findByIdAndCandidateId(draftId, candidateId);
        draft.ifPresent(emailDraftRepository::delete);
        return draft.isPresent();
    }

    private Optional<EmailDraft> findUnsent(UUID candidateId, UUID draftId) {
        return emailDraftRepository.findByIdAndCandidateId(draftId, candidateId)
                .filter(d -> d.getStatus() == EmailDraft.Status.DRAFT);
    }

    private EmailDraftView view(EmailDraft draft) {
        return EmailDraftView.of(draft, jobListingRepository.findById(draft.getJobId()).orElse(null));
    }

    // job_listings.emails is whatever the scraper found, usually comma-separated.
    static List<String> contactEmails(JobListing job) {
        if (job.getEmails() == null || job.getEmails().isBlank()) {
            return List.of();
        }
        return Arrays.stream(job.getEmails().split("[,;\\s]+"))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> e.contains("@"))
                .distinct()
                .toList();
    }

    // Candidate skills that appear in the job's skills or description as whole words, so
    // "Java" doesn't match "JavaScript" and "C" doesn't match every word containing a c.
    // "+", "#" and "." count as word characters for C++, C#, .NET and Node.js.
    static List<String> overlappingSkills(List<String> skills, JobListing job) {
        String text = (Objects.toString(job.getSkills(), "") + " " + Objects.toString(job.getDescription(), ""))
                .toLowerCase(Locale.ROOT);
        return skills.stream()
                .filter(skill -> skill != null && !skill.isBlank())
                .filter(skill -> TextMatching.containsTerm(text, skill))
                .toList();
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
