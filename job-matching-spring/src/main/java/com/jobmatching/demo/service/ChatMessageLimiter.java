package com.jobmatching.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

// Messages to the "Ask about your matches" chat (MatchChatService): at least one model call
// each, more when the model uses tools.
@Component
public class ChatMessageLimiter extends HourlyLimiter {

    @Autowired
    public ChatMessageLimiter(@Value("${app.chat.messages-per-hour:30}") int perHour) {
        this(perHour, Clock.systemUTC());
    }

    ChatMessageLimiter(int perHour, Clock clock) {
        super(perHour, clock);
    }
}
