package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.entity.CandidateInfo;
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

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/candidates/{id}/generate-embedding through the real security chain (real signed
 * JWTs), with the services mocked. No database or OpenAI needed.
 */
@WebMvcTest(JobMatchingController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class GenerateCandidateEmbeddingTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private static final String URL = "/api/candidates/" + CANDIDATE_ID + "/generate-embedding";

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
    void staffCanGenerateEmbedding() throws Exception {
        when(candidateService.getCandidateById(CANDIDATE_ID)).thenReturn(Optional.of(new CandidateInfo()));
        when(embeddingService.generateCandidateEmbedding(CANDIDATE_ID)).thenReturn(true);

        mockMvc.perform(post(URL).header("Authorization", bearer("Staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Embedding generated successfully"))
                .andExpect(jsonPath("$.candidate_id").value(CANDIDATE_ID.toString()));
    }

    @Test
    void unknownCandidateIs404WithoutCallingOpenAi() throws Exception {
        when(candidateService.getCandidateById(CANDIDATE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post(URL).header("Authorization", bearer("Admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Candidate not found"));
        verify(embeddingService, never()).generateCandidateEmbedding(any());
    }

    @Test
    void embeddingFailureIs502() throws Exception {
        when(candidateService.getCandidateById(CANDIDATE_ID)).thenReturn(Optional.of(new CandidateInfo()));
        when(embeddingService.generateCandidateEmbedding(CANDIDATE_ID)).thenReturn(false);

        mockMvc.perform(post(URL).header("Authorization", bearer("Admin")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void regularUserIsForbidden() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", bearer("User")))
                .andExpect(status().isForbidden());
        verify(embeddingService, never()).generateCandidateEmbedding(any());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
    }

    private String bearer(String activeRole) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        return "Bearer " + jwtService.generateToken(user, activeRole);
    }
}
