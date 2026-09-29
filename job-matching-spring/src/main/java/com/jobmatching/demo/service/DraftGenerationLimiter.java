package com.jobmatching.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

// "Generate draft" on job matches (EmailDraftGenerator): one model call each.
@Component
public class DraftGenerationLimiter extends HourlyLimiter {

    @Autowired
    public DraftGenerationLimiter(@Value("${app.drafts.generations-per-hour:10}") int perHour) {
        this(perHour, Clock.systemUTC());
    }

    DraftGenerationLimiter(int perHour, Clock clock) {
        super(perHour, clock);
    }
}
