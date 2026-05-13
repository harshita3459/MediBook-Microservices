package com.medibook.appointment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for Spring Boot 4.x / Jackson 3.x.
 *
 * In Jackson 3.x, WRITE_DATES_AS_TIMESTAMPS was removed from SerializationFeature
 * entirely — it can no longer be set via yml or programmatically using that constant.
 *
 * The solution here is to:
 *   1. Register JavaTimeModule so LocalDate/LocalTime are handled natively.
 *   2. Rely on the @JsonFormat(pattern=..., timezone="Asia/Kolkata") annotations
 *      already present on every date/time field in the entity and DTO — those
 *      annotations take precedence over any global serialization feature and
 *      guarantee string output in the correct IST format regardless of Jackson version.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // JavaTimeModule enables LocalDate, LocalTime, LocalDateTime support.
        // @JsonFormat annotations on each field control the exact output pattern.
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
