package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.random.RandomGenerator;

import com.example.demo.time.DelaySleeper;

/**
 * Time source configuration for application services.
 *
 * <p><b>Purpose:</b> Exposes a single {@link Clock} bean so all services use the same
 * UTC time source. This improves consistency for persisted timestamps and allows tests
 * to replace the bean with a fixed clock when deterministic time assertions are needed.</p>
 */
@Configuration
public class ClockConfig {

    /**
     * Create the default system UTC clock bean.
     *
     * @return UTC clock used across services
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RandomGenerator paymentDelayRandomGenerator() {
        return RandomGenerator.getDefault();
    }

    @Bean
    public DelaySleeper delaySleeper() {
        return seconds -> Thread.sleep(seconds * 1000L);
    }
}
