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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/candidates/unclaimed and POST /api/user/claim-profile: Admin/Staff see every
 * unclaimed profile; a User only sees, and can only claim, ones with their account email.
 */
@WebMvcTest(CandidateController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class UnclaimedProfilesTest {

    private static final String USER_EMAIL = "ada@example.com";

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
    void staffSeesEveryUnclaimedProfile() throws Exception {
        when(candidateService.getUnclaimedCandidates()).thenReturn(List.of(candidate("Ada", USER_EMAIL), candidate("Alan", "alan@example.com")));

        mockMvc.perform(get("/api/candidates/unclaimed").header("Authorization", bearer(UUID.randomUUID(), "Staff", "staff@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(2));
    }

    @Test
    void userOnlySeesProfilesWithTheirOwnEmail() throws Exception {
        when(candidateService.findUnclaimedCandidatesByEmail(USER_EMAIL)).thenReturn(List.of(candidate("Ada", USER_EMAIL)));

        mockMvc.perform(get("/api/candidates/unclaimed").header("Authorization", bearer(UUID.randomUUID(), "User", USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].candidate_name").value("Ada"));
        verify(candidateService, never()).getUnclaimedCandidates();
    }

    @Test
    void userCanClaimAProfileWithTheirEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        CandidateInfo mine = candidate("Ada", USER_EMAIL);
        when(candidateService.findUnclaimedCandidatesByEmail(USER_EMAIL)).thenReturn(List.of(mine));

        mockMvc.perform(claim(userId, mine.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile claimed successfully"));
        verify(userAccountService).associateCandidateProfile(userId, mine.getId());
    }

    @Test
    void claimingReturnsANewTokenThatCarriesTheProfile() throws Exception {
        // The client's token predates the claim, so without a fresh one the profile would be
        // forgotten on the next page reload.
        UUID userId = UUID.randomUUID();
        CandidateInfo mine = candidate("Ada", USER_EMAIL);
        when(candidateService.findUnclaimedCandidatesByEmail(USER_EMAIL)).thenReturn(List.of(mine));
        UserAccount linked = new UserAccount();
        linked.setId(userId);
        linked.setEmail(USER_EMAIL);
        linked.setUsername("ada");
        linked.setCandidateProfile(mine);
        when(userAccountService.getUserWithProfile(userId)).thenReturn(java.util.Optional.of(linked));

        String body = mockMvc.perform(claim(userId, mine.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        assertEquals(mine.getId().toString(), jwtService.parseClaims(token).get("candidateProfileId", String.class));
        assertEquals("User", jwtService.parseClaims(token).get("activeRole", String.class));
    }

    @Test
    void userCannotClaimSomeoneElsesProfileById() throws Exception {
        CandidateInfo someoneElse = candidate("Alan", "alan@example.com");
        when(candidateService.getUnclaimedCandidates()).thenReturn(List.of(someoneElse));
        when(candidateService.findUnclaimedCandidatesByEmail(USER_EMAIL)).thenReturn(List.of());

        mockMvc.perform(claim(UUID.randomUUID(), someoneElse.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Profile not found or already claimed"));
        verify(userAccountService, never()).associateCandidateProfile(any(), any());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder claim(UUID userId, UUID candidateId) {
        return post("/api/user/claim-profile")
                .header("Authorization", bearer(userId, "User", USER_EMAIL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidate_id\": \"" + candidateId + "\"}");
    }

    private static CandidateInfo candidate(String name, String email) {
        CandidateInfo c = new CandidateInfo();
        c.setId(UUID.randomUUID());
        c.setCandidateName(name);
        c.setEmail(email);
        return c;
    }

    private String bearer(UUID userId, String role, String email) {
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail(email);
        user.setUsername("someone");
        return "Bearer " + jwtService.generateToken(user, role);
    }
}
