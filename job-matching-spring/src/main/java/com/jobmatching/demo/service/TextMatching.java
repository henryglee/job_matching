package com.jobmatching.demo.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whole-term matching for the keyword checks (skills, location), so "Java" doesn't match
 * "JavaScript", "C" doesn't match every word containing a c, and a state code like "CA" doesn't
 * match "Chicago". "+", "#" and "." count as part of a word, for C++, C#, .NET and Node.js.
 */
final class TextMatching {

    private TextMatching() {
    }

    /** True if {@code term} appears in {@code lowerCaseText} as a whole term, ignoring case. */
    static boolean containsTerm(String lowerCaseText, String term) {
        String trimmed = term == null ? "" : term.strip();
        if (trimmed.isEmpty() || lowerCaseText == null) {
            return false;
        }
        return Pattern.compile("(?<![\\w+#.])" + Pattern.quote(trimmed.toLowerCase(Locale.ROOT)) + "(?![\\w+#])")
                .matcher(lowerCaseText).find();
    }
}
