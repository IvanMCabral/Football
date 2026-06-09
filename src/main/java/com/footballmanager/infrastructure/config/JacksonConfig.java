package com.footballmanager.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {
    /**
     * Configura el ObjectMapper global para serializar fechas como string ISO-8601.
     * La anotación @Primary asegura que Spring use este mapper para JSON.
     *
     * <p>V24D6M12: annotation introspector set to JacksonAnnotationIntrospector so
     * @JsonCreator/@JsonProperty on DTO constructor params are recognized during
     * deserialization. Without this, Jackson ignores @JsonProperty on constructor
     * params and fails to bind properties, resulting in null-filled objects.
     *
     * <p>Key settings for V24DetailedMatchData round-trip:
     * - annotationIntrospector = JacksonAnnotationIntrospector
     *   so @JsonCreator/@JsonProperty on constructor params are recognized during
     *   deserialization
     * - visibility = PUBLIC_ONLY for getters (serialization uses @JsonProperty on methods)
     * - FAIL_ON_UNKNOWN_PROPERTIES = false (backward compatibility)
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // V24D6M12: Use JacksonAnnotationIntrospector so @JsonCreator/@JsonProperty
        // on constructor params are recognized during deserialization.
        // This is critical for V24DetailedMatchData and nested DTOs (V24MatchEventDto,
        // V24PlayerMatchRatingDto, V24ShotCoordinateDto).
        mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector());
        // Ignorar propiedades desconocidas al deserializar (backward compatibility)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // V24D6M12: Set visibility so Jackson sees public getters as properties during serialization.
        // With @JsonAutoDetect(PUBLIC_ONLY) on V24 DTOs, getters are visible as properties.
        // Set CREATOR to PUBLIC_ONLY so @JsonCreator constructor is discovered.
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        mapper.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        mapper.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        return mapper;
    }
}
