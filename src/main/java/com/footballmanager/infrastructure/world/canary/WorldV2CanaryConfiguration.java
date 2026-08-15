package com.footballmanager.infrastructure.world.canary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Dedicated opt-in wiring; absent from every normal profile. */
@Configuration
@Profile("world-v2-canary")
@ConditionalOnProperty(name = "world.v2.canary.enabled", havingValue = "true")
@EnableConfigurationProperties(WorldV2CanaryProperties.class)
public class WorldV2CanaryConfiguration {

    @Bean
    WorldV2CanaryCertifiedAuthority worldV2CanaryCertifiedAuthority() {
        return WorldV2CanaryCertifiedAuthority.h79f();
    }
}
