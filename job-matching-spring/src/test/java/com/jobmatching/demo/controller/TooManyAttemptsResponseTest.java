package com.jobmatching.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TooManyAttemptsResponseTest {

    @Test
    void sends429WithRetryAfterHeaderAndTheWaitInTheMessage() {
        ResponseEntity<Map<String, String>> response =
                new GlobalExceptionHandler().handleResponseStatus(new TooManyAttemptsException(600));

        assertEquals(429, response.getStatusCode().value());
        assertEquals("600", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("Too many attempts. Please try again in 10 minutes.", response.getBody().get("error"));
    }

    @Test
    void describesShortAndRoundedWaits() {
        assertEquals("1 second", TooManyAttemptsException.describe(1));
        assertEquals("45 seconds", TooManyAttemptsException.describe(45));
        assertEquals("1 minute", TooManyAttemptsException.describe(60));
        assertEquals("2 minutes", TooManyAttemptsException.describe(61));
        assertEquals("15 minutes", TooManyAttemptsException.describe(900));
    }
}
