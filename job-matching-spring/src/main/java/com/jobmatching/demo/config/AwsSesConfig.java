package com.jobmatching.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
public class AwsSesConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public SesClient sesClient() {
        // DefaultCredentialsProvider checks env vars (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY),
        // then falls back to an EC2/ECS instance role in production, matching
        // SPRINGBOOT_DB_CONFIG.md's guidance.
        return SesClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
