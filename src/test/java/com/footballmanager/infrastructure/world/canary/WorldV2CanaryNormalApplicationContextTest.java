package com.footballmanager.infrastructure.world.canary;

import com.footballmanager.FootballManagerApplication;
import com.footballmanager.application.service.world.canary.WorldV2CanaryCapacityProvider;
import com.footballmanager.application.service.world.canary.WorldV2CanarySourceProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = FootballManagerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class WorldV2CanaryNormalApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void realNormalApplicationContextContainsNoCanaryOperationalPath() {
        assertThat(applicationContext.getBeansOfType(WorldV2CanaryRunner.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(WorldV2CanaryCertifiedAuthority.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(UpstashManagementCapacityProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ProductWorldV2CanarySourceProbe.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(WorldV2CanaryCapacityProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(WorldV2CanarySourceProbe.class)).isEmpty();
    }
}
