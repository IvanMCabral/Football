package com.footballmanager.infrastructure.world.canary;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WorldV2CanaryActivationMatrixTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WorldV2CanaryConfiguration.class, WorldV2CanaryRunner.class,
                    UpstashManagementCapacityProvider.class, ProductWorldV2CanarySourceProbe.class);

    @Test
    void profileAbsentAndEnabledAbsentIsInert() {
        assertInert(contextRunner);
    }

    @Test
    void profilePresentAndEnabledAbsentIsInert() {
        assertInert(contextRunner.withPropertyValues("spring.profiles.active=world-v2-canary"));
    }

    @Test
    void profileAbsentAndEnabledTrueIsInert() {
        assertInert(contextRunner.withPropertyValues("world.v2.canary.enabled=true"));
    }

    @Test
    void profilePresentAndEnabledFalseIsInert() {
        assertInert(contextRunner.withPropertyValues("spring.profiles.active=world-v2-canary",
                "world.v2.canary.enabled=false"));
    }

    private static void assertInert(ApplicationContextRunner runner) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WorldV2CanaryRunner.class);
            assertThat(context).doesNotHaveBean(UpstashManagementCapacityProvider.class);
            assertThat(context).doesNotHaveBean(ProductWorldV2CanarySourceProbe.class);
        });
    }
}
