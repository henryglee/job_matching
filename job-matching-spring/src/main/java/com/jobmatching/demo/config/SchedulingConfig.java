package com.jobmatching.demo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Backs the nightly old-jobs cleanup in BackgroundJobs; off together with it.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.background-jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
