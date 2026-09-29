package com.jobmatching.demo.dto;

import java.util.List;

public record ScrapeJobsRequest(
        List<String> siteName,
        String searchTerm,
        String location,
        Integer resultsWanted
) {
    private static final List<String> DEFAULT_SITES = List.of("indeed", "google");

    public List<String> siteNameOrDefault() {
        return siteName == null || siteName.isEmpty() ? DEFAULT_SITES : siteName;
    }

    public String searchTermOrDefault() {
        return searchTerm == null || searchTerm.isBlank() ? "software engineer" : searchTerm;
    }

    public String locationOrDefault() {
        return location == null || location.isBlank() ? "United States" : location;
    }

    public int resultsWantedOrDefault() {
        // Passed straight to the scrape service, which is slow and rate-limited by the job sites.
        return resultsWanted == null ? 50 : Math.max(1, Math.min(resultsWanted, 200));
    }
}
