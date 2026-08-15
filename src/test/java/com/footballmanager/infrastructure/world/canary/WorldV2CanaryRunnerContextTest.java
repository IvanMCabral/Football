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
        "world.v2.canary.expected-source-sha=2fe7dff53f2c6f07d222337cdda0a6963841e229aef721d2e11a395ba3d4d68f",
        "world.v2.canary.expected-semantic-plan-sha=298b32c98e0052269896f3f9caa9a89e1f70e3fa15019ee061d7247c751ebc7a",
        "world.v2.canary.expected-canonical-fingerprint=1e654bec389796d232aba91685ac87d9ef1de08bcf3f5a7da563fdedbfb27000",
        "world.v2.canary.mode=VALIDATE_ONLY"
})
class WorldV2CanaryRunnerContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WorldV2CanaryRunner runner;

    @Autowired
    private WorldV2CanaryCertifiedAuthority authority;

    @Test
    void dedicatedProfileWiresProductionAuthorityAndRunner() {
        assertThat(applicationContext.getBean(WorldStorageMigrationOrchestrator.class)).isNotNull();
        assertThat(runner).isNotNull();
        assertThat(applicationContext.getBean(WorldV2CanaryCertifiedAuthority.class)).isSameAs(authority);
        assertThat(authority.ownerHash()).isEqualTo(WorldV2CanaryCertifiedAuthority.CERTIFIED_OWNER_SHA256);
        assertThat(runner.execute().block().status())
                .isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_OWNER_HASH);
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
                    WorldStorageMigrationExecutor.StoredState.LEGACY,
                    WorldV2CanaryCertifiedAuthority.SOURCE_SHA, true, true, 0,
                    true, false, WorldV2CanaryCertifiedAuthority.CANONICAL_FINGERPRINT));
        }

        @Bean
        WorldV2CanaryCapacityProvider capacityProvider() {
            return () -> Mono.just(new WorldV2CanaryCapacityProvider.CapacitySample(890_000));
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
