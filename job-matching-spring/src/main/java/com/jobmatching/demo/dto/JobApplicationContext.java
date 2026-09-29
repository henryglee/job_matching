package com.jobmatching.demo.dto;

import java.util.List;
import java.util.UUID;

// What an AI client gets to write an application email with: the job posting and the
// candidate's profile. resume_data is deliberately left out. It's the raw uploaded file,
// not text, and the most sensitive field on the profile.
public record JobApplicationContext(
        UUID jobId,
        String title,
        String companyName,
        String location,
        String description,
        String companyDescription,
        List<String> contactEmails,
        String applyUrl,
        String candidateName,
        String collegeDegree,
        String preferredJobTitle,
        List<String> candidateSkills,
        List<String> overlappingSkills
) {

    // A copy with the job description cut to maxChars. Scraped descriptions can be very long;
    // the start says what the role is and keeps model prompts (and their cost) bounded.
    public JobApplicationContext withDescriptionLimit(int maxChars) {
        if (description == null || description.length() <= maxChars) {
            return this;
        }
        return new JobApplicationContext(jobId, title, companyName, location,
                description.substring(0, maxChars) + " …", companyDescription, contactEmails, applyUrl,
                candidateName, collegeDegree, preferredJobTitle, candidateSkills, overlappingSkills);
    }
}
