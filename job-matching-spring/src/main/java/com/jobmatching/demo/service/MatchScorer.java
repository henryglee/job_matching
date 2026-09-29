package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.MatchBreakdown;
import com.jobmatching.demo.dto.MatchExplanation;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.JobListing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scores one candidate/job pair: the numbers behind the Job Matches bars (MatchBreakdown) and
 * the explain_match tools (MatchExplanation), so both always agree. The scoring itself is the
 * port of find_matching_jobs that JobMatchingService used before; what's new is that each part
 * records how it was reached.
 *
 * The overall score is 1 - cosine distance between the candidate's and the job's embeddings
 * (EmbeddingService builds the texts). The three parts are keyword checks shown next to it and
 * don't feed into it. When a part has nothing to compare (e.g. no skills on the profile) it
 * repeats the overall score, as app.py did; it's flagged as estimated so no one reads it as a
 * real match.
 */
final class MatchScorer {

    static final String HOW_OVERALL_WORKS = "Semantic similarity between the candidate's profile text and the job's "
            + "text, from OpenAI embeddings (cosine similarity). It measures how close the two are in meaning overall. "
            + "It is not a sum or average of the parts below, which are separate keyword checks, and it can't be split "
            + "into points per field.";

    private MatchScorer() {
    }

    static MatchExplanation explain(CandidateInfo candidate, JobListing job, double similarity) {
        return new MatchExplanation(
                job.getId(), job.getTitle(), job.getCompanyName(),
                percent(similarity),
                HOW_OVERALL_WORKS,
                profileFieldsCompared(candidate),
                emptyProfileFields(candidate),
                jobFieldsCompared(job),
                skills(candidate, job, similarity),
                titleRelevance(candidate, job, similarity),
                location(candidate, job, similarity));
    }

    static MatchBreakdown breakdown(MatchExplanation explanation) {
        return new MatchBreakdown(
                explanation.overallPercent(),
                explanation.skills().percent(),
                explanation.titleRelevance().percent(),
                explanation.location().percent(),
                explanation.skills().matched(),
                explanation.skills().estimated(),
                explanation.titleRelevance().estimated(),
                explanation.location().estimated());
    }

    // Candidate skills found (case-insensitive, as whole terms: see TextMatching) anywhere in the
    // job's description, title or skills; score = found / listed.
    private static MatchExplanation.Part skills(CandidateInfo candidate, JobListing job, double similarity) {
        List<String> skills = candidate.getSkills() == null ? List.of()
                : candidate.getSkills().stream().filter(s -> s != null && !s.isBlank()).toList();
        if (skills.isEmpty()) {
            return standIn(similarity, "The profile lists no skills, so this repeats the overall score; "
                    + "it isn't a real skills match.");
        }
        String jobText = Stream.of(job.getDescription(), job.getTitle(), job.getSkills())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        List<String> matched = skills.stream().filter(s -> TextMatching.containsTerm(jobText, s)).toList();
        List<String> notMatched = skills.stream().filter(s -> !matched.contains(s)).toList();
        return new MatchExplanation.Part(
                percent((double) matched.size() / skills.size()), false,
                "%d of %d of the candidate's skills appear in the job's title, description or skills."
                        .formatted(matched.size(), skills.size()),
                matched, notMatched);
    }

    // Words longer than 2 letters in the preferred title that also appear in the job title;
    // score = shared / words in the preferred title.
    private static MatchExplanation.Part titleRelevance(CandidateInfo candidate, JobListing job, double similarity) {
        Set<String> candidateWords = wordsLongerThan2(candidate.getPreferredJobTitle());
        Set<String> jobWords = wordsLongerThan2(job.getTitle());
        if (candidateWords.isEmpty()) {
            return standIn(similarity, "The profile has no preferred job title to compare, so this repeats the overall score.");
        }
        if (jobWords.isEmpty()) {
            return standIn(similarity, "The job has no title to compare, so this repeats the overall score.");
        }
        List<String> shared = candidateWords.stream().filter(jobWords::contains).toList();
        List<String> notShared = candidateWords.stream().filter(w -> !jobWords.contains(w)).toList();
        return new MatchExplanation.Part(
                percent((double) shared.size() / candidateWords.size()), false,
                "%d of %d words in the preferred title \"%s\" appear in the job title \"%s\" (words of 3+ letters)."
                        .formatted(shared.size(), candidateWords.size(), candidate.getPreferredJobTitle().trim(), job.getTitle().trim()),
                shared, notShared);
    }

    // Parts of the candidate's city/state/country found in the job's location text, as whole terms
    // (a plain substring test matched state code "CA" in "Chicago" and "IN" in "Austin").
    private static MatchExplanation.Part location(CandidateInfo candidate, JobListing job, double similarity) {
        List<String> parts = Stream.of(candidate.getLocationCity(), candidate.getLocationState(), candidate.getLocationCountry())
                .filter(s -> s != null && !s.isBlank())
                .map(String::strip)
                .toList();
        String jobLocation = job.getLocation() == null ? "" : job.getLocation();
        if (parts.isEmpty()) {
            return standIn(similarity, "The profile has no location, so this repeats the overall score.");
        }
        if (jobLocation.isBlank()) {
            return standIn(similarity, "The job lists no location, so this repeats the overall score.");
        }
        String jobLocationLower = jobLocation.toLowerCase(Locale.ROOT);
        List<String> matched = parts.stream().filter(p -> TextMatching.containsTerm(jobLocationLower, p)).toList();
        List<String> notMatched = parts.stream().filter(p -> !matched.contains(p)).toList();
        return new MatchExplanation.Part(
                percent((double) matched.size() / parts.size()), false,
                "%d of %d parts of the candidate's location (%s) appear in the job location \"%s\"."
                        .formatted(matched.size(), parts.size(), String.join(", ", parts), jobLocation.trim()),
                matched, notMatched);
    }

    private static MatchExplanation.Part standIn(double similarity, String basis) {
        return new MatchExplanation.Part(percent(similarity), true, basis, List.of(), List.of());
    }

    // What EmbeddingService.buildCandidateProfileText puts into the embedding, with values.
    private static List<String> profileFieldsCompared(CandidateInfo candidate) {
        List<String> fields = new ArrayList<>();
        addIfPresent(fields, "Name", candidate.getCandidateName());
        addIfPresent(fields, "College degree", candidate.getCollegeDegree());
        addIfPresent(fields, "Preferred job title", candidate.getPreferredJobTitle());
        List<String> skills = candidate.getSkills() == null ? List.of()
                : candidate.getSkills().stream().filter(s -> !isBlank(s)).map(String::strip).toList();
        if (!skills.isEmpty()) {
            fields.add("Skills: " + String.join(", ", skills));
        }
        String location = locationText(candidate);
        addIfPresent(fields, "Location", location);
        return fields;
    }

    private static List<String> emptyProfileFields(CandidateInfo candidate) {
        List<String> empty = new ArrayList<>();
        if (isBlank(candidate.getCollegeDegree())) empty.add("College degree");
        if (isBlank(candidate.getPreferredJobTitle())) empty.add("Preferred job title");
        if (candidate.getSkills() == null || candidate.getSkills().stream().allMatch(MatchScorer::isBlank)) empty.add("Skills");
        if (locationText(candidate).isEmpty()) empty.add("Location");
        return empty;
    }

    // What EmbeddingService.buildJobText puts into the job's embedding (names only; the
    // description is available from get_job_details).
    private static List<String> jobFieldsCompared(JobListing job) {
        List<String> fields = new ArrayList<>();
        if (!isBlank(job.getTitle())) fields.add("Title");
        if (!isBlank(job.getCompanyName())) fields.add("Company");
        if (!isBlank(job.getDescription())) fields.add("Description");
        if (!isBlank(job.getSkills())) fields.add("Skills");
        if (!isBlank(job.getJobType())) fields.add("Job type");
        if (!isBlank(job.getLocation())) fields.add("Location");
        return fields;
    }

    private static String locationText(CandidateInfo candidate) {
        return Stream.of(candidate.getLocationCity(), candidate.getLocationState(), candidate.getLocationCountry())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static void addIfPresent(List<String> fields, String label, String value) {
        if (!isBlank(value)) {
            fields.add(label + ": " + value.trim());
        }
    }

    private static Set<String> wordsLongerThan2(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        // Split on anything that isn't part of a word, so "Engineer," and "(Remote)" match "Engineer"
        // and "Remote". "+" and "#" stay, for C++ and C#.
        return Arrays.stream(text.trim().split("[^\\p{L}\\p{N}+#]+"))
                .map(w -> w.toLowerCase(Locale.ROOT))
                .filter(w -> w.length() > 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Cosine similarity can dip below zero for unrelated texts; a negative "match" isn't meaningful.
    private static double percent(double fraction) {
        if (Double.isNaN(fraction)) {
            return 0;
        }
        return Math.round(Math.max(0.0, Math.min(fraction, 1.0)) * 1000) / 10.0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
