package com.jobmatching.demo.config;

import com.jobmatching.demo.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Stateless JWT security, replacing Flask-Login's session cookie. Role checks live on
 * individual controller methods via @PreAuthorize (see RBAC notes in
 * API_DOCUMENTATION.md) rather than here, matching the per-route require_role() decorator
 * pattern in app.py.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/register",
            "/verify-email",
            "/login",
            "/api/auth/check-roles",
            "/forgot-password",
            "/reset-password/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonMapper jsonMapper;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           JsonMapper jsonMapper,
                           // Plain @Value("${...}") binding to List<String> does NOT split on
                           // commas here — confirmed via CorsConfigurationTest, it produces a
                           // single-element list containing the whole literal string, which
                           // would silently break CORS for any multi-origin config. The SpEL
                           // .split(',') form is the reliable way to get an actual split list.
                           @Value("#{'${app.cors.allowed-origins}'.split(',')}") List<String> allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jsonMapper = jsonMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // MCP's streamed (SSE) responses finish on an ASYNC dispatch.
                        // JwtAuthenticationFilter, a OncePerRequestFilter, doesn't run again
                        // there, so without this the dispatch is denied after the response has
                        // started. The original request was already authorized.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonAuthenticationEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // The React app (job-matching-react) proxies API calls through Vite's dev server today
    // (see its vite.config.ts), so the browser never actually needs cross-origin requests
    // in dev — but a production build served from a different origin than the API will.
    // No cookies are used (JWT is sent via the Authorization header), so credentials stay
    // disabled; that also means allowedOrigins can't be "*" together with credentials, but
    // since we don't need credentials here that restriction doesn't apply anyway.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Not readable cross-origin by default; lets a browser client on another origin see it.
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Spring Security's default for a missing/invalid token on a protected endpoint is a
    // bare 403 with no body. API_DOCUMENTATION.md's error table expects 401 + {"error": "..."}
    // for unauthenticated requests, so this overrides that default explicitly.
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            jsonMapper.writeValue(response.getWriter(), Map.of("error", "Authentication required"));
        };
    }
}
