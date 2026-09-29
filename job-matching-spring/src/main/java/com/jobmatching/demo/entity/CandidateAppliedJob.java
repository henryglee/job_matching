package com.jobmatching.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// A job the candidate marked as applied to. The job's details are copied in when it's
// marked, since the retention cleanup can delete the listing later (job_id then becomes null).
@Entity
@Table(name = "candidate_applied_jobs")
public class CandidateAppliedJob {

    // Where the application stands, set by the candidate on the Applied Jobs page. WAITING is
    // after the interviews, while the candidate waits to hear whether it's an offer or not.
    // Stored by name in a varchar(16), so a new status needs no schema change if it fits.
    public enum Status { APPLIED, INTERVIEWING, WAITING, OFFER, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "candidate_id", columnDefinition = "uuid", nullable = false)
    private UUID candidateId;

    @Column(name = "job_id", columnDefinition = "uuid")
    private UUID jobId;

    @Column(name = "job_title", length = 500)
    private String jobTitle;

    @Column(name = "company_name", length = 500)
    private String companyName;

    @Column(name = "location", length = 1000)
    private String location;

    @Column(name = "job_url", columnDefinition = "TEXT")
    private String jobUrl;

    @CreationTimestamp
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.APPLIED;

    // When the candidate last changed the status; null while it's still the initial APPLIED.
    @Column(name = "status_updated_at")
    private LocalDateTime statusUpdatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void setStatusUpdatedAt(LocalDateTime statusUpdatedAt) {
        this.statusUpdatedAt = statusUpdatedAt;
    }
}
