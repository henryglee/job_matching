package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.EmailDraftView;
import com.jobmatching.demo.dto.JobApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

/**
 * The in-app "Generate draft" path: writes an application email with the app's own OpenAI
 * chat model and saves it as a draft for the candidate to review on the Email Drafts page.
 * The other path is the candidate's own AI assistant over MCP; both follow
 * {@link DraftingGuidelines} and save through {@link EmailDraftService#createDraft}, so the
 * same recipient check applies.
 */
@Service
public class EmailDraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(EmailDraftGenerator.class);

    // See JobApplicationContext.withDescriptionLimit.
    static final int MAX_DESCRIPTION_CHARS = 6000;
    private static final int MAX_SUBJECT_CHARS = 300;
    private static final int MAX_BODY_CHARS = 20000;

    static final String SYSTEM_PROMPT = """
            You write job application emails on behalf of a candidate. Follow these rules:
            """ + DraftingGuidelines.RULES + """
            - The candidate may add instructions. Follow them unless they conflict with the rules
              above; if they ask you to mention something that isn't in the profile, leave it out.
            """;

    // What the model returns. The recipient isn't part of it: that's picked from the job's
    // scraped contact emails, never from model output.
    public record GeneratedEmail(String subject, String body) {
    }

    public static class GenerationFailedException extends RuntimeException {
        GenerationFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // The candidate's instructions were flagged by moderation; nothing was sent to the model.
    public static class InstructionsRejectedException extends RuntimeException {
        InstructionsRejectedException() {
            super("Instructions flagged by moderation");
        }
    }

    private final ChatClient chatClient;
    private final EmailDraftService emailDraftService;
    private final JsonMapper jsonMapper;
    private final ModerationService moderation;
    private final AiUsageMonitor usage;

    public EmailDraftGenerator(ChatClient.Builder chatClientBuilder,
                               EmailDraftService emailDraftService,
                               JsonMapper jsonMapper,
                               ModerationService moderation,
                               AiUsageMonitor usage) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.emailDraftService = emailDraftService;
        this.jsonMapper = jsonMapper;
        this.moderation = moderation;
        this.usage = usage;
    }

    /**
     * Generates and saves a draft. Empty if the candidate or job doesn't exist; throws
     * {@link InstructionsRejectedException} if moderation flags the instructions, and
     * {@link GenerationFailedException} if the model call fails or returns something unusable.
     */
    public Optional<EmailDraftView> generate(UUID candidateId, UUID jobId, String instructions) {
        Optional<JobApplicationContext> found = emailDraftService.getApplicationContext(candidateId, jobId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        JobApplicationContext context = found.get();

        ModerationService.Verdict verdict = moderation.check(instructions);
        if (verdict.flagged()) {
            usage.record(candidateId, AiUsageEvent.MODERATION_BLOCKED);
            log.info("Draft instructions from candidate {} blocked by moderation: {}", candidateId, verdict.categories());
            throw new InstructionsRejectedException();
        }
        if (verdict.outcome() == ModerationService.Outcome.UNAVAILABLE) {
            usage.record(candidateId, AiUsageEvent.MODERATION_UNAVAILABLE);
        }

        GeneratedEmail email;
        long tokens;
        try {
            ResponseEntity<ChatResponse, GeneratedEmail> result = chatClient.prompt()
                    .user(userMessage(context, instructions))
                    .call()
                    .responseEntity(GeneratedEmail.class);
            email = result.getEntity();
            tokens = totalTokens(result.getResponse());
        } catch (RuntimeException e) {
            log.error("Draft generation failed for candidate {} and job {}", candidateId, jobId, e);
            usage.record(candidateId, AiUsageEvent.DRAFT_FAILED);
            throw new GenerationFailedException("The AI model call failed", e);
        }
        if (email == null || isBlank(email.subject()) || isBlank(email.body())
                || email.subject().strip().length() > MAX_SUBJECT_CHARS || email.body().strip().length() > MAX_BODY_CHARS) {
            // Sizes only: the text is the candidate's profile details, and the usage monitor promises
            // never to keep message content.
            log.error("Draft generation returned an unusable email for candidate {} and job {} (subject {} chars, body {} chars)",
                    candidateId, jobId,
                    email == null || email.subject() == null ? 0 : email.subject().length(),
                    email == null || email.body() == null ? 0 : email.body().length());
            usage.record(candidateId, AiUsageEvent.DRAFT_FAILED, tokens);
            throw new GenerationFailedException("The AI model returned an unusable draft", null);
        }
        usage.record(candidateId, AiUsageEvent.DRAFT_GENERATED, tokens);

        String recipient = context.contactEmails().isEmpty() ? null : context.contactEmails().get(0);
        return Optional.of(emailDraftService.createDraft(
                candidateId, jobId, recipient, email.subject().strip(), email.body().strip()));
    }

    // The posting goes in as JSON so it reads as data; the system prompt says not to follow
    // anything written in it.
    String userMessage(JobApplicationContext context, String instructions) {
        JobApplicationContext trimmed = context.withDescriptionLimit(MAX_DESCRIPTION_CHARS);
        String candidateInstructions = isBlank(instructions) ? "(none)" : instructions.strip();
        return """
                Write an application email for this job.

                Candidate's instructions: %s

                Job and candidate profile (JSON):
                %s
                """.formatted(candidateInstructions, jsonMapper.writeValueAsString(trimmed));
    }

    private static long totalTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return 0;
        }
        Integer total = response.getMetadata().getUsage().getTotalTokens();
        return total == null ? 0 : total;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
