package com.jobmatching.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Backs the per-candidate "spawn a background thread to scrape jobs" behavior from
// app.py's candidate-creation flow (see ScrapeOrchestrationService.scrapeForTitleAsync).
@Configuration
@EnableAsync
public class AsyncConfig {
}
