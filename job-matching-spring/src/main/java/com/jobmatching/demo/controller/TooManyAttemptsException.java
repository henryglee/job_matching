package com.jobmatching.demo.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 429 that tells the client how long to wait, in the Retry-After header and in the message. */
public class TooManyAttemptsException extends ResponseStatusException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please try again in " + describe(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return headers;
    }

    static String describe(long seconds) {
        if (seconds < 60) {
            return seconds == 1 ? "1 second" : seconds + " seconds";
        }
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
