package com.jobmatching.demo.dto;

import java.util.List;

public record ScrapeSummary(int jobsScraped, int jobsSaved, int embeddingsGenerated, List<String> sites) {
}
