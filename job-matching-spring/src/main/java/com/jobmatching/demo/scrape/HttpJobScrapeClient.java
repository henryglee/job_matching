package com.jobmatching.demo.scrape;

import com.jobmatching.demo.dto.ScrapeJobsRequest;
import com.jobmatching.demo.entity.JobListing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Wire contract satisfied by ../scrape-service/scrape_service.py:
 *   POST {jobspy.service.url}/scrape
 *   body: {"site_name": [...], "search_term": "...", "location": "...", "results_wanted": N}
 *   response: {"jobs": [ { ...same field names as job_listings columns... } ]}
 * Verified against a real captured response — see ScrapeResponseDeserializationTest.
 *
 * The injected JsonMapper matters: a bare RestClient.builder() gets its own fresh,
 * unconfigured JacksonJsonHttpMessageConverter (confirmed via RestClientJacksonDefaultTest),
 * not the property-customized `jacksonJsonMapper` bean the rest of the app uses. Without
 * explicitly wiring that bean in here, snake_case fields like job_url/company_name never
 * map onto JobListing's jobUrl/companyName and silently deserialize as null.
 */
@Component
public class HttpJobScrapeClient implements JobScrapeClient {

    private record ScrapeServiceResponse(List<JobListing> jobs) {
    }

    private record ExpiryRequest(List<String> jobKeys) {
    }

    private record ExpiryResponse(List<String> expired) {
    }

    // The expiry check runs while a candidate waits for their matches, so unlike a scrape
    // (which can legitimately take minutes) it gets a short timeout.
    private static final Duration EXPIRY_CHECK_TIMEOUT = Duration.ofSeconds(10);

    // A scrape can legitimately take minutes, but without any limit a hung scrape service would
    // pin the calling thread forever and, for a title scrape, leave that title marked in progress
    // (so it would never be scraped again until a restart).
    private static final Duration SCRAPE_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SCRAPE_READ_TIMEOUT = Duration.ofMinutes(10);

    private final RestClient restClient;
    private final RestClient expiryRestClient;

    public HttpJobScrapeClient(@Value("${jobspy.service.url}") String baseUrl, JsonMapper jsonMapper) {
        SimpleClientHttpRequestFactory scrapeRequestFactory = new SimpleClientHttpRequestFactory();
        scrapeRequestFactory.setConnectTimeout(SCRAPE_CONNECT_TIMEOUT);
        scrapeRequestFactory.setReadTimeout(SCRAPE_READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(scrapeRequestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
                    converters.add(new JacksonJsonHttpMessageConverter(jsonMapper));
                })
                .build();
        SimpleClientHttpRequestFactory expiryRequestFactory = new SimpleClientHttpRequestFactory();
        expiryRequestFactory.setConnectTimeout(EXPIRY_CHECK_TIMEOUT);
        expiryRequestFactory.setReadTimeout(EXPIRY_CHECK_TIMEOUT);
        this.expiryRestClient = restClient.mutate().requestFactory(expiryRequestFactory).build();
    }

    @Override
    public List<JobListing> scrape(ScrapeJobsRequest request) {
        ScrapeServiceResponse response = restClient.post()
                .uri("/scrape")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ScrapeJobsRequest(
                        request.siteNameOrDefault(),
                        request.searchTermOrDefault(),
                        request.locationOrDefault(),
                        request.resultsWantedOrDefault()))
                .retrieve()
                .body(ScrapeServiceResponse.class);
        return response == null || response.jobs() == null ? List.of() : response.jobs();
    }

    @Override
    public List<String> findExpiredIndeedJobKeys(Collection<String> jobKeys) {
        ExpiryResponse response = expiryRestClient.post()
                .uri("/indeed/expired")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExpiryRequest(List.copyOf(jobKeys)))
                .retrieve()
                .body(ExpiryResponse.class);
        return response == null || response.expired() == null ? List.of() : response.expired();
    }
}
