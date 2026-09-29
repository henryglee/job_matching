package com.jobmatching.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "candidate_info")
public class CandidateInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "candidate_name", length = 500, nullable = false)
    private String candidateName;

    @Column(name = "email", length = 500)
    private String email;

    @Column(name = "college_degree", length = 500)
    private String collegeDegree;

    @Column(name = "graduation_date")
    private LocalDateTime graduationDate;

    @Column(name = "preferred_job_title", length = 500)
    private String preferredJobTitle;

    @Column(name = "location_city", length = 200)
    private String locationCity;

    @Column(name = "location_state", length = 200)
    private String locationState;

    @Column(name = "location_country", length = 200)
    private String locationCountry;

    @Column(name = "requires_sponsorship")
    private Boolean requiresSponsorship = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sponsorship_type")
    private List<String> sponsorshipType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills")
    private List<String> skills;

    @Column(name = "resume_filename", length = 500)
    private String resumeFilename;

    // Excluded from JSON responses (API_DOCUMENTATION.md's candidate payloads only ever
    // show resume_filename, never the Base64 blob) — set it via the setter, never read
    // it back through the API.
    @JsonIgnore
    @Column(name = "resume_data", columnDefinition = "TEXT")
    private String resumeData;

    @Column(name = "resume_content_type", length = 100)
    private String resumeContentType;

    @CreationTimestamp
    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    // `embedding` (vector(1536)) is intentionally not mapped here — Hibernate's schema
    // validator doesn't understand the pgvector column type. Embedding reads/writes go
    // through native SQL in the repository layer (see SPRINGBOOT_DB_CONFIG.md).

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCollegeDegree() {
        return collegeDegree;
    }

    public void setCollegeDegree(String collegeDegree) {
        this.collegeDegree = collegeDegree;
    }

    public LocalDateTime getGraduationDate() {
        return graduationDate;
    }

    public void setGraduationDate(LocalDateTime graduationDate) {
        this.graduationDate = graduationDate;
    }

    public String getPreferredJobTitle() {
        return preferredJobTitle;
    }

    public void setPreferredJobTitle(String preferredJobTitle) {
        this.preferredJobTitle = preferredJobTitle;
    }

    public String getLocationCity() {
        return locationCity;
    }

    public void setLocationCity(String locationCity) {
        this.locationCity = locationCity;
    }

    public String getLocationState() {
        return locationState;
    }

    public void setLocationState(String locationState) {
        this.locationState = locationState;
    }

    public String getLocationCountry() {
        return locationCountry;
    }

    public void setLocationCountry(String locationCountry) {
        this.locationCountry = locationCountry;
    }

    public Boolean getRequiresSponsorship() {
        return requiresSponsorship;
    }

    public void setRequiresSponsorship(Boolean requiresSponsorship) {
        this.requiresSponsorship = requiresSponsorship;
    }

    public List<String> getSponsorshipType() {
        return sponsorshipType;
    }

    public void setSponsorshipType(List<String> sponsorshipType) {
        this.sponsorshipType = sponsorshipType;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public String getResumeFilename() {
        return resumeFilename;
    }

    public void setResumeFilename(String resumeFilename) {
        this.resumeFilename = resumeFilename;
    }

    public String getResumeData() {
        return resumeData;
    }

    public void setResumeData(String resumeData) {
        this.resumeData = resumeData;
    }

    public String getResumeContentType() {
        return resumeContentType;
    }

    public void setResumeContentType(String resumeContentType) {
        this.resumeContentType = resumeContentType;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public LocalDateTime getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(LocalDateTime dateUpdated) {
        this.dateUpdated = dateUpdated;
    }
}
