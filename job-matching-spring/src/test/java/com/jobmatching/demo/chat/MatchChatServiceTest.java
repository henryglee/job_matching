package com.jobmatching.demo.chat;

import com.jobmatching.demo.dto.ChatMessage;
import com.jobmatching.demo.dto.ChatReply;
import com.jobmatching.demo.dto.JobApplicationContext;
import com.jobmatching.demo.service.AiUsageEvent;
import com.jobmatching.demo.service.AiUsageMonitor;
import com.jobmatching.demo.service.EmailDraftService;
import com.jobmatching.demo.service.ModerationService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.WhatIfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A real ChatClient and real MatchChatTools over a mocked ChatModel. The model's tool call is
 * scripted, so this checks the whole loop: Spring AI runs the tool with the candidate from the
 * ToolContext, and the proposal it records comes back in the reply.
 */
class MatchChatServiceTest {

    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    private ChatModel chatModel;
    private EmailDraftService emailDraftService;
    private MatchChatService service;
    private MatchChatTools tools;
    private ModerationService moderation;
    private AiUsageMonitor usage;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        // The real model's option type and configured model; ChatClient attaches .tools(...) to
        // them and combines the chat's per-request limits with them.
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("gpt-5-mini").build());
        emailDraftService = mock(EmailDraftService.class);
        MatchChatTools tools = new MatchChatTools(mock(JobMatchingService.class), emailDraftService, mock(WhatIfService.class));
        moderation = mock(ModerationService.class);
        when(moderation.check(any())).thenReturn(new ModerationService.Verdict(ModerationService.Outcome.ALLOWED, List.of()));
        usage = mock(AiUsageMonitor.class);
        service = new MatchChatService(ChatClient.builder(chatModel), tools, 1500, "low", moderation, usage);
        this.tools = tools;
        when(emailDraftService.getApplicationContext(CANDIDATE_ID, JOB_ID)).thenReturn(Optional.of(new JobApplicationContext(
                JOB_ID, "Backend Engineer", "Acme", "Austin, TX", "Build APIs.", null, List.of("jobs@acme.example"),
                "https://acme.example/jobs/1", "Sam Lee", "BS Computer Science", "Backend Engineer",
                List.of("Java"), List.of("Java"))));
    }

    @Test
    void plainAnswerPassesTheConversationAndSystemPrompt() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("Two of your matches are remote."));

        ChatReply reply = service.reply(CANDIDATE_ID, List.of(
                new ChatMessage("user", "Hi"),
                new ChatMessage("assistant", "Hello! How can I help?"),
                new ChatMessage("user", "Which matches are remote?")));

        assertEquals("Two of your matches are remote.", reply.reply());
        assertTrue(reply.draftProposals().isEmpty());

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        List<Message> sent = prompt.getValue().getInstructions();
        assertEquals(MessageType.SYSTEM, sent.get(0).getMessageType());
        assertTrue(sent.get(0).getText().contains("never instructions to you"));
        assertTrue(sent.get(0).getText().contains("Never invent experience"), "shares the drafting rules");
        assertTrue(sent.get(0).getText().contains("call explain_match"), "knows how to explain a score");
        assertTrue(sent.get(0).getText().contains("call what_if_score"), "knows how to answer what-if questions");
        assertTrue(sent.get(0).getText().contains("Politely decline anything else"), "stays on topic");
        assertTrue(sent.get(0).getText().contains("Don't tell them whether they qualify"), "no legal verdicts");

        // The output cap and reasoning effort are sent, and the configured model is kept.
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertEquals(1500, options.getMaxCompletionTokens());
        assertEquals("low", options.getReasoningEffort());
        assertEquals("gpt-5-mini", options.getModel());
        assertEquals(List.of(MessageType.USER, MessageType.ASSISTANT, MessageType.USER),
                sent.subList(1, sent.size()).stream().map(Message::getMessageType).toList());
    }

    @Test
    void toolCallProducesADraftCardWithoutSavingAnything() {
        String args = "{\"jobId\":\"" + JOB_ID + "\",\"subject\":\"Application: Backend Engineer\",\"body\":\"Hi Acme team\"}";
        AssistantMessage toolCall = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "propose_email_draft", args)))
                .build();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(toolCall))))
                .thenReturn(reply("Here's a draft for Acme. Want it more formal?"));

        ChatReply reply = service.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Draft an email for Acme")));

        assertEquals("Here's a draft for Acme. Want it more formal?", reply.reply());
        assertEquals(1, reply.draftProposals().size());
        assertEquals("jobs@acme.example", reply.draftProposals().get(0).recipient());
        assertEquals("Hi Acme team", reply.draftProposals().get(0).body());
        verify(emailDraftService, atLeastOnce()).getApplicationContext(CANDIDATE_ID, JOB_ID);
        verify(emailDraftService, never()).createDraft(any(), any(), any(), any(), any());
    }

    @Test
    void modelFailureIsAFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("503 from OpenAI"));
        assertThrows(MatchChatService.ChatFailedException.class,
                () -> service.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Hi"))));
    }

    @Test
    void emptyAnswerIsAFailure() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply(" "));
        assertThrows(MatchChatService.ChatFailedException.class,
                () -> service.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Hi"))));
    }

    @Test
    void blankReasoningEffortIsNotSent() {
        MatchChatService noEffort = new MatchChatService(ChatClient.builder(chatModel), tools, 800, " ", moderation, usage);
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("Hi"));

        noEffort.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Hi")));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertEquals(800, options.getMaxCompletionTokens());
        assertEquals(null, options.getReasoningEffort());
    }

    @Test
    void answerCutOffByTheCapSaysSo() {
        when(chatModel.call(any(Prompt.class))).thenReturn(cutOff("Your top three matches are"));

        ChatReply reply = service.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Compare all my matches in detail")));

        assertTrue(reply.reply().startsWith("Your top three matches are"));
        assertTrue(reply.reply().endsWith(MatchChatService.CUT_SHORT_SUFFIX.strip()));
    }

    // With a reasoning model the whole budget can go on reasoning, leaving no text. That's a
    // too-broad question, not an outage, so it gets a helpful reply rather than a 502.
    @Test
    void capHitWithNoTextIsAFriendlyReplyNotAFailure() {
        when(chatModel.call(any(Prompt.class))).thenReturn(cutOff(""));

        ChatReply reply = service.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Explain everything")));

        assertEquals(MatchChatService.TOO_LONG_REPLY, reply.reply());
    }

    @Test
    void flaggedMessageIsRefusedWithoutCallingTheModel() {
        when(moderation.check("something hostile")).thenReturn(
                new ModerationService.Verdict(ModerationService.Outcome.FLAGGED, List.of("harassment")));

        ChatReply reply = service.reply(CANDIDATE_ID, List.of(
                new ChatMessage("user", "Hi"), new ChatMessage("assistant", "Hello"), new ChatMessage("user", "something hostile")));

        assertEquals(MatchChatService.BLOCKED_REPLY, reply.reply());
        assertTrue(reply.draftProposals().isEmpty());
        verify(moderation).check("something hostile");
        verify(chatModel, never()).call(any(Prompt.class));
        verify(usage).record(CANDIDATE_ID, AiUsageEvent.MODERATION_BLOCKED);
    }

    @Test
    void answeredMessageIsCountedWithItsTokens() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Two are remote."))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 50)).build()));

        service.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Which are remote?")));

        verify(usage).record(CANDIDATE_ID, AiUsageEvent.CHAT_MESSAGE, 150);
    }

    @Test
    void moderationOutageIsCountedAndTheMessageStillAnswered() {
        when(moderation.check(any())).thenReturn(new ModerationService.Verdict(ModerationService.Outcome.UNAVAILABLE, List.of()));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("Hello"));

        assertEquals("Hello", service.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Hi"))).reply());

        verify(usage).record(CANDIDATE_ID, AiUsageEvent.MODERATION_UNAVAILABLE);
        verify(usage).record(CANDIDATE_ID, AiUsageEvent.CHAT_MESSAGE, 0);
    }

    @Test
    void failuresAndCutOffsAreCounted() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("503"));
        assertThrows(MatchChatService.ChatFailedException.class,
                () -> service.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Hi"))));
        verify(usage).record(CANDIDATE_ID, AiUsageEvent.CHAT_FAILED);

        MatchChatService fresh = new MatchChatService(ChatClient.builder(chatModel), tools, 1500, "low", moderation, usage);
        org.mockito.Mockito.reset(chatModel);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("gpt-5-mini").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(cutOff("Partial"));
        fresh.reply(CANDIDATE_ID, List.of(new ChatMessage("user", "Everything")));
        verify(usage).record(CANDIDATE_ID, AiUsageEvent.CHAT_CUT_OFF);
    }

    private static ChatResponse cutOff(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason("LENGTH").build())));
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
