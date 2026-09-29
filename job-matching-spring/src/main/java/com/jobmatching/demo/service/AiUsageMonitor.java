package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.AiUsageReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * Counts AI usage per candidate (chat messages, drafts, what-if scores, model tokens,
 * moderation blocks, limit hits, failures) so an Admin can spot abuse or runaway cost on the
 * AI Usage page. Never stores message text.
 *
 * In memory since the server started: it resets on restart and isn't shared between
 * instances. Every event is also written as a one-line log record (event, candidate, tokens),
 * which is the durable trail if you ship logs somewhere.
 */
@Component
public class AiUsageMonitor {

    private static final Logger log = LoggerFactory.getLogger(AiUsageMonitor.class);

    private final Clock clock;
    private final Instant since;
    private final Map<UUID, Counters> byCandidate = new ConcurrentHashMap<>();

    @Autowired
    public AiUsageMonitor() {
        this(Clock.systemUTC());
    }

    AiUsageMonitor(Clock clock) {
        this.clock = clock;
        this.since = clock.instant();
    }

    public void record(UUID candidateId, AiUsageEvent event) {
        record(candidateId, event, 0);
    }

    public void record(UUID candidateId, AiUsageEvent event, long tokens) {
        Counters counters = byCandidate.computeIfAbsent(candidateId, id -> new Counters());
        counters.events.get(event).increment();
        if (tokens > 0) {
            counters.tokens.add(tokens);
        }
        counters.lastActivity.set(clock.instant());
        log.info("ai_usage event={} candidate={} tokens={}", event, candidateId, tokens);
    }

    // Busiest candidates first (by model tokens, then chat messages). names: candidate id to
    // display name, for whatever candidates the caller could look up.
    public AiUsageReport report(Function<List<UUID>, Map<UUID, String>> names) {
        Map<UUID, String> nameById = names.apply(List.copyOf(byCandidate.keySet()));
        List<AiUsageReport.CandidateUsage> candidates = byCandidate.entrySet().stream()
                .map(e -> new AiUsageReport.CandidateUsage(e.getKey(), nameById.get(e.getKey()),
                        e.getValue().usage(), e.getValue().lastActivity.get(), e.getValue().needsReview()))
                .sorted(Comparator.comparingLong((AiUsageReport.CandidateUsage c) -> c.usage().modelTokens())
                        .thenComparingLong(c -> c.usage().chatMessages())
                        .reversed())
                .toList();
        return new AiUsageReport(since, totals(), candidates);
    }

    private AiUsageReport.Usage totals() {
        Counters sum = new Counters();
        for (Counters counters : byCandidate.values()) {
            counters.events.forEach((event, count) -> sum.events.get(event).add(count.sum()));
            sum.tokens.add(counters.tokens.sum());
        }
        return sum.usage();
    }

    private static final class Counters {
        // Filled for every event up front and never modified after, so reads need no locking.
        private final Map<AiUsageEvent, LongAdder> events = new EnumMap<>(AiUsageEvent.class);
        private final LongAdder tokens = new LongAdder();
        private final AtomicReference<Instant> lastActivity = new AtomicReference<>();

        Counters() {
            for (AiUsageEvent event : AiUsageEvent.values()) {
                events.put(event, new LongAdder());
            }
        }

        long count(AiUsageEvent... events) {
            long total = 0;
            for (AiUsageEvent event : events) {
                total += this.events.get(event).sum();
            }
            return total;
        }

        AiUsageReport.Usage usage() {
            return new AiUsageReport.Usage(
                    count(AiUsageEvent.CHAT_MESSAGE),
                    count(AiUsageEvent.DRAFT_GENERATED),
                    count(AiUsageEvent.WHAT_IF),
                    tokens.sum(),
                    count(AiUsageEvent.MODERATION_BLOCKED),
                    count(AiUsageEvent.MODERATION_UNAVAILABLE),
                    count(AiUsageEvent.CHAT_LIMITED, AiUsageEvent.DRAFT_LIMITED, AiUsageEvent.WHAT_IF_LIMITED),
                    count(AiUsageEvent.CHAT_CUT_OFF),
                    count(AiUsageEvent.CHAT_FAILED, AiUsageEvent.DRAFT_FAILED, AiUsageEvent.WHAT_IF_FAILED));
        }

        boolean needsReview() {
            return count(AiUsageEvent.MODERATION_BLOCKED) > 0
                    || count(AiUsageEvent.CHAT_LIMITED, AiUsageEvent.DRAFT_LIMITED, AiUsageEvent.WHAT_IF_LIMITED) > 0;
        }
    }
}
