package com.footballmanager.infrastructure.world.canary;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.canary.WorldV2CanaryCapacityProvider;
import com.footballmanager.application.service.world.canary.WorldV2CanarySourceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldV2CanaryRunnerTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final WorldV2CanaryCertifiedAuthority AUTHORITY = testAuthority();
    private static final long THRESHOLD = WorldV2CanaryCertifiedAuthority.MAX_ADMITTED_CURRENT_STORAGE_BYTES;

    @Test
    void validateOnlyPassesWithoutInvokingMigration() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryRunner.RunResult result = runner(orchestrator, source(), capacity(THRESHOLD),
                "VALIDATE_ONLY", null).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_PASS);
        assertThat(result.semanticPlanSha()).isEqualTo(WorldV2CanaryCertifiedAuthority.SEMANTIC_PLAN_SHA);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void absentOrBlankModeIsReadOnly(String mode) {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryRunner.RunResult result = runner(orchestrator, source(), capacity(THRESHOLD),
                mode, WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_PASS);
        assertThat(result.mode()).isEqualTo(WorldV2CanaryRunner.VALIDATE_ONLY_MODE);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"execute", "Execute", " EXECUTE", "EXECUTE ", "\tEXECUTE", "EXECUTE\n",
            "EXECUTE\t", "unknown"})
    void executeModeRequiresRawExactEquality(String mode) {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryRunner.RunResult result = runner(orchestrator, source(), capacity(THRESHOLD), mode,
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.EXECUTION_NOT_ARMED);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"pb123h79f_one_owner_execute", " PB123H79F_ONE_OWNER_EXECUTE",
            "PB123H79F_ONE_OWNER_EXECUTE ", "PB123H79F_ONE_OWNER_EXECUTE\n", "wrong"})
    void confirmationRequiresRawExactEquality(String confirmation) {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryRunner.RunResult result = runner(orchestrator, source(), capacity(THRESHOLD), "EXECUTE",
                confirmation).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.EXECUTION_NOT_ARMED);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void exactExecuteInvokesOrchestratorOnce() {
        WorldStorageMigrationOrchestrator orchestrator = migratedOrchestrator();
        WorldV2CanaryRunner.RunResult result = runner(orchestrator, source(), capacity(THRESHOLD), "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.MIGRATED);
        assertThat(result.orchestratorInvocations()).isOne();
        verify(orchestrator).migrate(any(), any());
    }

    @Test
    void secondInvocationIsRejectedBeforeEveryCollaborator() {
        WorldV2CanarySourceProbe source = mock(WorldV2CanarySourceProbe.class);
        WorldV2CanaryCapacityProvider capacity = mock(WorldV2CanaryCapacityProvider.class);
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        when(source.inspect(any())).thenReturn(Mono.just(validSource()));
        when(capacity.sample()).thenReturn(Mono.just(sample(THRESHOLD)));
        WorldV2CanaryRunner runner = runner(orchestrator, source, capacity, "VALIDATE_ONLY", null);
        assertThat(runner.execute().block().status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_PASS);
        WorldV2CanaryRunner.RunResult second = runner.execute().block();
        assertThat(second.status()).isEqualTo(WorldV2CanaryRunner.Status.RUNNER_ALREADY_INVOKED);
        assertThat(second.exitCode()).isOne();
        verify(source).inspect(any());
        verify(capacity).sample();
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void twoSubscriptionsToSameColdPublisherConsumeOnlyOneAttempt() {
        WorldV2CanarySourceProbe source = mock(WorldV2CanarySourceProbe.class);
        WorldV2CanaryCapacityProvider capacity = mock(WorldV2CanaryCapacityProvider.class);
        WorldStorageMigrationOrchestrator orchestrator = migratedOrchestrator();
        when(source.inspect(any())).thenReturn(Mono.just(validSource()));
        when(capacity.sample()).thenReturn(Mono.just(sample(THRESHOLD)));
        WorldV2CanaryRunner runner = runner(orchestrator, source, capacity, "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION);
        Mono<WorldV2CanaryRunner.RunResult> attempt = runner.execute();
        assertThat(attempt.block().status()).isEqualTo(WorldV2CanaryRunner.Status.MIGRATED);
        assertThat(attempt.block().status()).isEqualTo(WorldV2CanaryRunner.Status.RUNNER_ALREADY_INVOKED);
        verify(source).inspect(any());
        verify(capacity).sample();
        verify(orchestrator).migrate(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "*,", "*", "11111111-1111-1111-1111-111111111111,",
            "11111111-1111-1111-1111-111111111111,22222222-2222-2222-2222-222222222222",
            "11111111-1111-1111-1111-111111111111 22222222-2222-2222-2222-222222222222",
            "[\"11111111-1111-1111-1111-111111111111\"]",
            "11111111-1111-1111-1111-111111111111\n22222222-2222-2222-2222-222222222222"})
    void malformedOrMultiOwnerInputFailsBeforeSource(String rawOwner) {
        WorldV2CanarySourceProbe source = mock(WorldV2CanarySourceProbe.class);
        WorldV2CanaryCapacityProvider capacity = mock(WorldV2CanaryCapacityProvider.class);
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryProperties properties = properties("VALIDATE_ONLY", null);
        properties.setOwnerId(rawOwner);
        WorldV2CanaryRunner.RunResult result = new WorldV2CanaryRunner(properties, AUTHORITY, source, capacity,
                orchestrator).execute().block();
        assertThat(result.status()).isIn(WorldV2CanaryRunner.Status.VALIDATION_FAILED_STATE,
                WorldV2CanaryRunner.Status.VALIDATION_FAILED_OWNER_HASH);
        verifyNoCalls(source, capacity, orchestrator);
    }

    @Test
    void alternateOwnerCannotSelfAuthorizeWithMatchingRuntimeHash() {
        UUID ownerB = UUID.fromString("22222222-2222-2222-2222-222222222222");
        WorldV2CanaryProperties properties = properties("VALIDATE_ONLY", null);
        properties.setOwnerId(ownerB.toString());
        properties.setExpectedOwnerHash(WorldV2CanaryRunner.sha256(ownerB.toString()));
        WorldV2CanarySourceProbe source = mock(WorldV2CanarySourceProbe.class);
        WorldV2CanaryCapacityProvider capacity = mock(WorldV2CanaryCapacityProvider.class);
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryRunner.RunResult result = new WorldV2CanaryRunner(properties, AUTHORITY, source, capacity,
                orchestrator).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_STATE);
        verifyNoCalls(source, capacity, orchestrator);
    }

    @Test
    void alternateSourceAndFingerprintCannotSelfAuthorizeWithRuntimeEchoes() {
        WorldV2CanaryProperties sourceProperties = properties("VALIDATE_ONLY", null);
        sourceProperties.setExpectedSourceSha("source-b");
        WorldV2CanaryRunner.RunResult sourceResult = new WorldV2CanaryRunner(sourceProperties, AUTHORITY,
                ignored -> Mono.just(new WorldV2CanarySourceProbe.SourceSnapshot(
                        WorldStorageMigrationExecutor.StoredState.LEGACY, "source-b", true, true, 0,
                        true, false, WorldV2CanaryCertifiedAuthority.CANONICAL_FINGERPRINT)),
                capacity(THRESHOLD), mock(WorldStorageMigrationOrchestrator.class)).execute().block();
        assertThat(sourceResult.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_STATE);

        WorldV2CanaryProperties fingerprintProperties = properties("VALIDATE_ONLY", null);
        fingerprintProperties.setExpectedCanonicalFingerprint("fingerprint-b");
        WorldV2CanaryRunner.RunResult fingerprintResult = new WorldV2CanaryRunner(fingerprintProperties,
                AUTHORITY, source(), capacity(THRESHOLD), mock(WorldStorageMigrationOrchestrator.class))
                .execute().block();
        assertThat(fingerprintResult.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_STATE);
    }

    @Test
    void semanticPlanRuntimeEchoCannotReplaceCertifiedAuthority() {
        WorldV2CanaryProperties properties = properties("VALIDATE_ONLY", null);
        properties.setExpectedSemanticPlanSha("plan-b");
        WorldV2CanarySourceProbe source = mock(WorldV2CanarySourceProbe.class);
        WorldV2CanaryRunner.RunResult result = new WorldV2CanaryRunner(properties, AUTHORITY, source,
                mock(WorldV2CanaryCapacityProvider.class), mock(WorldStorageMigrationOrchestrator.class))
                .execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_STATE);
        verify(source, never()).inspect(any());
    }

    @Test
    void sourceValidationCompletesBeforeCapacitySamplingAndMigration() {
        List<String> order = new ArrayList<>();
        WorldV2CanarySourceProbe source = ignored -> Mono.defer(() -> {
            order.add("source");
            return Mono.just(validSource());
        });
        WorldV2CanaryCapacityProvider capacity = () -> Mono.defer(() -> {
            order.add("capacity");
            return Mono.just(sample(THRESHOLD));
        });
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        when(orchestrator.migrate(any(), any())).thenAnswer(ignored -> Mono.defer(() -> {
            order.add("orchestrator");
            return Mono.just(migratedOutcome());
        }));
        WorldV2CanaryRunner.RunResult result = runner(orchestrator, source, capacity, "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.MIGRATED);
        assertThat(order).containsExactly("source", "capacity", "orchestrator");
    }

    @Test
    void invalidSourceNeverSamplesCapacity() {
        WorldV2CanaryCapacityProvider capacity = mock(WorldV2CanaryCapacityProvider.class);
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanarySourceProbe changed = ignored -> Mono.just(new WorldV2CanarySourceProbe.SourceSnapshot(
                WorldStorageMigrationExecutor.StoredState.LEGACY, "changed", true, true, 0, true, false,
                WorldV2CanaryCertifiedAuthority.CANONICAL_FINGERPRINT));
        WorldV2CanaryRunner.RunResult result = runner(orchestrator, changed, capacity, "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_SOURCE_CHANGED);
        verify(capacity, never()).sample();
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void providerAndCapacityFailuresNeverInvokeOrchestrator() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        WorldV2CanaryCapacityProvider unauthorized = () -> Mono.error(
                new WorldV2CanaryCapacityProvider.ProviderCapacityException(
                        WorldV2CanaryCapacityProvider.FailureKind.AUTHENTICATION, "redacted", null));
        assertThat(runner(orchestrator, source(), unauthorized, "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block().status())
                .isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_PROVIDER_AUTH);
        assertThat(runner(orchestrator, source(), capacity(THRESHOLD + 1), "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block().status())
                .isEqualTo(WorldV2CanaryRunner.Status.VALIDATION_FAILED_CAPACITY);
        verify(orchestrator, never()).migrate(any(), any());
    }

    @Test
    void partialAndExceptionInvokeOnceWithoutRetry() {
        WorldStorageMigrationOrchestrator partial = mock(WorldStorageMigrationOrchestrator.class);
        when(partial.migrate(any(), any())).thenReturn(Mono.just(new WorldStorageMigrationOrchestrator.Outcome(
                WorldStorageMigrationOrchestrator.Status.RETRYABLE_PARTIAL, "partial", 10)));
        assertThat(runner(partial, source(), capacity(THRESHOLD), "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block().status())
                .isEqualTo(WorldV2CanaryRunner.Status.PARTIAL_PREPARED);
        verify(partial).migrate(any(), any());

        WorldStorageMigrationOrchestrator failing = mock(WorldStorageMigrationOrchestrator.class);
        when(failing.migrate(any(), any())).thenReturn(Mono.error(new IllegalStateException("failure")));
        assertThat(runner(failing, source(), capacity(THRESHOLD), "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block().status())
                .isEqualTo(WorldV2CanaryRunner.Status.ORCHESTRATOR_FAILED);
        verify(failing).migrate(any(), any());
    }

    @Test
    void alreadyMigratedOutcomeIsNotAnAcceptedRunnerSuccess() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        when(orchestrator.migrate(any(), any())).thenReturn(Mono.just(new WorldStorageMigrationOrchestrator.Outcome(
                WorldStorageMigrationOrchestrator.Status.ALREADY_MIGRATED_VALID, "unexpected", 0)));
        WorldV2CanaryRunner.RunResult result = runner(orchestrator, source(), capacity(THRESHOLD), "EXECUTE",
                WorldV2CanaryRunner.EXECUTE_CONFIRMATION).execute().block();
        assertThat(result.status()).isEqualTo(WorldV2CanaryRunner.Status.ORCHESTRATOR_FAILED);
        assertThat(result.exitCode()).isOne();
    }

    @Test
    void failedFirstAttemptPermanentlyConsumesRunner() {
        AtomicInteger capacityCalls = new AtomicInteger();
        WorldV2CanaryCapacityProvider capacity = () -> Mono.defer(() -> {
            capacityCalls.incrementAndGet();
            return Mono.error(new IllegalStateException("down"));
        });
        WorldV2CanaryRunner runner = runner(mock(WorldStorageMigrationOrchestrator.class), source(), capacity,
                "VALIDATE_ONLY", null);
        assertThat(runner.execute().block().status()).isEqualTo(WorldV2CanaryRunner.Status.ORCHESTRATOR_FAILED);
        assertThat(runner.execute().block().status()).isEqualTo(WorldV2CanaryRunner.Status.RUNNER_ALREADY_INVOKED);
        assertThat(capacityCalls).hasValue(1);
    }

    @Test
    void logsOwnerHashButNeverRawOwner() {
        Logger logger = (Logger) LoggerFactory.getLogger(WorldV2CanaryRunner.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            runner(mock(WorldStorageMigrationOrchestrator.class), source(), capacity(THRESHOLD),
                    "VALIDATE_ONLY", null).run(new DefaultApplicationArguments());
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + right);
            assertThat(logs).doesNotContain(OWNER.toString(), "UPSTASH_API_KEY", "Authorization", "Basic ");
            assertThat(logs).contains(AUTHORITY.ownerHash());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void providerFailureNeverLogsProviderSecretMaterial() {
        String secretMarker = "provider-secret-must-not-leak";
        WorldV2CanaryCapacityProvider capacity = () -> Mono.error(
                new WorldV2CanaryCapacityProvider.ProviderCapacityException(
                        WorldV2CanaryCapacityProvider.FailureKind.AUTHENTICATION, secretMarker, null));
        Logger logger = (Logger) LoggerFactory.getLogger(WorldV2CanaryRunner.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            WorldV2CanaryRunner runner = runner(mock(WorldStorageMigrationOrchestrator.class), source(), capacity,
                    "VALIDATE_ONLY", null);
            assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                    .isInstanceOf(WorldV2CanaryRunner.WorldV2CanaryExecutionException.class)
                    .hasMessageNotContaining(secretMarker);
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + right);
            assertThat(logs).doesNotContain(secretMarker, OWNER.toString(), "Authorization", "Basic ");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static WorldV2CanaryRunner runner(WorldStorageMigrationOrchestrator orchestrator,
                                              WorldV2CanarySourceProbe source,
                                              WorldV2CanaryCapacityProvider capacity,
                                              String mode, String confirmation) {
        return new WorldV2CanaryRunner(properties(mode, confirmation), AUTHORITY, source, capacity, orchestrator);
    }

    private static WorldV2CanaryProperties properties(String mode, String confirmation) {
        WorldV2CanaryProperties properties = new WorldV2CanaryProperties();
        properties.setEnabled(true);
        properties.setOwnerId(OWNER.toString());
        properties.setExpectedSourceSha(WorldV2CanaryCertifiedAuthority.SOURCE_SHA);
        properties.setExpectedSemanticPlanSha(WorldV2CanaryCertifiedAuthority.SEMANTIC_PLAN_SHA);
        properties.setExpectedCanonicalFingerprint(WorldV2CanaryCertifiedAuthority.CANONICAL_FINGERPRINT);
        properties.setMode(mode);
        properties.setConfirm(confirmation);
        return properties;
    }

    private static WorldV2CanarySourceProbe source() { return ignored -> Mono.just(validSource()); }

    private static WorldV2CanarySourceProbe.SourceSnapshot validSource() {
        return new WorldV2CanarySourceProbe.SourceSnapshot(WorldStorageMigrationExecutor.StoredState.LEGACY,
                WorldV2CanaryCertifiedAuthority.SOURCE_SHA, true, true, 0, true, false,
                WorldV2CanaryCertifiedAuthority.CANONICAL_FINGERPRINT);
    }

    private static WorldV2CanaryCapacityProvider capacity(long current) {
        return () -> Mono.just(sample(current));
    }

    private static WorldV2CanaryCapacityProvider.CapacitySample sample(long current) {
        return new WorldV2CanaryCapacityProvider.CapacitySample(current);
    }

    private static WorldStorageMigrationOrchestrator migratedOrchestrator() {
        WorldStorageMigrationOrchestrator orchestrator = mock(WorldStorageMigrationOrchestrator.class);
        when(orchestrator.migrate(any(), any())).thenReturn(Mono.just(migratedOutcome()));
        return orchestrator;
    }

    private static WorldStorageMigrationOrchestrator.Outcome migratedOutcome() {
        return new WorldStorageMigrationOrchestrator.Outcome(
                WorldStorageMigrationOrchestrator.Status.MIGRATED, "ok", 10);
    }

    private static WorldV2CanaryCertifiedAuthority testAuthority() {
        WorldV2CanaryCertifiedAuthority certified = WorldV2CanaryCertifiedAuthority.h79f();
        WorldV2CanaryCertifiedAuthority authority = mock(WorldV2CanaryCertifiedAuthority.class);
        when(authority.ownerHash()).thenReturn(WorldV2CanaryRunner.sha256(OWNER.toString()));
        when(authority.sourceSha()).thenReturn(WorldV2CanaryCertifiedAuthority.SOURCE_SHA);
        when(authority.semanticPlanSha()).thenReturn(WorldV2CanaryCertifiedAuthority.SEMANTIC_PLAN_SHA);
        when(authority.canonicalFingerprint()).thenReturn(WorldV2CanaryCertifiedAuthority.CANONICAL_FINGERPRINT);
        when(authority.effectiveCapacity(any())).thenAnswer(invocation ->
                certified.effectiveCapacity(invocation.getArgument(0)));
        when(authority.operatorEchoesMatch(any())).thenAnswer(invocation ->
                certified.operatorEchoesMatch(invocation.getArgument(0)));
        return authority;
    }

    private static void verifyNoCalls(WorldV2CanarySourceProbe source,
                                      WorldV2CanaryCapacityProvider capacity,
                                      WorldStorageMigrationOrchestrator orchestrator) {
        verify(source, never()).inspect(any());
        verify(capacity, never()).sample();
        verify(orchestrator, never()).migrate(any(), any());
    }
}
