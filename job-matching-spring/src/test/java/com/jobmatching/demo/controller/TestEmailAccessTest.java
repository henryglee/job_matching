package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmailService;
import com.jobmatching.demo.service.JobMatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/email/test is Admin/Staff only, through the real security chain. */
@WebMvcTest(EmailController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class TestEmailAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CandidateService candidateService;

    @MockitoBean
    private JobMatchingService jobMatchingService;

    @MockitoBean
    private EmailService emailService;

    @Test
    void staffCanSendTestEmail() throws Exception {
        mockMvc.perform(testEmail("Staff")).andExpect(status().isOk());
        verify(emailService).sendJobMatches(anyString(), eq("ops@example.com"), anyList());
    }

    @Test
    void adminCanSendTestEmail() throws Exception {
        mockMvc.perform(testEmail("Admin")).andExpect(status().isOk());
    }

    @Test
    void regularUserIsForbidden() throws Exception {
        mockMvc.perform(testEmail("User")).andExpect(status().isForbidden());
        verify(emailService, never()).sendJobMatches(any(), any(), any());
    }

    private MockHttpServletRequestBuilder testEmail(String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        return post("/api/email/test")
                .header("Authorization", "Bearer " + jwtService.generateToken(user, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ops@example.com\"}");
    }
}
