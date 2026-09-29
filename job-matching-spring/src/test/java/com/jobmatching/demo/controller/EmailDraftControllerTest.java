package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.dto.EmailDraftView;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.AiUsageEvent;
import com.jobmatching.demo.service.AiUsageMonitor;
import com.jobmatching.demo.service.DraftGenerationLimiter;
import com.jobmatching.demo.service.EmailDraftGenerator;
import com.jobmatching.demo.service.EmailDraftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/drafts: always the caller's own linked candidate profile. */
@WebMvcTest(EmailDraftController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000",
        "spring.jackson.property-naming-strategy=SNAKE_CASE"
})
class EmailDraftControllerTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private static final UUID DRAFT_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EmailDraftService emailDraftService;

    @MockitoBean
    private EmailDraftGenerator emailDraftGenerator;

    @MockitoBean
    private DraftGenerationLimiter draftGenerationLimiter;

    @MockitoBean
    private AiUsageMonitor aiUsageMonitor;

    @Test
    void listsTheCallersOwnDrafts() throws Exception {
        when(emailDraftService.listDrafts(CANDIDATE_ID, null)).thenReturn(List.of(view()));

        mockMvc.perform(get("/api/drafts").header("Authorization", bearer(CANDIDATE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drafts[0].job_title").value("Backend Engineer"))
                .andExpect(jsonPath("$.drafts[0].subject").value("Application"));
    }

    @Test
    void accountWithoutProfileIsForbidden() throws Exception {
        mockMvc.perform(get("/api/drafts").header("Authorization", bearer(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Email drafts need a linked candidate profile"));
        verifyNoInteractions(emailDraftService);
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/drafts")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateValidatesRecipientAndSubject() throws Exception {
        mockMvc.perform(put("/api/drafts/" + DRAFT_ID)
                        .header("Authorization", bearer(CANDIDATE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"not-an-email\",\"subject\":\"\",\"body\":\"Body\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(emailDraftService);
    }

    @Test
    void updateOfMissingOrSentDraftIs404() throws Exception {
        when(emailDraftService.updateDraft(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/drafts/" + DRAFT_ID)
                        .header("Authorization", bearer(CANDIDATE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"\",\"subject\":\"Hi\",\"body\":\"Body\"}"))
                .andExpect(status().isNotFound());
        verify(emailDraftService).updateDraft(CANDIDATE_ID, DRAFT_ID, "", "Hi", "Body");
    }

    @Test
    void markSentAndDeleteUseTheCallersProfile() throws Exception {
        when(emailDraftService.markSent(CANDIDATE_ID, DRAFT_ID)).thenReturn(Optional.of(view()));
        when(emailDraftService.deleteDraft(CANDIDATE_ID, DRAFT_ID)).thenReturn(true);

        mockMvc.perform(post("/api/drafts/" + DRAFT_ID + "/mark-sent").header("Authorization", bearer(CANDIDATE_ID)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/drafts/" + DRAFT_ID).header("Authorization", bearer(CANDIDATE_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void generateReturnsTheNewDraft() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(draftGenerationLimiter.tryAcquire(CANDIDATE_ID)).thenReturn(true);
        when(emailDraftGenerator.generate(CANDIDATE_ID, jobId, "friendly")).thenReturn(Optional.of(view()));

        mockMvc.perform(generate("{\"job_id\":\"" + jobId + "\",\"instructions\":\"friendly\"}", bearer(CANDIDATE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(DRAFT_ID.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void generateOverTheLimitIs429WithoutCallingTheModel() throws Exception {
        when(draftGenerationLimiter.tryAcquire(CANDIDATE_ID)).thenReturn(false);

        mockMvc.perform(generate("{\"job_id\":\"" + UUID.randomUUID() + "\"}", bearer(CANDIDATE_ID)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("use your own AI assistant")));
        verify(emailDraftGenerator, never()).generate(any(), any(), any());
        verify(aiUsageMonitor).record(CANDIDATE_ID, AiUsageEvent.DRAFT_LIMITED);
    }

    @Test
    void generateWithFlaggedInstructionsIs400() throws Exception {
        when(draftGenerationLimiter.tryAcquire(CANDIDATE_ID)).thenReturn(true);
        when(emailDraftGenerator.generate(any(), any(), any()))
                .thenThrow(org.mockito.Mockito.mock(EmailDraftGenerator.InstructionsRejectedException.class));

        mockMvc.perform(generate("{\"job_id\":\"" + UUID.randomUUID() + "\",\"instructions\":\"x\"}", bearer(CANDIDATE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("can't be used")));
    }

    @Test
    void generateModelFailureIs502() throws Exception {
        when(draftGenerationLimiter.tryAcquire(CANDIDATE_ID)).thenReturn(true);
        when(emailDraftGenerator.generate(any(), any(), any()))
                .thenThrow(org.mockito.Mockito.mock(EmailDraftGenerator.GenerationFailedException.class));

        mockMvc.perform(generate("{\"job_id\":\"" + UUID.randomUUID() + "\"}", bearer(CANDIDATE_ID)))
                .andExpect(status().isBadGateway());
    }

    @Test
    void generateForUnknownJobIs404() throws Exception {
        when(draftGenerationLimiter.tryAcquire(CANDIDATE_ID)).thenReturn(true);
        when(emailDraftGenerator.generate(any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(generate("{\"job_id\":\"" + UUID.randomUUID() + "\"}", bearer(CANDIDATE_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void generateValidatesTheRequest() throws Exception {
        mockMvc.perform(generate("{\"instructions\":\"hi\"}", bearer(CANDIDATE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("job_id is required."));
        mockMvc.perform(generate("{\"job_id\":\"" + UUID.randomUUID() + "\",\"instructions\":\"" + "x".repeat(501) + "\"}",
                        bearer(CANDIDATE_ID)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(draftGenerationLimiter, emailDraftGenerator);
    }

    @Test
    void generateNeedsACandidateProfile() throws Exception {
        mockMvc.perform(generate("{\"job_id\":\"" + UUID.randomUUID() + "\"}", bearer(null)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(draftGenerationLimiter, emailDraftGenerator);
    }

    @Test
    void createSavesAProposedDraftForTheCaller() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(emailDraftService.createDraft(CANDIDATE_ID, jobId, "jobs@acme.example", "Application", "Hi Acme"))
                .thenReturn(view());

        mockMvc.perform(post("/api/drafts").header("Authorization", bearer(CANDIDATE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_id\":\"" + jobId + "\",\"recipient\":\"jobs@acme.example\",\"subject\":\"Application\",\"body\":\"Hi Acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(DRAFT_ID.toString()));
    }

    @Test
    void createRejectsAnInventedRecipientAndBadInput() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(emailDraftService.createDraft(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Recipient must be one of the job's contact emails; leave it empty if the job has none"));

        mockMvc.perform(post("/api/drafts").header("Authorization", bearer(CANDIDATE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_id\":\"" + jobId + "\",\"recipient\":\"ceo@acme.example\",\"subject\":\"s\",\"body\":\"b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("contact emails")));
        mockMvc.perform(post("/api/drafts").header("Authorization", bearer(CANDIDATE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"s\",\"body\":\"b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("job_id is required."));
    }

    private static org.springframework.test.web.servlet.RequestBuilder generate(String json, String authorization) {
        return post("/api/drafts/generate")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private EmailDraftView view() {
        return new EmailDraftView(DRAFT_ID, UUID.randomUUID(), "Backend Engineer", "Acme", "https://acme.example",
                "jobs@acme.example", "Application", "Body", "DRAFT", LocalDateTime.now(), LocalDateTime.now(), null);
    }

    private String bearer(UUID candidateProfileId) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        if (candidateProfileId != null) {
            CandidateInfo profile = new CandidateInfo();
            profile.setId(candidateProfileId);
            user.setCandidateProfile(profile);
        }
        return "Bearer " + jwtService.generateToken(user, candidateProfileId != null ? "User" : "Staff");
    }
}
