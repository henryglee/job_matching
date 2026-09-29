package com.jobmatching.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "job_listings")
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "company_name", length = 500)
    private String companyName;

    @Column(name = "job_url", columnDefinition = "TEXT", nullable = false)
    private String jobUrl;

    @Column(name = "job_url_direct", columnDefinition = "TEXT")
    private String jobUrlDirect;

    @Column(name = "location", length = 1000)
    private String location;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "company_url", columnDefinition = "TEXT")
    private String companyUrl;

    @Column(name = "company_url_direct", columnDefinition = "TEXT")
    private String companyUrlDirect;

    @Column(name = "company_addresses", columnDefinition = "TEXT")
    private String companyAddresses;

    @Column(name = "company_num_employees", length = 200)
    private String companyNumEmployees;

    @Column(name = "company_revenue", length = 200)
    private String companyRevenue;

    @Column(name = "company_description", columnDefinition = "TEXT")
    private String companyDescription;

    @Column(name = "company_logo", columnDefinition = "TEXT")
    private String companyLogo;

    @Column(name = "company_industry", length = 500)
    private String companyIndustry;

    @Column(name = "company_rating")
    private Float companyRating;

    @Column(name = "company_reviews_count")
    private Integer companyReviewsCount;

    @Column(name = "job_type", length = 200)
    private String jobType;

    @Column(name = "job_level", length = 200)
    private String jobLevel;

    @Column(name = "job_function", length = 500)
    private String jobFunction;

    @Column(name = "is_remote")
    private Boolean isRemote;

    @Column(name = "listing_type", length = 100)
    private String listingType;

    @Column(name = "interval", length = 50)
    private String interval;

    @Column(name = "min_amount")
    private Float minAmount;

    @Column(name = "max_amount")
    private Float maxAmount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "salary_source", length = 100)
    private String salarySource;

    @Column(name = "date_posted")
    private LocalDateTime datePosted;

    @CreationTimestamp
    @Column(name = "date_scraped")
    private LocalDateTime dateScraped;

    @Column(name = "site", length = 100, nullable = false)
    private String site;

    @Column(name = "emails", columnDefinition = "TEXT")
    private String emails;

    @Column(name = "banner_photo_url", columnDefinition = "TEXT")
    private String bannerPhotoUrl;

    // JSON string of skill names (application-level serialized, not a JSON column) —
    // matches jobspy/database.py's `skills = Column(Text)` on JobListing.
    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    @Column(name = "experience_range", length = 200)
    private String experienceRange;

    @Column(name = "vacancy_count")
    private Integer vacancyCount;

    @Column(name = "work_from_home_type", length = 100)
    private String workFromHomeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_metadata")
    private Map<String, Object> jobMetadata;

    // Set when the job stops accepting applications (see JobExpiryService, which writes it via
    // native SQL). Mapped only so ddl-auto=validate checks the column exists; no accessors, so
    // it stays out of API responses and scrape-result deserialization.
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    // `embedding` (vector(1536)) is intentionally not mapped here — see CandidateInfo
    // for why; embedding reads/writes go through native SQL in the repository layer.

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public String getJobUrlDirect() {
        return jobUrlDirect;
    }

    public void setJobUrlDirect(String jobUrlDirect) {
        this.jobUrlDirect = jobUrlDirect;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCompanyUrl() {
        return companyUrl;
    }

    public void setCompanyUrl(String companyUrl) {
        this.companyUrl = companyUrl;
    }

    public String getCompanyUrlDirect() {
        return companyUrlDirect;
    }

    public void setCompanyUrlDirect(String companyUrlDirect) {
        this.companyUrlDirect = companyUrlDirect;
    }

    public String getCompanyAddresses() {
        return companyAddresses;
    }

    public void setCompanyAddresses(String companyAddresses) {
        this.companyAddresses = companyAddresses;
    }

    public String getCompanyNumEmployees() {
        return companyNumEmployees;
    }

    public void setCompanyNumEmployees(String companyNumEmployees) {
        this.companyNumEmployees = companyNumEmployees;
    }

    public String getCompanyRevenue() {
        return companyRevenue;
    }

    public void setCompanyRevenue(String companyRevenue) {
        this.companyRevenue = companyRevenue;
    }

    public String getCompanyDescription() {
        return companyDescription;
    }

    public void setCompanyDescription(String companyDescription) {
        this.companyDescription = companyDescription;
    }

    public String getCompanyLogo() {
        return companyLogo;
    }

    public void setCompanyLogo(String companyLogo) {
        this.companyLogo = companyLogo;
    }

    public String getCompanyIndustry() {
        return companyIndustry;
    }

    public void setCompanyIndustry(String companyIndustry) {
        this.companyIndustry = companyIndustry;
    }

    public Float getCompanyRating() {
        return companyRating;
    }

    public void setCompanyRating(Float companyRating) {
        this.companyRating = companyRating;
    }

    public Integer getCompanyReviewsCount() {
        return companyReviewsCount;
    }

    public void setCompanyReviewsCount(Integer companyReviewsCount) {
        this.companyReviewsCount = companyReviewsCount;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getJobLevel() {
        return jobLevel;
    }

    public void setJobLevel(String jobLevel) {
        this.jobLevel = jobLevel;
    }

    public String getJobFunction() {
        return jobFunction;
    }

    public void setJobFunction(String jobFunction) {
        this.jobFunction = jobFunction;
    }

    public Boolean getIsRemote() {
        return isRemote;
    }

    public void setIsRemote(Boolean isRemote) {
        this.isRemote = isRemote;
    }

    public String getListingType() {
        return listingType;
    }

    public void setListingType(String listingType) {
        this.listingType = listingType;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public Float getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(Float minAmount) {
        this.minAmount = minAmount;
    }

    public Float getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(Float maxAmount) {
        this.maxAmount = maxAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSalarySource() {
        return salarySource;
    }

    public void setSalarySource(String salarySource) {
        this.salarySource = salarySource;
    }

    public LocalDateTime getDatePosted() {
        return datePosted;
    }

    public void setDatePosted(LocalDateTime datePosted) {
        this.datePosted = datePosted;
    }

    public LocalDateTime getDateScraped() {
        return dateScraped;
    }

    public void setDateScraped(LocalDateTime dateScraped) {
        this.dateScraped = dateScraped;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getEmails() {
        return emails;
    }

    public void setEmails(String emails) {
        this.emails = emails;
    }

    public String getBannerPhotoUrl() {
        return bannerPhotoUrl;
    }

    public void setBannerPhotoUrl(String bannerPhotoUrl) {
        this.bannerPhotoUrl = bannerPhotoUrl;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getExperienceRange() {
        return experienceRange;
    }

    public void setExperienceRange(String experienceRange) {
        this.experienceRange = experienceRange;
    }

    public Integer getVacancyCount() {
        return vacancyCount;
    }

    public void setVacancyCount(Integer vacancyCount) {
        this.vacancyCount = vacancyCount;
    }

    public String getWorkFromHomeType() {
        return workFromHomeType;
    }

    public void setWorkFromHomeType(String workFromHomeType) {
        this.workFromHomeType = workFromHomeType;
    }

    public Map<String, Object> getJobMetadata() {
        return jobMetadata;
    }

    public void setJobMetadata(Map<String, Object> jobMetadata) {
        this.jobMetadata = jobMetadata;
    }
}
