package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.JobListing;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EmbeddingTextBoundsTest {

    private final EmbeddingService service = new EmbeddingService(mock(EmbeddingModel.class), null, null);

    @Test
    void aHugeDescriptionIsCutSoTheEmbeddingCallStaysUnderTheModelsLimit() {
        JobListing job = new JobListing();
        job.setTitle("Engineer");
        job.setDescription("word ".repeat(100_000));
        assertTrue(service.buildJobText(job).length() < EmbeddingService.MAX_DESCRIPTION_CHARS + 500);
    }

    @Test
    void anOrdinaryDescriptionIsKeptWhole() {
        JobListing job = new JobListing();
        job.setTitle("Engineer");
        job.setDescription("Build things.");
        assertTrue(service.buildJobText(job).contains("Description: Build things."));
    }
}
