package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.scrape.ScrapeOrchestrationService;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmailService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PUT /api/candidates/{id}: a User's profile email must stay their own; Staff/Admin can set any. */
@WebMvcTest(CandidateController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class UpdateCandidateEmailTest {

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
    void userCannotSetSomeoneElsesEmail() throws Exception {
        update("User", "{\"email\": \"victim@example.com\"}").andExpect(status().isForbidden());
        verify(candidateService, never()).updateCandidate(any(), any());
    }

    @Test
    void userCanKeepOwnEmailOrClearIt() throws Exception {
        when(candidateService.updateCandidate(any(), any())).thenReturn(true);
        update("User", "{\"email\": \"Me@Example.com\"}").andExpect(status().isOk());
        update("User", "{\"email\": \"\"}").andExpect(status().isOk());
    }

    @Test
    void userCanResubmitTheEmailTheirProfileAlreadyHas() throws Exception {
        // A profile saved before the rule existed can hold an address other than the account's;
        // the edit page sends it back on every save.
        CandidateInfo existing = new CandidateInfo();
        existing.setId(CANDIDATE_ID);
        existing.setEmail("Old.Address@example.com");
        when(candidateService.getCandidateById(CANDIDATE_ID)).thenReturn(java.util.Optional.of(existing));
        when(candidateService.updateCandidate(any(), any())).thenReturn(true);

        update("User", "{\"email\": \"old.address@example.com\"}").andExpect(status().isOk());
        update("User", "{\"email\": \"someone.new@example.com\"}").andExpect(status().isForbidden());
    }

    @Test
    void userCanStillEditOtherFields() throws Exception {
        when(candidateService.updateCandidate(any(), any())).thenReturn(true);
        update("User", "{\"preferred_job_title\": \"Engineer\"}").andExpect(status().isOk());
    }

    @Test
    void staffCanSetAnyEmail() throws Exception {
        when(candidateService.updateCandidate(any(), any())).thenReturn(true);
        update("Staff", "{\"email\": \"candidate@example.com\"}").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions update(String role, String json) throws Exception {
        CandidateInfo profile = new CandidateInfo();
        profile.setId(CANDIDATE_ID);
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("me@example.com");
        user.setUsername("me");
        user.setCandidateProfile(profile);
        return mockMvc.perform(put("/api/candidates/" + CANDIDATE_ID)
                .header("Authorization", "Bearer " + jwtService.generateToken(user, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
