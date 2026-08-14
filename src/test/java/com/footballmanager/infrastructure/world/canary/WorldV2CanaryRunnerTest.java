package com.footballmanager.infrastructure.world.canary;

import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.canary.WorldV2CanaryCapacityProvider;
import com.footballmanager.application.service.world.canary.WorldV2CanarySourceProbe;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldV2CanaryRunnerTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SOURCE_SHA = WorldV2CanaryRunner.APPROVED_SOURCE_SHA;
    private static final String PLAN_SHA = WorldV2CanaryRunner.APPROVED_SEMANTIC_PLAN_SHA;
    private static final String FINGERPRINT = WorldV2CanaryRunner.APPROVED_CANONICAL_FINGERPRINT;
    private static final long QUOTA = WorldV2CanaryRunner.APPROVED_QUOTA_BYTES;
    private static final long REQUIRED = WorldV2CanaryRunner.APPROVED_REQUIRED_HEADROOM_BYTES;
    private static final long CUSHION = WorldV2CanaryRunner.APPROVED_RETAINED_CUSHION_BYTES;
    private static final long THRESHOLD = WorldV2CanaryRunner.APPROVED_MAX_CURRENT_STORAGE_BYTES;

    @Test
    void validateOnlyPassesWithoutInvokingMigration() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryRunner runner = runner(orchestrator, source(), capacity(THRESHOLD), "VALIDATE_ONLY", null);

        WorldV2CanaryRunner.RunResult result = runner.execute().block();

        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_PASS);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void executeRequiresIndependentArming() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryRunner runner = runner(orchestrator, source(), capacity(THRESHOLD), "EXECUTE", null);

        WorldV2CanaryRunner.RunResult result = runner.execute().block();

        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.EXECUTION_NOT_ARMED);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void wrongOwnerHashFailsBeforeProviderOrMigration() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanarySourceProbe probe = mock(WorldV2CanarySourceProbe.class);
        WorldV2CanaryCapacityProvider provider = mock(WorldV2CanaryCapacityProvider.class);
        WorldV2CanaryProperties properties = properties("VALIDATE_ONLY", null);
        properties.setExpectedOwnerHash("wrong");

        WorldV2CanaryRunner.RunResult result = new WorldV2CanaryRunner(properties, probe, provider, orchestrator)
                .execute().block();

        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_OWNER_HASH);
        verifyNoSourceOrCapacityCalls(probe, provider, orchestrator);
    }

    @Test
    void sourceChangeFailsClosed() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanarySourceProbe changed = ignored -> Mono.just(new WorldV2CanarySourceProbe.SourceSnapshot(
                WorldStorageMigrationExecutor.StoredState.LEGACY, "changed", true, true, 0, true, false,
                FINGERPRINT));

        WorldV2CanaryRunner.RunResult result = runner(orchestrator, changed, capacity(THRESHOLD),
                "VALIDATE_ONLY", null).execute().block();

        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_SOURCE_CHANGED);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void nonLegacyStateFailsClosed() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanarySourceProbe committed = ignored -> Mono.just(new WorldV2CanarySourceProbe.SourceSnapshot(
                WorldStorageMigrationExecutor.StoredState.COMMITTED, SOURCE_SHA, true, true, 0, true, false,
                FINGERPRINT));

        WorldV2CanaryRunner.RunResult result = runner(orchestrator, committed, capacity(THRESHOLD),
                "VALIDATE_ONLY", null).execute().block();

        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_STATE);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void referencesAndCatalogConflictFailClosed() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanarySourceProbe references = ignored -> Mono.just(new WorldV2CanarySourceProbe.SourceSnapshot(
                WorldStorageMigrationExecutor.StoredState.LEGACY, SOURCE_SHA, true, false, 1, true, false,
                FINGERPRINT));
        WorldV2CanaryRunner.RunResult referenceResult = runner(orchestrator, references, capacity(THRESHOLD),
                "VALIDATE_ONLY", null).execute().block();
        assertThat(referenceResult.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_REFERENCE);

        WorldV2CanarySourceProbe catalog = ignored -> Mono.just(new WorldV2CanarySourceProbe.SourceSnapshot(
                WorldStorageMigrationExecutor.StoredState.LEGACY, SOURCE_SHA, true, true, 0, false, true,
                "other-fingerprint"));
        WorldV2CanaryRunner.RunResult catalogResult = runner(orchestrator, catalog, capacity(THRESHOLD),
                "VALIDATE_ONLY", null).execute().block();
        assertThat(catalogResult.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_CATALOG);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void providerAuthenticationAndCapacityAreFailClosed() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryCapacityProvider unauthorized = () -> Mono.error(
                new WorldV2CanaryCapacityProvider.ProviderCapacityException(
                        WorldV2CanaryCapacityProvider.FailureKind.AUTHENTICATION, "redacted", null));
        WorldV2CanaryRunner.RunResult auth = runner(orchestrator, source(), unauthorized,
                "VALIDATE_ONLY", null).execute().block();
        assertThat(auth.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_PROVIDER_AUTH);

        WorldV2CanaryRunner.RunResult overCapacity = runner(orchestrator, source(), capacity(THRESHOLD + 1),
                "VALIDATE_ONLY", null).execute().block();
        assertThat(overCapacity.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_CAPACITY);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void executeInvokesOrchestratorExactlyOnce() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        when(orchestrator.migrate(any(), any())).thenReturn(Mono.just(new WorldStorageMigrationOrchestrator.Outcome(
                WorldStorageMigrationOrchestrator.Status.MIGRATED, "ok", 10)));
        WorldV2CanaryRunner runner = runner(orchestrator, source(), capacity(THRESHOLD), "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION);

        WorldV2CanaryRunner.RunResult result = runner.execute().block();

        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.MIGRATED);
        assertThat(result.orchestratorInvocations()).isEqualTo(1);
        verify(orchestrator).migrate(any(), any());
    }

    @Test
    void partialAndExceptionDoNotRetry() {
        WorldStorageMigrationOrchestrator partial = mock(WorldStorageMigrationOrchestrator.class);
        when(partial.migrate(any(), any())).thenReturn(Mono.just(new WorldStorageMigrationOrchestrator.Outcome(
                WorldStorageMigrationOrchestrator.Status.RETRYABLE_PARTIAL, "partial", 10)));
        WorldV2CanaryRunner.RunResult partialResult = runner(partial, source(), capacity(THRESHOLD), "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block();
        assertThat(partialResult.status()).isEqualTo(WorldV2CanaryRunner.Status.PARTIAL_PREPARED);
        verify(partial).migrate(any(), any());

        WorldStorageMigrationOrchestrator failing = mock(WorldStorageMigrationOrchestrator.class);
        when(failing.migrate(any(), any())).thenReturn(Mono.error(new IllegalStateException("failure")));
        WorldV2CanaryRunner.RunResult failureResult = runner(failing, source(), capacity(THRESHOLD), "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block();
        assertThat(failureResult.status()).isEqualTo(WorldV2CanaryRunner.Status.ORCHESTRATOR_FAILED);
        verify(failing).migrate(any(), any());
    }

    @Test
    void exactThresholdIsAdmittedAndMultipleOwnersAreRejected() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryRunner.RunResult boundary = runner(orchestrator, source(), capacity(THRESHOLD),
                "VALIDATE_ONLY", null).execute().block();
        assertThat(boundary.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_PASS);

        WorldV2CanaryProperties multiple = properties("VALIDATE_ONLY", null);
        multiple.setOwnerId(OWNER + ",22222222-2222-2222-2222-222222222222");
        WorldV2CanaryRunner.RunResult rejected = new WorldV2CanaryRunner(multiple, source(),
                capacity(THRESHOLD), orchestrator).execute().block();
        assertThat(rejected.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_OWNER_HASH);
        verify(orchestrator, never()).migrate(any(), any());
    }

    private static WorldV2CanaryRunner runner(WorldStorageMigrationOrchestrator orchestrator,
                                              WorldV2CanarySourceProbe source,
                                              WorldV2CanaryCapacityProvider capacity,
                                              String mode, String confirmation) {
        return new WorldV2CanaryRunner(properties(mode, confirmation), source, capacity, orchestrator);
    }

    private static WorldV2CanaryProperties properties(String mode, String confirmation) {
        WorldV2CanaryProperties properties = new WorldV2CanaryProperties();
        properties.setEnabled(true);
        properties.setOwnerId(OWNER.toString());
        properties.setExpectedOwnerHash(WorldV2CanaryRunner.sha256(OWNER.toString()));
        properties.setExpectedSourceSha(SOURCE_SHA);
        properties.setExpectedSemanticPlanSha(PLAN_SHA);
        properties.setExpectedCanonicalFingerprint(FINGERPRINT);
        properties.setMaxCurrentStorageBytes(THRESHOLD);
        properties.setQuotaBytes(QUOTA);
        properties.setRequiredHeadroomBytes(REQUIRED);
        properties.setRetainedCushionBytes(CUSHION);
        properties.setMode(mode);
        properties.setConfirm(confirmation);
        return properties;
    }

    private static WorldV2CanarySourceProbe source() {
        return ignored -> Mono.just(new WorldV2CanarySourceProbe.SourceSnapshot(
                WorldStorageMigrationExecutor.StoredState.LEGACY, SOURCE_SHA, true, true, 0, true, false,
                FINGERPRINT));
    }

    private static WorldV2CanaryCapacityProvider capacity(long current) {
        return () -> Mono.just(new WorldV2CanaryCapacityProvider.CapacitySample(
                current, QUOTA, REQUIRED, CUSHION));
    }

    private static void verifyNoSourceOrCapacityCalls(WorldV2CanarySourceProbe probe,
                                                      WorldV2CanaryCapacityProvider provider,
                                                      WorldStorageMigrationOrchestrator orchestrator) {
        verify(probe, never()).inspect(any());
        verify(provider, never()).sample();
        verify(orchestrator, never()).migrate(any(), any());
    }
}
