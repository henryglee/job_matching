package com.jobmatching.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the two things this app actually relies on from the auto-configured Jackson 3
 * `jacksonJsonMapper` bean — the single JSON engine used everywhere now (SecurityConfig,
 * JobMatchingController, and every @RestController response), after retiring the parallel
 * hand-built Jackson 2 ObjectMapper this project used to carry: snake_case field names
 * (from spring.jackson.property-naming-strategy in application.properties) and ISO-string
 * date serialization (Jackson 3's default — no JavaTimeModule needed, unlike Jackson 2).
 *
 * No DataSource/JPA involved, so this runs without a database.
 */
class JacksonMessageConverterWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE");

    record TestBean(String someField) {
    }

    record TestBeanWithDate(LocalDateTime datePosted) {
    }

    @Test
    void honorsSnakeCasePropertyFromApplicationProperties() {
        contextRunner.run(context -> {
            JsonMapper jsonMapper = context.getBean("jacksonJsonMapper", JsonMapper.class);
            String json = jsonMapper.writeValueAsString(new TestBean("x"));
            assertTrue(json.contains("some_field"), "expected snake_case field name, got: " + json);
            assertFalse(json.contains("someField"), "unexpected camelCase field name, got: " + json);
        });
    }

    @Test
    void serializesLocalDateTimeAsIsoStringNotArray() {
        // JobMatchingController#flatten runs JobListing through this exact bean via
        // convertValue() before returning the result for the real HTTP response.
        contextRunner.run(context -> {
            JsonMapper jsonMapper = context.getBean("jacksonJsonMapper", JsonMapper.class);
            Map<?, ?> asMap = jsonMapper.convertValue(new TestBeanWithDate(LocalDateTime.of(2026, 9, 15, 0, 0)), Map.class);
            assertTrue(asMap.get("date_posted") instanceof String,
                    "expected an ISO string, got: " + asMap.get("date_posted"));
            assertTrue(((String) asMap.get("date_posted")).startsWith("2026-09-15"));
        });
    }
}
