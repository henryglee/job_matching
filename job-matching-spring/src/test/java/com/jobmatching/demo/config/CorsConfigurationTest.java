package com.jobmatching.demo.config;

import com.jobmatching.demo.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigurationTest {

    // Mirrors SecurityConfig's actual @Value binding expression exactly. SecurityConfig
    // itself can't be loaded directly here: its securityFilterChain(HttpSecurity) bean method
    // would be eagerly instantiated too, and this non-web test context has no HttpSecurity
    // bean to satisfy it. This isolates just the binding behavior we actually need to verify.
    @Configuration
    static class OriginsProbe {
        @Bean
        List<String> allowedOrigins(@Value("#{'${app.cors.allowed-origins}'.split(',')}") List<String> origins) {
            return origins;
        }
    }

    @Test
    void commaSeparatedPropertySplitsIntoList() {
        // Plain @Value("${...}") List<String> binding does NOT split on commas — it binds
        // the whole literal string as a single-element list. Confirmed the hard way while
        // building this: that would have silently broken CORS for any multi-origin config,
        // with the single-origin default masking it completely. The SpEL .split(',') form
        // above is what SecurityConfig actually uses, and what this test guards.
        new ApplicationContextRunner()
                .withUserConfiguration(OriginsProbe.class)
                .withPropertyValues("app.cors.allowed-origins=http://localhost:3000,https://app.example.com")
                .run(context -> {
                    @SuppressWarnings("unchecked")
                    List<String> origins = context.getBean(List.class);
                    assertEquals(List.of("http://localhost:3000", "https://app.example.com"), origins);
                });
    }

    @Test
    void allowsConfiguredOriginWithCommonMethodsAndNoCredentials() {
        SecurityConfig securityConfig = new SecurityConfig(
                Mockito.mock(JwtAuthenticationFilter.class),
                Mockito.mock(JsonMapper.class),
                List.of("http://localhost:3000"));

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/candidates");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertTrue(config.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowedMethods().contains("DELETE"));
        assertTrue(config.getAllowedHeaders().contains("Authorization"));
        // No cookies used (JWT via Authorization header), so credentials should stay off.
        assertEquals(Boolean.FALSE, config.getAllowCredentials());
    }
}
