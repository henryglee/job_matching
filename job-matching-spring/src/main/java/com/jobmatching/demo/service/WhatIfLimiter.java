package com.jobmatching.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

// What-if scores (WhatIfService), from the chat or MCP: one embedding call each. Separate from
// the chat's own limit because a model can call the tool several times for one message, and
// MCP calls have no other limit.
@Component
public class WhatIfLimiter extends HourlyLimiter {

    @Autowired
    public WhatIfLimiter(@Value("${app.what-if.per-hour:20}") int perHour) {
        this(perHour, Clock.systemUTC());
    }

    WhatIfLimiter(int perHour, Clock clock) {
        super(perHour, clock);
    }
}
