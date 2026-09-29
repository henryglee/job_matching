package com.jobmatching.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.Generation;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationServiceTest {

    @Test
    void flaggedTextReportsOpenAiCategoryNames() {
        ModerationModel model = mock(ModerationModel.class);
        when(model.call(any(ModerationPrompt.class))).thenReturn(response(true,
                Categories.builder().harassment(true).violence(true).build()));

        ModerationService.Verdict verdict = service(model, true).check("some hostile text");

        assertTrue(verdict.flagged());
        assertEquals(List.of("harassment", "violence"), verdict.categories());
    }

    @Test
    void cleanTextIsAllowed() {
        ModerationModel model = mock(ModerationModel.class);
        when(model.call(any(ModerationPrompt.class))).thenReturn(response(false, Categories.builder().build()));

        ModerationService.Verdict verdict = service(model, true).check("Which of my matches are remote?");

        assertEquals(ModerationService.Outcome.ALLOWED, verdict.outcome());
        assertTrue(verdict.categories().isEmpty());
    }

    // Fails open: an outage of the moderation endpoint mustn't take the chat down.
    @Test
    void moderationOutageLetsTextThroughAndSaysSo() {
        ModerationModel model = mock(ModerationModel.class);
        when(model.call(any(ModerationPrompt.class))).thenThrow(new RuntimeException("503"));

        ModerationService.Verdict verdict = service(model, true).check("hello");

        assertEquals(ModerationService.Outcome.UNAVAILABLE, verdict.outcome());
        assertFalse(verdict.flagged());
    }

    @Test
    void disabledMissingOrBlankIsSkippedWithoutACall() {
        ModerationModel model = mock(ModerationModel.class);

        assertEquals(ModerationService.Outcome.SKIPPED, service(model, false).check("hello").outcome());
        assertEquals(ModerationService.Outcome.SKIPPED, service(model, true).check("  ").outcome());
        assertEquals(ModerationService.Outcome.SKIPPED, service(null, true).check("hello").outcome());
        verify(model, never()).call(any(ModerationPrompt.class));
    }

    @SuppressWarnings("unchecked")
    private static ModerationService service(ModerationModel model, boolean enabled) {
        ObjectProvider<ModerationModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return new ModerationService(provider, enabled);
    }

    private static ModerationResponse response(boolean flagged, Categories categories) {
        ModerationResult result = ModerationResult.builder().flagged(flagged).categories(categories).build();
        return new ModerationResponse(new Generation(Moderation.builder().id("modr-test").model("omni-moderation-latest").results(List.of(result)).build()));
    }
}
