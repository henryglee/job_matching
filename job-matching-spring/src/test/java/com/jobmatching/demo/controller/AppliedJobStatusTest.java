package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.dto.AppliedJobView;
import com.jobmatching.demo.entity.CandidateAppliedJob;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/candidates/{id}/applied-jobs/{appliedId}/status, from the Applied Jobs page's
 * status dropdown: the snake_case entry it returns, and what it rejects. Also that Admin/Staff
 * get a view-only Applied Jobs page: they can list a candidate's applied jobs, but only the
 * candidate can change a status or undo one.
 */
@WebMvcTest(JobMatchingController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000",
        "spring.jackson.property-naming-strategy=SNAKE_CASE"
})
class AppliedJobStatusTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private static final UUID APPLIED_ID = UUID.randomUUID();

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
    void ownerCanSetStatus() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(jobMatchingService.updateAppliedStatus(CANDIDATE_ID, APPLIED_ID, CandidateAppliedJob.Status.INTERVIEWING))
                .thenReturn(Optional.of(new AppliedJobView(APPLIED_ID, UUID.randomUUID(), "Backend Engineer", "Acme",
                        "Austin, TX", "https://acme.example/1", now, "INTERVIEWING", now)));

        mockMvc.perform(statusRequest(CANDIDATE_ID, "INTERVIEWING").header("Authorization", userBearer(CANDIDATE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"))
                .andExpect(jsonPath("$.status_updated_at").exists())
                .andExpect(jsonPath("$.company_name").value("Acme"));
    }

    @Test
    void waitingIsAValidStatus() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(jobMatchingService.updateAppliedStatus(CANDIDATE_ID, APPLIED_ID, CandidateAppliedJob.Status.WAITING))
                .thenReturn(Optional.of(new AppliedJobView(APPLIED_ID, UUID.randomUUID(), "Backend Engineer", "Acme",
                        "Austin, TX", "https://acme.example/1", now, "WAITING", now)));

        mockMvc.perform(statusRequest(CANDIDATE_ID, "WAITING").header("Authorization", userBearer(CANDIDATE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void unknownStatusIsRejected() throws Exception {
        mockMvc.perform(statusRequest(CANDIDATE_ID, "HIRED").header("Authorization", userBearer(CANDIDATE_ID)))
                .andExpect(status().isBadRequest());
        verify(jobMatchingService, never()).updateAppliedStatus(any(), any(), any());
    }

    @Test
    void anotherCandidatesEntryIsForbidden() throws Exception {
        mockMvc.perform(statusRequest(CANDIDATE_ID, "OFFER").header("Authorization", userBearer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        verify(jobMatchingService, never()).updateAppliedStatus(any(), any(), any());
    }

    @Test
    void missingEntryIsNotFound() throws Exception {
        when(jobMatchingService.updateAppliedStatus(any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(statusRequest(CANDIDATE_ID, "REJECTED").header("Authorization", userBearer(CANDIDATE_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCanViewACandidatesAppliedJobs() throws Exception {
        when(jobMatchingService.listAppliedJobs(CANDIDATE_ID)).thenReturn(List.of(new AppliedJobView(APPLIED_ID,
                UUID.randomUUID(), "Backend Engineer", "Acme", "Austin, TX", "https://acme.example/1",
                LocalDateTime.now(), "OFFER", LocalDateTime.now())));

        mockMvc.perform(get("/api/candidates/" + CANDIDATE_ID + "/applied-jobs").header("Authorization", staffBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied_jobs[0].status").value("OFFER"));
    }

    @Test
    void staffCannotChangeAStatus() throws Exception {
        mockMvc.perform(statusRequest(CANDIDATE_ID, "REJECTED").header("Authorization", staffBearer()))
                .andExpect(status().isForbidden());
        verify(jobMatchingService, never()).updateAppliedStatus(any(), any(), any());
    }

    @Test
    void staffCannotUndoAnAppliedJob() throws Exception {
        mockMvc.perform(delete("/api/candidates/" + CANDIDATE_ID + "/applied-jobs/" + APPLIED_ID)
                        .header("Authorization", staffBearer()))
                .andExpect(status().isForbidden());
        verify(jobMatchingService, never()).unmarkApplied(any(), any());
    }

    @Test
    void candidateCanUndoTheirOwnAppliedJob() throws Exception {
        when(jobMatchingService.unmarkApplied(CANDIDATE_ID, APPLIED_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/candidates/" + CANDIDATE_ID + "/applied-jobs/" + APPLIED_ID)
                        .header("Authorization", userBearer(CANDIDATE_ID)))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder statusRequest(
            UUID candidateId, String status) {
        return put("/api/candidates/" + candidateId + "/applied-jobs/" + APPLIED_ID + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }

    private String userBearer(UUID candidateProfileId) {
        CandidateInfo profile = new CandidateInfo();
        profile.setId(candidateProfileId);
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setUsername("user");
        user.setCandidateProfile(profile);
        return "Bearer " + jwtService.generateToken(user, "User");
    }

    private String staffBearer() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("staff@example.com");
        user.setUsername("staff");
        return "Bearer " + jwtService.generateToken(user, "Staff");
    }
}
