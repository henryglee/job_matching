package com.jobmatching.demo.scrape;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobmatching.demo.entity.JobListing;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * fixtures/scrape_response.json is a REAL response captured from a live run of
 * JobSpy-main/scrape_service.py (POST /scrape against actual Indeed listings) — not a
 * hand-written fixture. This checks the JSON *shape* itself is JobListing-compatible
 * (snake_case field names, ISO-datetime date_posted) using a standalone Jackson 2 mapper
 * built by hand here, independent of whatever the app wires up.
 *
 * It does NOT verify HttpJobScrapeClient's actual RestClient deserializes this correctly
 * — that requires the real, property-configured Jackson 3 `jacksonJsonMapper` bean, which
 * a hand-built mapper doesn't stand in for (see HttpJobScrapeClientJacksonWiringTest,
 * which caught a real bug here: a bare RestClient.builder() gets its own unconfigured
 * Jackson converter and silently drops every snake_case field).
 */
class ScrapeResponseDeserializationTest {

    private record ScrapeServiceResponse(List<JobListing> jobs) {
    }

    @Test
    void deserializesRealScrapeServiceResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        try (InputStream in = getClass().getResourceAsStream("/fixtures/scrape_response.json")) {
            assertNotNull(in, "fixture file missing");
            ScrapeServiceResponse response = objectMapper.readValue(in, ScrapeServiceResponse.class);

            assertEquals(2, response.jobs().size());
            JobListing first = response.jobs().get(0);
            assertFalse(first.getTitle().isBlank());
            assertNotNull(first.getCompanyName());
            assertNotNull(first.getJobUrl());
            assertNotNull(first.getDatePosted());
            assertEquals("indeed", first.getSite());
        }
    }
}
