package com.example.frontapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${integration.meeting-manager.timeout:3000}")
    private int meetingManagerTimeout;

    @Value("${integration.ac-server.timeout:3000}")
    private int acServerTimeout;

    @Value("${integration.copilot-admin.timeout:3000}")
    private int copilotAdminTimeout;

    @Bean(name = "meetingManagerRestTemplate")
    public RestTemplate meetingManagerRestTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofMillis(meetingManagerTimeout))
            .readTimeout(Duration.ofMillis(meetingManagerTimeout))
            .build();
    }

    @Bean(name = "acServerRestTemplate")
    public RestTemplate acServerRestTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofMillis(acServerTimeout))
            .readTimeout(Duration.ofMillis(acServerTimeout))
            .build();
    }

    @Bean(name = "copilotAdminRestTemplate")
    public RestTemplate copilotAdminRestTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofMillis(copilotAdminTimeout))
            .readTimeout(Duration.ofMillis(copilotAdminTimeout))
            .build();
    }
}
