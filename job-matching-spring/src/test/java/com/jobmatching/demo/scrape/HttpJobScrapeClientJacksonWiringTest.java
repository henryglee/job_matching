package com.jobmatching.demo.scrape;

import com.jobmatching.demo.entity.JobListing;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression test for a real bug: a bare {@code RestClient.builder()} (what
 * HttpJobScrapeClient used before this fix) gets its own fresh, unconfigured
 * JacksonJsonHttpMessageConverter — not the property-customized `jacksonJsonMapper` bean
 * used everywhere else in the app — so snake_case fields (job_url, company_name) silently
 * deserialized as null. This builds the client the same way Spring would (real
 * auto-configured JsonMapper injected) and checks it against the same real captured
 * fixture ScrapeResponseDeserializationTest uses.
 *
 * No DataSource/JPA involved, so this runs without a database.
 */
class HttpJobScrapeClientJacksonWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE");

    @Test
    void deserializesRealFixtureWithSnakeCaseFieldsPopulated() {
        contextRunner.run(context -> {
            JsonMapper jsonMapper = context.getBean("jacksonJsonMapper", JsonMapper.class);
            HttpJobScrapeClient client = new HttpJobScrapeClient("http://localhost:5000", jsonMapper);

            List<JobListing> jobs = readFixtureThroughClientsRestClient(client);

            assertEquals(2, jobs.size());
            JobListing first = jobs.get(0);
            assertNotNull(first.getCompanyName(), "companyName was null — snake_case JSON did not map");
            assertNotNull(first.getJobUrl(), "jobUrl was null — snake_case JSON did not map");
            assertEquals("indeed", first.getSite());
        });
    }

    // HttpJobScrapeClient's RestClient always calls a live HTTP endpoint, so to reuse the
    // exact same JacksonJsonHttpMessageConverter it configured (rather than duplicate that
    // setup here and test something slightly different), pull it out via reflection and
    // read the fixture directly through it.
    private static List<JobListing> readFixtureThroughClientsRestClient(HttpJobScrapeClient client) throws Exception {
        Field restClientField = HttpJobScrapeClient.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        RestClient restClient = (RestClient) restClientField.get(client);

        Field convertersField = findConvertersField(restClient.getClass());
        convertersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HttpMessageConverter<?>> converters = (List<HttpMessageConverter<?>>) convertersField.get(restClient);
        JacksonJsonHttpMessageConverter jacksonConverter = converters.stream()
                .filter(JacksonJsonHttpMessageConverter.class::isInstance)
                .map(JacksonJsonHttpMessageConverter.class::cast)
                .findFirst()
                .orElseThrow();

        record ScrapeResponse(List<JobListing> jobs) {
        }
        try (InputStream in = HttpJobScrapeClientJacksonWiringTest.class.getResourceAsStream("/fixtures/scrape_response.json")) {
            assertNotNull(in, "fixture missing");
            return jacksonConverter.getMapper().<ScrapeResponse>readValue(in, ScrapeResponse.class).jobs();
        }
    }

    private static Field findConvertersField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            for (Field f : current.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType()) && f.getName().toLowerCase().contains("convert")) {
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException("no converters-like field found on " + type);
    }
}
