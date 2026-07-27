package com.example.demo.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that Flyway migrations can bootstrap schema that passes JPA validation.
 */
@SpringBootTest
@ActiveProfiles("flyway")
class FlywaySchemaValidationTest {

    @Test
    void contextLoadsWithFlywayAndJpaValidate() {
        // Context startup is the assertion: Flyway must create tables before JPA validation.
    }
}

