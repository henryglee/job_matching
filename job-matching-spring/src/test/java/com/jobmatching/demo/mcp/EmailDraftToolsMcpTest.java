package com.jobmatching.demo.mcp;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.dto.EmailDraftView;
import com.jobmatching.demo.dto.JobMatchResult;
import com.jobmatching.demo.dto.MatchBreakdown;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.EmailDraft;
import com.jobmatching.demo.entity.JobListing;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.security.JwtAuthenticationFilter;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.EmailDraftService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.WhatIfService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Drives the real MCP server over Streamable HTTP with the MCP Java SDK client, the same
 * way Claude Desktop or Claude Code would. The point is the auth path: /mcp sits behind
 * JwtAuthenticationFilter, and the tools read the candidate from the SecurityContext. That
 * only works if Spring AI runs tool calls on the request thread, which this checks end to end.
 *
 * No database: the services are mocked and the JDBC/JPA auto-configurations are excluded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = EmailDraftToolsMcpTest.McpOnlyApp.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000",
        "spring.ai.openai.api-key=test-key",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
class EmailDraftToolsMcpTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class, EmailDraftTools.class})
    static class McpOnlyApp {
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EmailDraftService emailDraftService;

    @MockitoBean
    private JobMatchingService jobMatchingService;

    @MockitoBean
    private WhatIfService whatIfService;

    @Test
    void exposesDraftingToolsAndPromptButNoSendTool() {
        try (McpSyncClient client = client(bearer(CANDIDATE_ID))) {
            client.initialize();

            List<String> tools = client.listTools().tools().stream().map(Tool::name).toList();
            assertEquals(List.of("explain_match", "get_application_context", "list_email_drafts", "list_my_matches", "save_email_draft",
                            "update_email_draft", "what_if_score"),
                    tools.stream().sorted().toList());
            assertTrue(tools.stream().noneMatch(name -> name.contains("send")));

            List<String> prompts = client.listPrompts().prompts().stream().map(Prompt::name).toList();
            assertEquals(List.of("draft_application_email"), prompts);
        }
    }

    @Test
    void toolsActOnTheCandidateFromTheJwt() {
        when(emailDraftService.listDrafts(CANDIDATE_ID, EmailDraft.Status.DRAFT)).thenReturn(List.of(new EmailDraftView(
                UUID.randomUUID(), UUID.randomUUID(), "Backend Engineer", "Acme", "https://acme.example/jobs/1",
                "jobs@acme.example", "Application: Backend Engineer", "Hello", "DRAFT",
                LocalDateTime.now(), LocalDateTime.now(), null)));

        try (McpSyncClient client = client(bearer(CANDIDATE_ID))) {
            client.initialize();
            CallToolResult result = client.callTool(new CallToolRequest("list_email_drafts", Map.of()));

            assertFalse(Boolean.TRUE.equals(result.isError()), () -> text(result));
            assertTrue(text(result).contains("Application: Backend Engineer"));
            verify(emailDraftService).listDrafts(CANDIDATE_ID, EmailDraft.Status.DRAFT);
        }
    }

    @Test
    void listMyMatchesClampsLimitAndReturnsJobIds() {
        JobListing job = new JobListing();
        job.setId(UUID.randomUUID());
        job.setTitle("Backend Engineer");
        when(jobMatchingService.findMatchingJobs(CANDIDATE_ID, 25)).thenReturn(List.of(
                new JobMatchResult(job, 0.8234, new MatchBreakdown(82, 90, 70, 100, List.of("Java"), false, false, false))));

        try (McpSyncClient client = client(bearer(CANDIDATE_ID))) {
            client.initialize();
            CallToolResult result = client.callTool(new CallToolRequest("list_my_matches", Map.of("limit", 500)));

            assertFalse(Boolean.TRUE.equals(result.isError()), () -> text(result));
            assertTrue(text(result).contains(job.getId().toString()));
            assertTrue(text(result).contains("82.3"));
            verify(jobMatchingService).findMatchingJobs(CANDIDATE_ID, 25);
        }
    }

    @Test
    void saveDraftPassesArgumentsForTheJwtCandidateAndReturnsAReviewLink() {
        UUID jobId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        when(emailDraftService.createDraft(eq(CANDIDATE_ID), eq(jobId), any(), any(), any())).thenReturn(new EmailDraftView(
                draftId, jobId, "Backend Engineer", "Acme", null, null, "Hi", "Body", "DRAFT",
                LocalDateTime.now(), LocalDateTime.now(), null));

        try (McpSyncClient client = client(bearer(CANDIDATE_ID))) {
            client.initialize();
            CallToolResult result = client.callTool(new CallToolRequest("save_email_draft",
                    Map.of("jobId", jobId.toString(), "subject", "Hi", "body", "Body")));

            assertFalse(Boolean.TRUE.equals(result.isError()), () -> text(result));
            verify(emailDraftService).createDraft(CANDIDATE_ID, jobId, null, "Hi", "Body");
            assertTrue(text(result).contains("http://localhost:3000/email-drafts?highlight=" + draftId), () -> text(result));
        }
    }

    @Test
    void accountWithoutCandidateProfileGetsToolError() {
        try (McpSyncClient client = client(bearer(null))) {
            client.initialize();
            CallToolResult result = client.callTool(new CallToolRequest("list_email_drafts", Map.of()));

            assertTrue(Boolean.TRUE.equals(result.isError()));
            assertTrue(text(result).contains("No candidate profile"), () -> text(result));
            verifyNoInteractions(emailDraftService);
        }
    }

    @Test
    void promptTellsModelToFetchContextAndSaveOnlyAfterApproval() {
        try (McpSyncClient client = client(bearer(CANDIDATE_ID))) {
            client.initialize();
            var prompt = client.getPrompt(new GetPromptRequest("draft_application_email", Map.of("jobId", "abc", "tone", "friendly")));

            String instructions = ((TextContent) prompt.messages().get(0).content()).text();
            assertTrue(instructions.contains("get_application_context"));
            assertTrue(instructions.contains("friendly tone"));
            assertTrue(instructions.contains("until I approve it, then call save_email_draft"));
            assertTrue(instructions.contains("give me the reviewUrl"));
        }
    }

    @Test
    void connectionWithoutTokenIsRejected() {
        try (McpSyncClient client = client(null)) {
            assertThrows(RuntimeException.class, client::initialize);
        }
        verifyNoInteractions(emailDraftService);
    }

    private McpSyncClient client(String authorization) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        if (authorization != null) {
            requestBuilder.header("Authorization", authorization);
        }
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .requestBuilder(requestBuilder)
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
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

    private static String text(CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .reduce("", String::concat);
    }
}
