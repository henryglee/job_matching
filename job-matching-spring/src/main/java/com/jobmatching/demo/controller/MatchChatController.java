package com.jobmatching.demo.controller;

import com.jobmatching.demo.chat.MatchChatService;
import com.jobmatching.demo.dto.ChatMessage;
import com.jobmatching.demo.dto.ChatReply;
import com.jobmatching.demo.dto.ChatRequest;
import com.jobmatching.demo.security.JwtUserPrincipal;
import com.jobmatching.demo.service.AiUsageEvent;
import com.jobmatching.demo.service.AiUsageMonitor;
import com.jobmatching.demo.service.ChatMessageLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * The "Ask about your matches" chat on the Job Matches page (MatchChatService). Candidates
 * only: it answers from the caller's own linked profile and matches. No app.py equivalent.
 */
@RestController
public class MatchChatController {

    private final MatchChatService matchChatService;
    private final ChatMessageLimiter chatMessageLimiter;
    private final AiUsageMonitor aiUsageMonitor;

    public MatchChatController(MatchChatService matchChatService, ChatMessageLimiter chatMessageLimiter,
                               AiUsageMonitor aiUsageMonitor) {
        this.matchChatService = matchChatService;
        this.chatMessageLimiter = chatMessageLimiter;
        this.aiUsageMonitor = aiUsageMonitor;
    }

    @PostMapping("/api/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest request,
                          @AuthenticationPrincipal JwtUserPrincipal principal) {
        if (principal.candidateProfileId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The chat needs a linked candidate profile");
        }
        List<ChatMessage> messages = request.messages();
        if (!"user".equals(messages.get(messages.size() - 1).role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The last message must be from the user.");
        }
        UUID candidateId = principal.candidateProfileId();
        if (!chatMessageLimiter.tryAcquire(candidateId)) {
            aiUsageMonitor.record(candidateId, AiUsageEvent.CHAT_LIMITED);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've sent a lot of messages in the last hour. Try again later, or use Draft email on a match.");
        }
        try {
            return matchChatService.reply(candidateId, messages);
        } catch (MatchChatService.ChatFailedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The assistant couldn't answer right now. Try again in a moment.");
        }
    }
}
