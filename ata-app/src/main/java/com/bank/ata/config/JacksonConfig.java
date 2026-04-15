package com.bank.ata.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Jackson 3.x / Spring Boot 4.x configuration.
 *
 * <p>Migration notes from Jackson 2.x → 3.x (Spring Boot 3 → 4):
 * <ul>
 *   <li>{@code Jackson2ObjectMapperBuilderCustomizer} →
 *       {@link JsonMapperBuilderCustomizer} (org.springframework.boot.jackson.autoconfigure)</li>
 *   <li>{@code SerializationFeature.WRITE_DATES_AS_TIMESTAMPS} →
 *       {@link DateTimeFeature#WRITE_DATES_AS_TIMESTAMPS} (tools.jackson.databind.cfg)</li>
 *   <li>{@code JavaTimeModule} is no longer needed — java.time.* support is built into
 *       jackson-databind 3.x (tools.jackson.databind.ext.javatime)</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    /**
     * Disables numeric timestamp serialisation so all {@code java.time.*} types
     * (Instant, LocalDate, ZonedDateTime, …) are written as ISO 8601 strings.
     */
    @Bean
    public JsonMapperBuilderCustomizer isoDateFormatCustomizer() {
        return builder -> builder
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS,
                         DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
    }
}
