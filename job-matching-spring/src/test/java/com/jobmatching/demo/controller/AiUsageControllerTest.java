package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.AiUsageEvent;
import com.jobmatching.demo.service.AiUsageMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/admin/ai-usage: Admin only, counts only (no message content). */
@WebMvcTest(AiUsageController.class)
@Import({SecurityConfig.class, JwtService.class, AiUsageMonitor.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000",
        "spring.jackson.property-naming-strategy=SNAKE_CASE"
})
class AiUsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AiUsageMonitor monitor;

    @MockitoBean
    private CandidateInfoRepository candidateInfoRepository;

    @Test
    void adminSeesUsagePerCandidateWithNames() throws Exception {
        UUID candidateId = UUID.randomUUID();
        monitor.record(candidateId, AiUsageEvent.CHAT_MESSAGE, 420);
        monitor.record(candidateId, AiUsageEvent.MODERATION_BLOCKED);
        CandidateInfo candidate = new CandidateInfo();
        candidate.setId(candidateId);
        candidate.setCandidateName("Sam Lee");
        when(candidateInfoRepository.findAllById(any())).thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/admin/ai-usage").header("Authorization", bearer("Admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.since").exists())
                .andExpect(jsonPath("$.totals.chat_messages").value(1))
                .andExpect(jsonPath("$.totals.model_tokens").value(420))
                .andExpect(jsonPath("$.candidates[0].candidate_name").value("Sam Lee"))
                .andExpect(jsonPath("$.candidates[0].usage.moderation_blocked").value(1))
                .andExpect(jsonPath("$.candidates[0].needs_review").value(true));
    }

    @Test
    void staffAndCandidatesCannotSeeIt() throws Exception {
        mockMvc.perform(get("/api/admin/ai-usage").header("Authorization", bearer("Staff"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/ai-usage").header("Authorization", bearer("User"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/ai-usage")).andExpect(status().isUnauthorized());
    }

    private String bearer(String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        return "Bearer " + jwtService.generateToken(user, role);
    }
}
