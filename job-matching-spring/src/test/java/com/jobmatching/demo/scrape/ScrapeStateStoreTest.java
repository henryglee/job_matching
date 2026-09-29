package com.jobmatching.demo.scrape;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrapeStateStoreTest {

    @TempDir
    Path dir;

    private ScrapeStateStore store(Path file) {
        return new ScrapeStateStore(file.toString(), JsonMapper.builder().build());
    }

    @Test
    void reportsNoProblemForAWritablePathEvenIfItsFoldersDontExistYet() {
        assertNull(store(dir.resolve("state.json")).writeProblem());
        assertNull(store(dir.resolve("not/yet/created/state.json")).writeProblem());
    }

    @Test
    void reportsAnUnwritableDirectory() throws Exception {
        Path readOnly = Files.createDirectory(dir.resolve("readonly"));
        assumeTrue(readOnly.toFile().setWritable(false) && !Files.isWritable(readOnly),
                "can't make a directory read-only here (running as root?)");
        try {
            assertEquals("is in a directory that isn't writable", store(readOnly.resolve("state.json")).writeProblem());
            assertEquals("is in a directory that isn't writable", store(readOnly.resolve("a/b/state.json")).writeProblem());
        } finally {
            readOnly.toFile().setWritable(true);
        }
    }

    @Test
    void reportsAPathThatIsADirectory() {
        assertEquals("is a directory", store(dir).writeProblem());
    }

    @Test
    void missingFileMeansNeverScraped() {
        assertTrue(store(dir.resolve("state.json")).lastScraped("software engineer").isEmpty());
    }

    @Test
    void recordsAndReadsBackIgnoringCaseAndSpacing() {
        Path file = dir.resolve("nested/state.json");
        Instant when = Instant.parse("2026-09-22T10:15:30.500Z");

        store(file).recordScrape("  Software Engineer ", when);

        // A fresh instance reads it from disk, under any spelling of the same term.
        assertEquals(Optional.of(when), store(file).lastScraped("software engineer"));
        assertEquals(Optional.of(when), store(file).lastScraped("SOFTWARE ENGINEER"));
        assertTrue(store(file).lastScraped("data analyst").isEmpty());
    }

    @Test
    void readsFileWrittenByFlaskApp() throws Exception {
        // app.py's format: json.dump({search_term: time.time()}).
        Path file = dir.resolve("scrape_state.json");
        Files.writeString(file, "{\"data analyst\": 1758535200.25}");

        assertEquals(Optional.of(Instant.ofEpochMilli(1758535200250L)), store(file).lastScraped("data analyst"));
    }

    @Test
    void corruptFileIsTreatedAsEmptyAndOverwritten() throws Exception {
        Path file = dir.resolve("state.json");
        Files.writeString(file, "{not json");
        ScrapeStateStore store = store(file);

        assertTrue(store.lastScraped("x").isEmpty());
        store.recordScrape("x", Instant.parse("2026-09-22T00:00:00Z"));
        assertTrue(store(file).lastScraped("x").isPresent());
    }
}
