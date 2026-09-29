package com.jobmatching.demo.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public final class DateParsing {

    private DateParsing() {
    }

    // Mirrors Python's `datetime.fromisoformat(value.replace('Z', '+00:00'))`, with a
    // date-only fallback since fromisoformat also accepts plain "YYYY-MM-DD" strings.
    public static LocalDateTime parseIsoOrDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace("Z", "+00:00");
        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(raw).atStartOfDay();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
