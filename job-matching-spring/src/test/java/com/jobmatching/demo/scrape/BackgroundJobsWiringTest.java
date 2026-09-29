package com.jobmatching.demo.scrape;

import com.jobmatching.demo.config.AsyncConfig;
import com.jobmatching.demo.config.SchedulingConfig;
import com.jobmatching.demo.repository.JobListingRepository;
import com.jobmatching.demo.service.CandidateService;
import com.jobmatching.demo.service.EmbeddingService;
import com.jobmatching.demo.service.JobListingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the Spring wiring the unit tests can't: the cleanup and daily-scrape crons are registered,
 * the startup scrape runs off the thread that publishes ApplicationReadyEvent, and
 * app.background-jobs.enabled=false turns both off. No database needed.
 */
class BackgroundJobsWiringTest {

    private final CandidateService candidateService = mock(CandidateService.class);
    private final ScrapeOrchestrationService scrapeService = mock(ScrapeOrchestrationService.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncConfig.class, SchedulingConfig.class, BackgroundJobs.class)
            .withBean(CandidateService.class, () -> candidateService)
            .withBean(ScrapeOrchestrationService.class, () -> scrapeService)
            .withBean(JobListingService.class, () -> mock(JobListingService.class));

    @Test
    void registersNightlyCleanupAndDailyScrapeCrons() {
        runner.run(context -> {
            List<String> crons = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                    .flatMap(holder -> holder.getScheduledTasks().stream())
                    .map(task -> task.getTask())
                    .filter(CronTask.class::isInstance)
                    .map(task -> ((CronTask) task).getExpression())
                    .toList();
            assertEquals(Set.of("0 0 2 * * *", "0 0 3 * * *"), Set.copyOf(crons));
        });
    }

    @Test
    void startupScrapeRunsAsynchronouslyOnApplicationReady() {
        when(candidateService.getUniqueJobTitles()).thenReturn(List.of("Nurse"));
        AtomicReference<Thread> scrapeThread = new AtomicReference<>();
        when(scrapeService.scrapeForTitle("Nurse", ScrapeOrchestrationService.SCRAPE_TTL)).thenAnswer(inv -> {
            scrapeThread.set(Thread.currentThread());
            return ScrapeOrchestrationService.TitleScrapeOutcome.SCRAPED;
        });

        runner.run(context -> {
            context.publishEvent(new ApplicationReadyEvent(
                    new SpringApplication(), new String[0], context.getSourceApplicationContext(), Duration.ZERO));

            verify(scrapeService, timeout(5000)).scrapeForTitle("Nurse", ScrapeOrchestrationService.SCRAPE_TTL);
            assertNotEquals(Thread.currentThread(), scrapeThread.get());
        });
    }

    @Test
    void disabledPropertyTurnsBothJobsOff() {
        runner.withPropertyValues("app.background-jobs.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(BackgroundJobs.class);
            assertThat(context).doesNotHaveBean(SchedulingConfig.class);
        });
    }

    @Test
    void orchestrationServiceIsConstructibleBySpring() {
        // It has two constructors (one takes a Clock for tests); Spring must pick the right one.
        new ApplicationContextRunner()
                .withUserConfiguration(ScrapeOrchestrationService.class, ScrapeStateStore.class)
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(JobScrapeClient.class, () -> mock(JobScrapeClient.class))
                .withBean(JobListingService.class, () -> mock(JobListingService.class))
                .withBean(EmbeddingService.class, () -> mock(EmbeddingService.class))
                .withPropertyValues("app.scrape.state-file=target/wiring-test-state.json")
                .run(context -> assertTrue(context.getStartupFailure() == null
                        && context.getBean(ScrapeOrchestrationService.class) != null));
    }

    @Test
    void expiryServiceIsConstructibleBySpring() {
        // Same two-constructor shape as ScrapeOrchestrationService.
        new ApplicationContextRunner()
                .withUserConfiguration(JobExpiryService.class)
                .withBean(JobScrapeClient.class, () -> mock(JobScrapeClient.class))
                .withBean(JobListingRepository.class, () -> mock(JobListingRepository.class))
                .run(context -> assertTrue(context.getStartupFailure() == null
                        && context.getBean(JobExpiryService.class) != null));
    }
}
