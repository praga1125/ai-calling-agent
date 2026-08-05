package com.flowzo.callingagent.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    ApplicationRunner ensureAudioDirectory(AppProperties properties) {
        return args -> {
            Path dir = Paths.get(properties.getStorage().getAudioDir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
        };
    }
}
