package com.jobmatching.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Screens text a candidate sends to the app's AI (chat messages, Generate-draft instructions)
 * with OpenAI's moderation endpoint, which is free, before it reaches the chat model. Flagged
 * text (harassment, hate, violence, self-harm, sexual content...) is refused without a model
 * call.
 *
 * Fails open: if the moderation call itself fails, the text goes through and the caller
 * records MODERATION_UNAVAILABLE, so an OpenAI moderation outage doesn't take the chat down.
 * The chat model's own safety behavior and the scope rules still apply in that case.
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    // OpenAI's categories, by the names its API uses. Spring AI's Categories also has fields
    // for other providers (health, financial, law, pii) that OpenAI never sets.
    private static final Map<String, Predicate<Categories>> CATEGORIES = Map.ofEntries(
            Map.entry("harassment", Categories::isHarassment),
            Map.entry("harassment/threatening", Categories::isHarassmentThreatening),
            Map.entry("hate", Categories::isHate),
            Map.entry("hate/threatening", Categories::isHateThreatening),
            Map.entry("self-harm", Categories::isSelfHarm),
            Map.entry("self-harm/intent", Categories::isSelfHarmIntent),
            Map.entry("self-harm/instructions", Categories::isSelfHarmInstructions),
            Map.entry("sexual", Categories::isSexual),
            Map.entry("sexual/minors", Categories::isSexualMinors),
            Map.entry("violence", Categories::isViolence),
            Map.entry("violence/graphic", Categories::isViolenceGraphic));

    public enum Outcome { ALLOWED, FLAGGED, SKIPPED, UNAVAILABLE }

    // categories are only filled in when flagged. The text itself is never kept or logged.
    public record Verdict(Outcome outcome, List<String> categories) {
        public boolean flagged() {
            return outcome == Outcome.FLAGGED;
        }
    }

    private final ModerationModel moderationModel;
    private final boolean enabled;

    public ModerationService(ObjectProvider<ModerationModel> moderationModel,
                             @Value("${app.moderation.enabled:true}") boolean enabled) {
        this.moderationModel = moderationModel.getIfAvailable();
        this.enabled = enabled;
        if (enabled && this.moderationModel == null) {
            log.warn("Moderation is enabled but no ModerationModel is configured; AI input won't be screened");
        }
    }

    public Verdict check(String text) {
        if (!enabled || moderationModel == null || text == null || text.isBlank()) {
            return new Verdict(Outcome.SKIPPED, List.of());
        }
        try {
            ModerationResponse response = moderationModel.call(new ModerationPrompt(text));
            List<ModerationResult> results = response.getResult().getOutput().getResults();
            List<String> flaggedCategories = new ArrayList<>();
            boolean flagged = false;
            for (ModerationResult result : results) {
                if (result.isFlagged()) {
                    flagged = true;
                    Categories categories = result.getCategories();
                    if (categories != null) {
                        CATEGORIES.forEach((name, test) -> {
                            if (test.test(categories) && !flaggedCategories.contains(name)) {
                                flaggedCategories.add(name);
                            }
                        });
                    }
                }
            }
            flaggedCategories.sort(null);
            return flagged ? new Verdict(Outcome.FLAGGED, List.copyOf(flaggedCategories)) : new Verdict(Outcome.ALLOWED, List.of());
        } catch (RuntimeException e) {
            log.warn("Moderation check failed; letting the text through: {}", e.getMessage());
            return new Verdict(Outcome.UNAVAILABLE, List.of());
        }
    }
}
