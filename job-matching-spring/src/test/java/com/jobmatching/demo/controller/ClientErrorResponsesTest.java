package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmbeddingService;
import com.jobmatching.demo.service.JobEmbeddingBackfillService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bad client input gets a 4xx in the API's {"error": "..."} shape, not a 500 from the
 * catch-all in GlobalExceptionHandler; genuine server errors still get 500.
 */
@WebMvcTest(controllers = {JobMatchingController.class, UserNameController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class ClientErrorResponsesTest {

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
    @MockitoBean
    private UserAccountService userAccountService;

    @Test
    void malformedUuidInPathIs400() throws Exception {
        mockMvc.perform(auth(get("/api/candidates/not-a-uuid/matches")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for 'candidateId' (expected UUID)"));
    }

    @Test
    void nonNumericQueryParamIs400() throws Exception {
        mockMvc.perform(auth(get("/api/candidates/" + UUID.randomUUID() + "/matches?limit=abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for 'limit' (expected int)"));
    }

    @Test
    void malformedJsonBodyIs400() throws Exception {
        mockMvc.perform(auth(put("/api/user/name")).contentType(MediaType.APPLICATION_JSON).content("{\"first_name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or missing request body"));
    }

    @Test
    void missingJsonBodyIs400() throws Exception {
        mockMvc.perform(auth(put("/api/user/name")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or missing request body"));
    }

    @Test
    void unsupportedMethodIs405WithAllowHeader() throws Exception {
        mockMvc.perform(auth(delete("/api/user/name")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("PUT")))
                .andExpect(jsonPath("$.error").value("Method 'DELETE' is not supported."));
    }

    @Test
    void unsupportedContentTypeIs415() throws Exception {
        mockMvc.perform(auth(put("/api/user/name")).contentType(MediaType.TEXT_PLAIN).content("Ada Lovelace"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value(containsString("is not supported")));
    }

    @Test
    void unknownPathIs404() throws Exception {
        mockMvc.perform(auth(get("/api/does-not-exist")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void genuineServerErrorIsStill500() throws Exception {
        when(jobMatchingService.findMatchingJobs(any(), anyInt())).thenThrow(new IllegalStateException("db down"));

        mockMvc.perform(auth(get("/api/candidates/" + UUID.randomUUID() + "/matches")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    // Staff, so the ownership check on /matches passes and the request reaches conversion.
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("staff@example.com");
        user.setUsername("staff");
        return request.header("Authorization", "Bearer " + jwtService.generateToken(user, "Staff"));
    }
}
