package com.jobmatching.demo.dto;

import com.jobmatching.demo.entity.CandidateAppliedJob;

import java.time.LocalDateTime;
import java.util.UUID;

// One entry on the Applied Jobs page. job_id is null once the listing itself has been cleaned
// up; the title/company/location/link were copied when the job was marked applied.
public record AppliedJobView(
        UUID id,
        UUID jobId,
        String title,
        String companyName,
        String location,
        String jobUrl,
        LocalDateTime appliedAt,
        String status,
        LocalDateTime statusUpdatedAt
) {
    public static AppliedJobView of(CandidateAppliedJob applied) {
        return new AppliedJobView(applied.getId(), applied.getJobId(), applied.getJobTitle(),
                applied.getCompanyName(), applied.getLocation(), applied.getJobUrl(), applied.getAppliedAt(),
                applied.getStatus().name(), applied.getStatusUpdatedAt());
    }
}
