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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/candidates (multipart, as the forms send it), parsed the way app.py's
 * create_candidate does: location/sponsorship fields and the User-role name fallback.
 * Services are mocked; no database.
 */
@WebMvcTest(CandidateController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class CreateCandidateTest {

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

    @BeforeEach
    void setUp() {
        when(candidateService.createCandidateWithEmbedding(any())).thenAnswer(inv -> {
            CandidateInfo c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
    }

    @Test
    void savesTrimmedLocationAndSponsorshipTypes() throws Exception {
        CandidateInfo saved = create(form()
                .param("location_city", "  Austin ")
                .param("location_state", "TX")
                .param("location_country", "United States")
                .param("requires_sponsorship", "true")
                .param("sponsorship_type", "Need H1B", "U.S. Citizen with green card"));

        assertEquals("Austin", saved.getLocationCity());
        assertEquals("TX", saved.getLocationState());
        assertEquals("United States", saved.getLocationCountry());
        assertTrue(saved.getRequiresSponsorship());
        assertEquals(List.of("Need H1B", "U.S. Citizen with green card"), saved.getSponsorshipType());
    }

    @Test
    void blankLocationBecomesNull() throws Exception {
        CandidateInfo saved = create(form().param("location_city", "   "));

        assertNull(saved.getLocationCity());
        assertNull(saved.getLocationState());
    }

    @Test
    void sponsorshipTypesAreDroppedWhenSponsorshipIsNotRequired() throws Exception {
        CandidateInfo saved = create(form().param("sponsorship_type", "Need H1B"));

        assertFalse(saved.getRequiresSponsorship());
        assertEquals(List.of(), saved.getSponsorshipType());
    }

    @Test
    void userWithBlankNameGetsTheirAccountName() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount();
        account.setFirstName("Ada");
        account.setLastName("Lovelace");
        when(userAccountService.getUserById(userId)).thenReturn(Optional.of(account));

        CandidateInfo saved = create(form(userId, "User", "  "));

        assertEquals("Ada Lovelace", saved.getCandidateName());
    }

    @Test
    void userWithNoNameAnywhereIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userAccountService.getUserById(userId)).thenReturn(Optional.of(new UserAccount()));

        mockMvc.perform(form(userId, "User", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Candidate name is required"));
        verify(candidateService, never()).createCandidateWithEmbedding(any());
    }

    @Test
    void staffMustAlwaysGiveAName() throws Exception {
        // No fallback for Admin/Staff (they're entering someone else's profile), and a
        // missing field is a 400 — it used to surface as a 500.
        mockMvc.perform(form(UUID.randomUUID(), "Staff", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Candidate name is required"));
        verify(userAccountService, never()).getUserById(any());
    }

    @Test
    void userCannotUseSomeoneElsesEmail() throws Exception {
        mockMvc.perform(form(UUID.randomUUID(), "User", "Ada").param("email", "victim@example.com"))
                .andExpect(status().isForbidden());
        verify(candidateService, never()).createCandidateWithEmbedding(any());
        verify(emailService, never()).sendJobMatches(any(), any(), any());
    }

    @Test
    void userGetsMatchesEmailedToTheirOwnAddress() throws Exception {
        mockMvc.perform(form(UUID.randomUUID(), "User", "Ada").param("email", "Someone@Example.com"))
                .andExpect(status().isCreated());
        verify(emailService).sendJobMatches(any(), eq("Someone@Example.com"), any());
    }

    @Test
    void userGetsANewTokenCarryingTheirNewProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount();
        account.setId(userId);
        account.setEmail("someone@example.com");
        account.setUsername("someone");
        when(userAccountService.getUserWithProfile(userId)).thenReturn(Optional.of(account));

        mockMvc.perform(form(userId, "User", "Ada"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Staff create profiles for other people, so their own token is left alone.
        mockMvc.perform(form(UUID.randomUUID(), "Staff", "Alan"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void userWhoAlreadyHasAProfileCannotCreateAnother() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        CandidateInfo existing = new CandidateInfo();
        existing.setId(UUID.randomUUID());
        user.setCandidateProfile(existing);

        mockMvc.perform(multipart("/api/candidates").param("candidate_name", "Ada")
                        .header("Authorization", "Bearer " + jwtService.generateToken(user, "User")))
                .andExpect(status().isConflict());
        verify(candidateService, never()).createCandidateWithEmbedding(any());
    }

    @Test
    void userWithNoEmailGetsTheirAccountEmailOnTheProfile() throws Exception {
        mockMvc.perform(form(UUID.randomUUID(), "User", "Ada")).andExpect(status().isCreated());
        ArgumentCaptor<CandidateInfo> captor = ArgumentCaptor.forClass(CandidateInfo.class);
        verify(candidateService).createCandidateWithEmbedding(captor.capture());
        assertEquals("someone@example.com", captor.getValue().getEmail());
    }

    @Test
    void staffCreatingAProfileWithNoEmailLeavesItBlank() throws Exception {
        assertNull(create(form()).getEmail());
    }

    @Test
    void staffCanEmailMatchesToAnyAddress() throws Exception {
        mockMvc.perform(form().param("email", "candidate@example.com"))
                .andExpect(status().isCreated());
        verify(emailService).sendJobMatches(any(), eq("candidate@example.com"), any());
    }

    private CandidateInfo create(MockMultipartHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isCreated());
        ArgumentCaptor<CandidateInfo> captor = ArgumentCaptor.forClass(CandidateInfo.class);
        verify(candidateService).createCandidateWithEmbedding(captor.capture());
        return captor.getValue();
    }

    private MockMultipartHttpServletRequestBuilder form() {
        return form(UUID.randomUUID(), "Staff", "Ada Lovelace");
    }

    private MockMultipartHttpServletRequestBuilder form(UUID userId, String role, String candidateName) {
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        MockMultipartHttpServletRequestBuilder request = multipart("/api/candidates");
        if (candidateName != null) {
            request.param("candidate_name", candidateName);
        }
        request.header("Authorization", "Bearer " + jwtService.generateToken(user, role));
        return request;
    }
}
