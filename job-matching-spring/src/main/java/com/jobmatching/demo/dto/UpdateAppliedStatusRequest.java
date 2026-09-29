package com.jobmatching.demo.dto;

import com.jobmatching.demo.entity.CandidateAppliedJob;
import jakarta.validation.constraints.NotNull;

// An unknown status name fails to parse and is answered 400 like any unreadable body.
public record UpdateAppliedStatusRequest(
        @NotNull(message = "Status is required.")
        CandidateAppliedJob.Status status
) {
}
