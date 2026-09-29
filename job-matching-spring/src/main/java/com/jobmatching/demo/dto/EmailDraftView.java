package com.jobmatching.demo.dto;

import com.jobmatching.demo.entity.EmailDraft;
import com.jobmatching.demo.entity.JobListing;

import java.time.LocalDateTime;
import java.util.UUID;

// A draft plus enough of its job to show on the Drafts page without a second request.
// job_title/company_name/job_url are null if the job has since been deleted.
public record EmailDraftView(
        UUID id,
        UUID jobId,
        String jobTitle,
        String companyName,
        String jobUrl,
        String recipient,
        String subject,
        String body,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime sentAt
) {
    public static EmailDraftView of(EmailDraft draft, JobListing job) {
        return new EmailDraftView(
                draft.getId(), draft.getJobId(),
                job != null ? job.getTitle() : null,
                job != null ? job.getCompanyName() : null,
                job != null ? applyUrl(job) : null,
                draft.getRecipient(), draft.getSubject(), draft.getBody(), draft.getStatus().name(),
                draft.getCreatedAt(), draft.getUpdatedAt(), draft.getSentAt());
    }

    // The employer's own posting when the scrape found one, otherwise the job board's.
    public static String applyUrl(JobListing job) {
        return job.getJobUrlDirect() != null && !job.getJobUrlDirect().isBlank() ? job.getJobUrlDirect() : job.getJobUrl();
    }
}
