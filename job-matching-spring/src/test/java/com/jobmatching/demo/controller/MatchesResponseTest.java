package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.dto.MatchBreakdown;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmbeddingService;
import com.jobmatching.demo.service.JobEmbeddingBackfillService;
import com.jobmatching.demo.service.JobMatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The JSON shape of GET /api/candidates/{id}/matches that the React matches page reads:
 * candidate_location (for location sorting) and the snake_case match_breakdown keys.
 */
@WebMvcTest(JobMatchingController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000",
        "spring.jackson.property-naming-strategy=SNAKE_CASE"
})
class MatchesResponseTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private JobMatchingService jobMatchingService;

    @MockitoBean
    private CandidateService candidateService;

    @MockitoBean
    private EmbeddingService embeddingService;

    @MockitoBean
    private JobEmbeddingBackfillService jobEmbeddingBackfillService;

    @Test
    void includesCandidateLocationAndBreakdown() throws Exception {
        CandidateInfo candidate = new CandidateInfo();
        candidate.setLocationCity("Austin");
        candidate.setLocationState("TX");
        when(candidateService.getCandidateById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));

        JobListing job = new JobListing();
        job.setId(UUID.randomUUID());
        job.setTitle("Data Analyst");
        job.setExperienceRange("2-4 years");
        when(jobMatchingService.findMatchingJobs(CANDIDATE_ID, 10)).thenReturn(List.of(
                new JobMatchResult(job, 0.87, new MatchBreakdown(87.0, 50.0, 100.0, 66.7, List.of("SQL"), false, false, true))));

        mockMvc.perform(get("/api/candidates/" + CANDIDATE_ID + "/matches").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate_location.city").value("Austin"))
                .andExpect(jsonPath("$.candidate_location.state").value("TX"))
                .andExpect(jsonPath("$.candidate_location.country").value(""))
                .andExpect(jsonPath("$.matches[0].experience_range").value("2-4 years"))
                .andExpect(jsonPath("$.matches[0].match_breakdown.overall").value(87.0))
                .andExpect(jsonPath("$.matches[0].match_breakdown.skills").value(50.0))
                .andExpect(jsonPath("$.matches[0].match_breakdown.title_relevance").value(100.0))
                .andExpect(jsonPath("$.matches[0].match_breakdown.matched_skills[0]").value("SQL"))
                // Stand-in parts (nothing to compare) are flagged so the page can show them as n/a.
                .andExpect(jsonPath("$.matches[0].match_breakdown.skills_estimated").value(false))
                .andExpect(jsonPath("$.matches[0].match_breakdown.title_relevance_estimated").value(false))
                .andExpect(jsonPath("$.matches[0].match_breakdown.location_estimated").value(true));
        verify(jobEmbeddingBackfillService).backfillAsync();
    }

    @Test
    void unknownCandidateGetsEmptyLocation() throws Exception {
        when(candidateService.getCandidateById(CANDIDATE_ID)).thenReturn(Optional.empty());
        when(jobMatchingService.findMatchingJobs(CANDIDATE_ID, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/candidates/" + CANDIDATE_ID + "/matches").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isEmpty())
                .andExpect(jsonPath("$.candidate_location").isEmpty());
    }

    private String bearer() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("staff@example.com");
        user.setUsername("staff");
        return "Bearer " + jwtService.generateToken(user, "Staff");
    }
}
