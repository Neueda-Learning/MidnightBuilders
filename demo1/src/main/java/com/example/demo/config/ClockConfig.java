package com.example.demo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time-related Spring bean configuration.
 *
 * <p>Provides a unified UTC {@link Clock} bean for business services,
 * making time access consistent and testable.</p>
 */
@Configuration
public class ClockConfig {

    /**
     * System UTC clock bean.
     *
     * @return UTC clock used across the application
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

