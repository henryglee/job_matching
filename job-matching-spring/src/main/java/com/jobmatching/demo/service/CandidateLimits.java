package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.CandidateInfo;

import java.util.List;

/**
 * Size limits on a candidate profile. The text limits are the column lengths, so an over-long
 * value is refused with a clear 400 instead of failing at the database as a 500. Skills are
 * stored as JSON with no column limit, and feed the embedding text, so they're capped here.
 * The React forms use the same numbers (maxLength, SkillPicker).
 */
public final class CandidateLimits {

    public static final int NAME_MAX = 500;
    public static final int EMAIL_MAX = 500;
    public static final int DEGREE_MAX = 500;
    public static final int JOB_TITLE_MAX = 500;
    public static final int LOCATION_MAX = 200;
    public static final int RESUME_FILENAME_MAX = 500;
    public static final int RESUME_CONTENT_TYPE_MAX = 100;
    public static final int MAX_SKILLS = 50;
    public static final int SKILL_MAX = 100;

    private CandidateLimits() {
    }

    public static void validate(CandidateInfo c) {
        check("Full name", c.getCandidateName(), NAME_MAX);
        check("Email", c.getEmail(), EMAIL_MAX);
        check("College degree", c.getCollegeDegree(), DEGREE_MAX);
        check("Preferred job title", c.getPreferredJobTitle(), JOB_TITLE_MAX);
        check("City", c.getLocationCity(), LOCATION_MAX);
        check("State / Province", c.getLocationState(), LOCATION_MAX);
        check("Country", c.getLocationCountry(), LOCATION_MAX);
        check("Resume file name", c.getResumeFilename(), RESUME_FILENAME_MAX);
        check("Resume content type", c.getResumeContentType(), RESUME_CONTENT_TYPE_MAX);
        checkSkills(c.getSkills());
    }

    private static void checkSkills(List<String> skills) {
        if (skills == null) {
            return;
        }
        if (skills.size() > MAX_SKILLS) {
            throw new IllegalArgumentException("Add at most " + MAX_SKILLS + " skills.");
        }
        for (String skill : skills) {
            check("Each skill", skill, SKILL_MAX);
        }
    }

    private static void check(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters.");
        }
    }
}
