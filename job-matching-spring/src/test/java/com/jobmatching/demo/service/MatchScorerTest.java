package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.MatchBreakdown;
import com.jobmatching.demo.dto.MatchExplanation;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchScorerTest {

    @Test
    void explainsEachPartWithWhatMatchedAndWhatDidnt() {
        MatchExplanation e = MatchScorer.explain(candidate(List.of("Java", "Python", "Kubernetes"), "Senior Software Engineer",
                "Austin", "TX", "USA"), job("Software Engineer", "Build Java and Python services.", "Austin, TX"), 0.8234);

        assertEquals(82.3, e.overallPercent());
        assertTrue(e.howOverallWorks().contains("not a sum or average"));

        assertEquals(66.7, e.skills().percent());
        assertFalse(e.skills().estimated());
        assertEquals(List.of("Java", "Python"), e.skills().matched());
        assertEquals(List.of("Kubernetes"), e.skills().notMatched());
        assertTrue(e.skills().basis().startsWith("2 of 3"));

        assertEquals(66.7, e.titleRelevance().percent(), "software + engineer of senior/software/engineer");
        assertEquals(List.of("software", "engineer"), e.titleRelevance().matched());
        assertEquals(List.of("senior"), e.titleRelevance().notMatched());

        assertEquals(66.7, e.location().percent(), "Austin and TX, not USA");
        assertEquals(List.of("USA"), e.location().notMatched());

        assertTrue(e.emptyProfileFields().isEmpty());
        assertTrue(e.profileFieldsCompared().contains("Skills: Java, Python, Kubernetes"));
        assertEquals(List.of("Title", "Description", "Location"), e.jobFieldsCompared());
    }

    // The case from the chat: a profile with no skills. The skills part used to show the overall
    // score as if it were a real skills match; it's now flagged as a stand-in.
    @Test
    void partsWithNothingToCompareAreFlaggedAsStandIns() {
        CandidateInfo noSkills = candidate(List.of(), "Software Engineer", null, null, null);
        noSkills.setCollegeDegree(null);
        MatchExplanation e = MatchScorer.explain(noSkills, job("Software Engineer", "Java, Python, DoD clearance.", ""), 0.802);

        assertTrue(e.skills().estimated());
        assertEquals(80.2, e.skills().percent(), "same number as before, so sorting and emails don't change");
        assertTrue(e.skills().basis().contains("no skills"));
        assertTrue(e.location().estimated());
        assertFalse(e.titleRelevance().estimated());
        assertEquals(List.of("College degree", "Skills", "Location"), e.emptyProfileFields());

        MatchBreakdown bars = MatchScorer.breakdown(e);
        assertEquals(80.2, bars.overall());
        assertTrue(bars.skillsEstimated());
        assertFalse(bars.titleRelevanceEstimated());
        assertTrue(bars.locationEstimated());
        assertEquals(List.of(), bars.matchedSkills());
    }

    @Test
    void jobWithoutATitleOrLocationIsAStandInToo() {
        MatchExplanation e = MatchScorer.explain(candidate(List.of("Java"), "Engineer", "Austin", null, null),
                job(null, "Java work", null), 0.5);

        assertTrue(e.titleRelevance().estimated());
        assertTrue(e.titleRelevance().basis().contains("job has no title"));
        assertTrue(e.location().estimated());
        assertEquals(100.0, e.skills().percent());
    }

    @Test
    void skillsMatchAsWholeTermsNotSubstrings() {
        // "Java" isn't in "JavaScript", and a one-letter skill isn't in every word that has the letter.
        CandidateInfo c = candidate(List.of("Java", "C", "R", "C++", "Node.js"), "Engineer", null, null, null);
        MatchExplanation e = MatchScorer.explain(c, job("Frontend Engineer", "We use JavaScript, Node.js and C++. Chicago office.", ""), 0.5);

        assertEquals(List.of("C++", "Node.js"), e.skills().matched());
        assertEquals(List.of("Java", "C", "R"), e.skills().notMatched());
    }

    @Test
    void locationMatchesWholeTermsSoStateCodesDontHitOtherWords() {
        // CA is in "Chicago" and IN in "Austin" as substrings, neither as a place.
        CandidateInfo ca = candidate(List.of("Java"), "Engineer", "Sacramento", "CA", null);
        assertEquals(List.of("Sacramento", "CA"), MatchScorer.explain(ca, job("E", "x", "Chicago, IL"), 0.5).location().notMatched());

        CandidateInfo in = candidate(List.of("Java"), "Engineer", null, "IN", null);
        assertEquals(List.of("IN"), MatchScorer.explain(in, job("E", "x", "Austin, TX"), 0.5).location().notMatched());

        CandidateInfo austin = candidate(List.of("Java"), "Engineer", " Austin ", "TX", null);
        assertEquals(100.0, MatchScorer.explain(austin, job("E", "x", "Austin, TX"), 0.5).location().percent());
        assertEquals(List.of("Austin", "TX"), MatchScorer.explain(austin, job("E", "x", "Austin,TX (Hybrid)"), 0.5).location().matched());
    }

    @Test
    void titleWordsIgnorePunctuation() {
        CandidateInfo c = candidate(List.of("Java"), "Software Engineer", null, null, null);
        MatchExplanation e = MatchScorer.explain(c, job("Software Engineer, Backend (Remote)", "x", ""), 0.5);
        assertEquals(100.0, e.titleRelevance().percent());
        assertEquals(List.of("software", "engineer"), e.titleRelevance().matched());
    }

    @Test
    void aNegativeOrInvalidSimilarityNeverShowsAsANegativePercent() {
        CandidateInfo c = candidate(List.of("Java"), "Engineer", null, null, null);
        assertEquals(0.0, MatchScorer.explain(c, job("E", "x", ""), -0.12).overallPercent());
        assertEquals(0.0, MatchScorer.explain(c, job("E", "x", ""), Double.NaN).overallPercent());
    }

    private static CandidateInfo candidate(List<String> skills, String title, String city, String state, String country) {
        CandidateInfo c = new CandidateInfo();
        c.setId(UUID.randomUUID());
        c.setCandidateName("Sam Lee");
        c.setCollegeDegree("BS Computer Science");
        c.setPreferredJobTitle(title);
        c.setSkills(skills);
        c.setLocationCity(city);
        c.setLocationState(state);
        c.setLocationCountry(country);
        return c;
    }

    private static JobListing job(String title, String description, String location) {
        JobListing j = new JobListing();
        j.setId(UUID.randomUUID());
        j.setTitle(title);
        j.setDescription(description);
        j.setLocation(location);
        return j;
    }
}
