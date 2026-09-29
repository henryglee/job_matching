package com.jobmatching.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ports the background pass app.py starts on every GET /matches: embed up to 50 jobs that
 * don't have an embedding yet (e.g. ones whose embedding failed during an OpenAI outage),
 * so they start showing up in matches without anyone running it by hand.
 *
 * Unlike app.py, at most one pass runs at a time: otherwise several people viewing
 * matches at once would each embed the same jobs, paying OpenAI for every duplicate.
 */
@Service
public class JobEmbeddingBackfillService {

    private static final Logger log = LoggerFactory.getLogger(JobEmbeddingBackfillService.class);
    static final int BATCH_SIZE = 50;

    private final EmbeddingService embeddingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JobEmbeddingBackfillService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    // Returns immediately; the pass runs on the async executor. Never throws.
    @Async
    public void backfillAsync() {
        backfill();
    }

    // Returns how many jobs were embedded, or -1 if another pass was already running.
    int backfill() {
        if (!running.compareAndSet(false, true)) {
            return -1;
        }
        try {
            int embedded = embeddingService.generateJobEmbeddings(BATCH_SIZE);
            if (embedded > 0) {
                log.info("Background backfill embedded {} jobs", embedded);
            }
            return embedded;
        } catch (Exception e) {
            log.warn("Background job-embedding backfill failed: {}", e.getMessage());
            return 0;
        } finally {
            running.set(false);
        }
    }
}
