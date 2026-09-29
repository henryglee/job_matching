package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.scrape.ScrapeOrchestrationService;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.EmbeddingService;
import com.jobmatching.demo.service.JobListingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/jobs is always paged, with a default and a maximum page size. */
@WebMvcTest(JobListingController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class JobListingPagingTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private JobListingService jobListingService;
    @MockitoBean
    private EmbeddingService embeddingService;
    @MockitoBean
    private ScrapeOrchestrationService scrapeOrchestrationService;

    @Test
    void withoutParamsReturnsTheFirstDefaultSizedPageNewestFirst() throws Exception {
        Pageable pageable = request(get("/api/jobs"));

        assertEquals(0, pageable.getPageNumber());
        assertEquals(JobListingController.DEFAULT_JOBS_LIMIT, pageable.getPageSize());
        assertEquals("dateScraped: DESC,id: ASC", pageable.getSort().toString());
    }

    @Test
    void limitIsClampedAndPageSelectsLaterPages() throws Exception {
        assertEquals(JobListingController.MAX_JOBS_LIMIT, request(get("/api/jobs?limit=100000")).getPageSize());
        assertEquals(1, request(get("/api/jobs?limit=0")).getPageSize());

        Pageable third = request(get("/api/jobs?limit=20&page=2"));
        assertEquals(20, third.getPageSize());
        assertEquals(2, third.getPageNumber());
        assertEquals(0, request(get("/api/jobs?page=-3")).getPageNumber());
    }

    @Test
    void responseIncludesTheTotalAndPagePosition() throws Exception {
        Page<JobListing> page = new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(1, 20), 45);
        when(jobListingService.getJobs(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/jobs?limit=20&page=1").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(45))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20));
    }

    private Pageable request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) throws Exception {
        when(jobListingService.getJobs(any(), any())).thenReturn(Page.empty());
        mockMvc.perform(builder.header("Authorization", bearer())).andExpect(status().isOk());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(jobListingService, org.mockito.Mockito.atLeastOnce()).getJobs(isNull(), captor.capture());
        return captor.getValue();
    }

    private String bearer() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        return "Bearer " + jwtService.generateToken(user, "User");
    }
}
