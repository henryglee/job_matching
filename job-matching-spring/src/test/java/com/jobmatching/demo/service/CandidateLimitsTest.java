package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.CandidateInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandidateLimitsTest {

    private static CandidateInfo valid() {
        CandidateInfo c = new CandidateInfo();
        c.setCandidateName("Ada Lovelace");
        c.setSkills(List.of("Java", "SQL"));
        return c;
    }

    private static String message(CandidateInfo c) {
        return assertThrows(IllegalArgumentException.class, () -> CandidateLimits.validate(c)).getMessage();
    }

    @Test
    void acceptsAnOrdinaryProfileAndValuesExactlyAtTheLimit() {
        assertDoesNotThrow(() -> CandidateLimits.validate(valid()));
        CandidateInfo c = valid();
        c.setCandidateName("x".repeat(CandidateLimits.NAME_MAX));
        c.setLocationCity("x".repeat(CandidateLimits.LOCATION_MAX));
        c.setSkills(Collections.nCopies(CandidateLimits.MAX_SKILLS, "y".repeat(CandidateLimits.SKILL_MAX)));
        assertDoesNotThrow(() -> CandidateLimits.validate(c));
    }

    @Test
    void refusesTextLongerThanItsColumnWithAMessageNamingTheField() {
        CandidateInfo c = valid();
        c.setCandidateName("x".repeat(501));
        assertEquals("Full name must be at most 500 characters.", message(c));

        c = valid();
        c.setLocationState("x".repeat(201));
        assertEquals("State / Province must be at most 200 characters.", message(c));

        c = valid();
        c.setPreferredJobTitle("x".repeat(501));
        assertEquals("Preferred job title must be at most 500 characters.", message(c));
    }

    @Test
    void refusesTooManyOrTooLongSkills() {
        CandidateInfo c = valid();
        c.setSkills(Collections.nCopies(CandidateLimits.MAX_SKILLS + 1, "Java"));
        assertEquals("Add at most 50 skills.", message(c));

        c = valid();
        c.setSkills(List.of("Java", "z".repeat(101)));
        assertEquals("Each skill must be at most 100 characters.", message(c));
    }
}
