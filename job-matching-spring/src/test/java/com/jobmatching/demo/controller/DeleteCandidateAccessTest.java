package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.scrape.ScrapeOrchestrationService;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmailService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DELETE /api/candidates/{id}: Admin or Staff, as in app.py. */
@WebMvcTest(CandidateController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class DeleteCandidateAccessTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CandidateService candidateService;
    @MockitoBean
    private UserAccountService userAccountService;
    @MockitoBean
    private JobMatchingService jobMatchingService;
    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private ScrapeOrchestrationService scrapeOrchestrationService;

    @Test
    void staffCanDelete() throws Exception {
        when(candidateService.deleteCandidate(CANDIDATE_ID)).thenReturn(true);
        mockMvc.perform(delete("/api/candidates/" + CANDIDATE_ID).header("Authorization", bearer("Staff")))
                .andExpect(status().isOk());
        verify(candidateService).deleteCandidate(CANDIDATE_ID);
    }

    @Test
    void adminCanDelete() throws Exception {
        when(candidateService.deleteCandidate(CANDIDATE_ID)).thenReturn(true);
        mockMvc.perform(delete("/api/candidates/" + CANDIDATE_ID).header("Authorization", bearer("Admin")))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/candidates/" + CANDIDATE_ID).header("Authorization", bearer("User")))
                .andExpect(status().isForbidden());
        verify(candidateService, never()).deleteCandidate(any());
    }

    private String bearer(String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        return "Bearer " + jwtService.generateToken(user, role);
    }
}
