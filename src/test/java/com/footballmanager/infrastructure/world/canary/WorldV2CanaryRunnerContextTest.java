package com.footballmanager.infrastructure.world.canary;

import com.footballmanager.application.service.career.CareerLifecycleCoordinator;
import com.footballmanager.application.service.world.WorldMigrationReferenceInventory;
import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.canary.WorldV2CanaryCapacityProvider;
import com.footballmanager.application.service.world.canary.WorldV2CanarySourceProbe;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.ports.out.world.CanonicalWorldCatalogSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.core.publisher.Mono;

import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(WorldV2CanaryRunnerContextTest.TestBeans.class)
@ActiveProfiles("world-v2-canary")
@TestPropertySource(properties = {
        "world.v2.canary.enabled=true",
        "world.v2.canary.owner-id=11111111-1111-1111-1111-111111111111",
        "world.v2.canary.expected-owner-hash=bafde89c041e1756082b933aaf16cad8e65dec48de748479352f657e89dd6da5",
        "world.v2.canary.expected-source-sha=source-sha",
        "world.v2.canary.expected-semantic-plan-sha=plan-sha",
        "world.v2.canary.expected-canonical-fingerprint=fingerprint",
        "world.v2.canary.max-current-storage-bytes=890000",
        "world.v2.canary.quota-bytes=1000000",
        "world.v2.canary.required-headroom-bytes=100000",
        "world.v2.canary.retained-cushion-bytes=10000",
        "world.v2.canary.mode=VALIDATE_ONLY"
})
class WorldV2CanaryRunnerContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WorldV2CanaryRunner runner;

    @Test
    void dedicatedProfileWiresRealOrchestratorAndRunner() {
        // The hash is asserted by the unit suite; this context test focuses on
        // profile/property wiring and the concrete product orchestrator bean.
        assertThat(applicationContext.getBean(WorldStorageMigrationOrchestrator.class)).isNotNull();
        assertThat(runner).isNotNull();
        assertThat(runner.execute().block().status())
                .isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_PASS);
    }

    @Test
    void normalProfileDoesNotRegisterRunner() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("test");
            context.register(WorldV2CanaryConfiguration.class, WorldV2CanaryRunner.class);
            context.refresh();
            assertThat(context.getBeansOfType(WorldV2CanaryRunner.class)).isEmpty();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("world-v2-canary")
    @Import({WorldV2CanaryConfiguration.class, WorldV2CanaryRunner.class})
    static class TestBeans {
        @Bean
        WorldV2CanarySourceProbe sourceProbe() {
            return ignored -> Mono.just(new WorldV2CanarySourceProbe.SourceSnapshot(
                    WorldStorageMigrationExecutor.StoredState.LEGACY, "source-sha", true, true, 0,
                    true, false, "fingerprint"));
        }

        @Bean
        WorldV2CanaryCapacityProvider capacityProvider() {
            return () -> Mono.just(new WorldV2CanaryCapacityProvider.CapacitySample(
                    890_000, 1_000_000, 100_000, 10_000));
        }

        @Bean
        WorldStorageMigrationExecutor executor() { return mock(WorldStorageMigrationExecutor.class); }

        @Bean
        CanonicalWorldCatalogSource canonicalSource() { return mock(CanonicalWorldCatalogSource.class); }

        @Bean
        CareerRepository careerRepository() { return mock(CareerRepository.class); }

        @Bean
        WorldMigrationReferenceInventory referenceInventory() {
            return mock(WorldMigrationReferenceInventory.class);
        }

        @Bean
        CareerLifecycleCoordinator lifecycleCoordinator() {
            return new CareerLifecycleCoordinator(Duration.ofSeconds(1));
        }

        @Bean
        WorldStorageMigrationOrchestrator orchestrator(WorldStorageMigrationExecutor executor,
                                                       CanonicalWorldCatalogSource canonicalSource,
                                                       CareerRepository careerRepository,
                                                       WorldMigrationReferenceInventory referenceInventory,
                                                       CareerLifecycleCoordinator lifecycleCoordinator) {
            return new WorldStorageMigrationOrchestrator(executor, canonicalSource, careerRepository,
                    referenceInventory, lifecycleCoordinator);
        }
    }
}
