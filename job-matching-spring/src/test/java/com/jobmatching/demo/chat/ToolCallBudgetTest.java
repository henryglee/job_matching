package com.jobmatching.demo.chat;

import com.jobmatching.demo.service.EmailDraftService;
import com.jobmatching.demo.service.JobMatchingService;
import com.jobmatching.demo.service.WhatIfService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/** One chat reply can only use so many tool calls, each of which is another paid model round. */
class ToolCallBudgetTest {

    private final MatchChatTools tools = new MatchChatTools(
            mock(JobMatchingService.class), mock(EmailDraftService.class), mock(WhatIfService.class));

    private static ToolContext context(AtomicInteger calls) {
        return new ToolContext(Map.of(
                MatchChatTools.CANDIDATE_ID, UUID.randomUUID(),
                MatchChatTools.PROPOSALS, new ArrayList<>(),
                MatchChatTools.CALLS, calls));
    }

    @Test
    void toolsRefuseOnceTheReplysBudgetIsSpent() {
        ToolContext context = context(new AtomicInteger());
        for (int i = 0; i < MatchChatTools.MAX_TOOL_CALLS_PER_REPLY; i++) {
            assertDoesNotThrow(() -> tools.listMyMatches(5, context));
        }
        assertThrows(IllegalStateException.class, () -> tools.listMyMatches(5, context));
    }

    @Test
    void eachReplyHasItsOwnBudget() {
        AtomicInteger spent = new AtomicInteger(MatchChatTools.MAX_TOOL_CALLS_PER_REPLY);
        assertThrows(IllegalStateException.class, () -> tools.listMyMatches(5, context(spent)));
        assertDoesNotThrow(() -> tools.listMyMatches(5, context(new AtomicInteger())));
    }

    @Test
    void invalidCallsCountToo() {
        AtomicInteger calls = new AtomicInteger();
        ToolContext context = context(calls);
        assertThrows(IllegalArgumentException.class, () -> tools.proposeEmailDraft(UUID.randomUUID().toString(), " ", "body", context));
        assertDoesNotThrow(() -> calls.get());
        assertThrows(IllegalArgumentException.class, () -> tools.proposeEmailDraft(UUID.randomUUID().toString(), "", "body", context));
        org.junit.jupiter.api.Assertions.assertEquals(2, calls.get());
    }
}
