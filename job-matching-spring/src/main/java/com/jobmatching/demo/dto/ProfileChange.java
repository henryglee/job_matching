package com.jobmatching.demo.dto;

import java.util.List;

// A hypothetical edit to the candidate's profile for a what-if score. Null means "leave as is";
// a blank string clears the field. Nothing is saved.
public record ProfileChange(
        List<String> addSkills,
        List<String> removeSkills,
        String preferredJobTitle,
        String collegeDegree,
        String locationCity,
        String locationState,
        String locationCountry
) {
}
