package com.jobmatching.demo.scrape;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Ports app.py's scrape_state.json: when each search term was last scraped, so the same
 * term isn't re-scraped within the TTL (see ScrapeOrchestrationService). Same on-disk
 * format as the Flask file — {"term": epoch-seconds} — and, like it, a local file rather
 * than a table, so it needs no schema change. Keys are trimmed and lower-cased (app.py
 * keys on the exact string, so "Software Engineer" and "software engineer" both scraped).
 *
 * A missing or unreadable file just means "nothing scraped yet", as in app.py.
 */
@Component
public class ScrapeStateStore {

    private static final Logger log = LoggerFactory.getLogger(ScrapeStateStore.class);
    private static final TypeReference<Map<String, Double>> STATE_TYPE = new TypeReference<>() {
    };

    private final Path file;
    private final boolean configuredAsRelative;
    private final JsonMapper jsonMapper;

    public ScrapeStateStore(@Value("${app.scrape.state-file}") String file, JsonMapper jsonMapper) {
        Path configured = Path.of(file);
        this.configuredAsRelative = !configured.isAbsolute();
        // Fixed once, so a later change of working directory can't move the file (and with it
        // every "last scraped" time).
        this.file = configured.toAbsolutePath().normalize();
        this.jsonMapper = jsonMapper;
    }

    /**
     * If the state can't be saved, every restart forgets what was scraped and re-scrapes every
     * title (paid embeddings included), with nothing but a per-write warning to say why. Say so
     * once, up front, with the resolved path.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnIfNotPersistent() {
        String problem = writeProblem();
        if (problem != null) {
            log.warn("Scrape state file {} {}. Until fixed, every restart will re-scrape every job title. "
                    + "Set SCRAPE_STATE_FILE to a writable path that survives restarts and deploys.", file, problem);
        } else if (configuredAsRelative) {
            log.warn("Scrape state file is a relative path, resolved to {} from the working directory. "
                    + "If the app starts from a different directory or a fresh checkout, the file is lost; "
                    + "set SCRAPE_STATE_FILE to an absolute path.", file);
        }
    }

    // Null if the file can be saved; otherwise why not. The directory has to be writable, not just
    // the file, because a save writes a temp file beside it and renames it over the file.
    String writeProblem() {
        if (Files.isDirectory(file)) {
            return "is a directory";
        }
        Path dir = file.getParent();
        while (dir != null && !Files.exists(dir)) {
            dir = dir.getParent(); // created on first save; check the nearest one that exists
        }
        if (dir == null || !Files.isDirectory(dir) || !Files.isWritable(dir)) {
            return "is in a directory that isn't writable";
        }
        if (Files.exists(file) && !Files.isWritable(file)) {
            return "exists but isn't writable";
        }
        return null;
    }

    public synchronized Optional<Instant> lastScraped(String searchTerm) {
        Double epochSeconds = load().get(key(searchTerm));
        return Optional.ofNullable(epochSeconds).map(s -> Instant.ofEpochMilli((long) (s * 1000)));
    }

    public synchronized void recordScrape(String searchTerm, Instant when) {
        Map<String, Double> state = load();
        state.put(key(searchTerm), when.toEpochMilli() / 1000.0);
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            // Write-then-rename, so a crash mid-write never leaves a truncated file behind.
            Path tmp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            try {
                jsonMapper.writeValue(tmp.toFile(), state);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp); // only still there if the write or the move failed
            }
        } catch (IOException | RuntimeException e) {
            // Losing a timestamp only means that term may be scraped again sooner.
            log.warn("Could not save scrape state to {}: {}", file, e.getMessage());
        }
    }

    static String key(String searchTerm) {
        return searchTerm.strip().toLowerCase(Locale.ROOT);
    }

    private Map<String, Double> load() {
        if (!Files.exists(file)) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(jsonMapper.readValue(file.toFile(), STATE_TYPE));
        } catch (RuntimeException e) {
            log.warn("Ignoring unreadable scrape state file {}: {}", file, e.getMessage());
            return new HashMap<>();
        }
    }
}
