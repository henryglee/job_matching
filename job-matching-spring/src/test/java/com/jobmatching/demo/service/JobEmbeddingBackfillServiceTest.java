package com.jobmatching.demo.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobEmbeddingBackfillServiceTest {

    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final JobEmbeddingBackfillService service = new JobEmbeddingBackfillService(embeddingService);

    @Test
    void embedsUpTo50MissingJobs() {
        when(embeddingService.generateJobEmbeddings(50)).thenReturn(7);

        assertEquals(7, service.backfill());
        verify(embeddingService).generateJobEmbeddings(50);
    }

    @Test
    void secondPassIsSkippedWhileOneIsRunning() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(embeddingService.generateJobEmbeddings(50)).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 3;
        });

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(service::backfill);
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertEquals(-1, service.backfill());
        release.countDown();
        assertEquals(3, first.get(5, TimeUnit.SECONDS));

        // Guard released: the next pass runs.
        assertEquals(3, service.backfill());
        verify(embeddingService, times(2)).generateJobEmbeddings(50);
    }

    @Test
    void failureNeverEscapesAndReleasesTheGuard() {
        when(embeddingService.generateJobEmbeddings(50)).thenThrow(new IllegalStateException("db down")).thenReturn(1);

        assertEquals(0, service.backfill());
        assertEquals(1, service.backfill());
    }
}
