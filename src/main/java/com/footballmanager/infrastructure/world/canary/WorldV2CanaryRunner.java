package com.footballmanager.infrastructure.world.canary;

import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.canary.WorldV2CanaryCapacityProvider;
import com.footballmanager.application.service.world.canary.WorldV2CanarySourceProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Non-public, single-owner World V2 operational runner.
 *
 * <p>The bean is absent unless both the dedicated profile and explicit enable
 * property are active. The default mode is read-only validation.</p>
 */
@Slf4j
@Component
@Profile("world-v2-canary")
@ConditionalOnProperty(name = "world.v2.canary.enabled", havingValue = "true")
public final class WorldV2CanaryRunner implements ApplicationRunner, ExitCodeGenerator {

    static final String EXECUTE_MODE = "EXECUTE";
    static final String VALIDATE_ONLY_MODE = "VALIDATE_ONLY";
    static final String EXECUTE_CONFIRMATION = "PB123H79F_ONE_OWNER_EXECUTE";
    static final String APPROVED_OWNER_HASH = "6d963e62a2a6095b976ca78156a7ef0a";
    static final String APPROVED_SOURCE_SHA = "2fe7dff53f2c6f07d222337cdda0a6963841e229aef721d2e11a395ba3d4d68f";
    static final String APPROVED_SEMANTIC_PLAN_SHA = "298b32c98e0052269896f3f9caa9a89e1f70e3fa15019ee061d7247c751ebc7a";
    static final String APPROVED_CANONICAL_FINGERPRINT = "1e654bec389796d232aba91685ac87d9ef1de08bcf3f5a7da563fdedbfb27000";
    static final long APPROVED_QUOTA_BYTES = 268_435_456L;
    static final long APPROVED_REQUIRED_HEADROOM_BYTES = 2_436_344L;
    static final long APPROVED_RETAINED_CUSHION_BYTES = 262_144L;
    static final long APPROVED_MAX_CURRENT_STORAGE_BYTES = 265_736_968L;

    private final WorldV2CanaryProperties properties;
    private final WorldV2CanarySourceProbe sourceProbe;
    private final WorldV2CanaryCapacityProvider capacityProvider;
    private final WorldStorageMigrationOrchestrator orchestrator;

    private volatile RunResult lastResult;

    public WorldV2CanaryRunner(WorldV2CanaryProperties properties,
                               WorldV2CanarySourceProbe sourceProbe,
                               WorldV2CanaryCapacityProvider capacityProvider,
                               WorldStorageMigrationOrchestrator orchestrator) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.sourceProbe = Objects.requireNonNull(sourceProbe, "sourceProbe");
        this.capacityProvider = Objects.requireNonNull(capacityProvider, "capacityProvider");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    }

    @Override
    public void run(ApplicationArguments args) {
        RunResult result = execute().block(Duration.ofMinutes(2));
        lastResult = result;
        log.info("World V2 canary runner mode={} status={} ownerHash={} sourceSha={} semanticPlanSha={} "
                        + "currentStorage={} quota={} capacityMargin={} orchestratorInvocations={}",
                result.mode(), result.status(), result.ownerHash(), result.sourceSha(),
                result.semanticPlanSha(), result.currentStorageBytes(), result.quotaBytes(),
                result.capacityMarginBytes(), result.orchestratorInvocations());
        if (result.exitCode() != 0) {
            throw new WorldV2CanaryExecutionException(result.status(), result.reason());
        }
    }

    /** Executes the bounded flow once; tests use this API without starting a server. */
    public Mono<RunResult> execute() {
        return Mono.defer(() -> {
            ConfigurationValidation config = validateConfiguration();
            if (!config.valid()) return Mono.just(failed(config.status(), config.reason(), null, null));

            UUID ownerId;
            try {
                ownerId = UUID.fromString(properties.getOwnerId());
            } catch (IllegalArgumentException error) {
                return Mono.just(failed(Status.VALIDATION_FAILED_OWNER_HASH,
                        "owner identifier is invalid", null, null));
            }

            String ownerHash = sha256(properties.getOwnerId());
            if (!ownerHash.equalsIgnoreCase(properties.getExpectedOwnerHash())) {
                return Mono.just(failed(Status.VALIDATION_FAILED_OWNER_HASH,
                        "owner hash does not match the configured authority", ownerHash, null));
            }

            return sourceProbe.inspect(ownerId)
                    .zipWith(capacityProvider.sample())
                    .flatMap(tuple -> evaluate(ownerId, ownerHash, tuple.getT1(), tuple.getT2()))
                    .onErrorResume(WorldV2CanaryCapacityProvider.ProviderCapacityException.class,
                            error -> Mono.just(failed(
                                    error.kind() == WorldV2CanaryCapacityProvider.FailureKind.AUTHENTICATION
                                            ? Status.VALIDATION_FAILED_PROVIDER_AUTH
                                            : Status.VALIDATION_FAILED_CAPACITY,
                                    "provider capacity precondition failed", ownerHash, null)))
                    .onErrorResume(error -> Mono.just(failed(Status.ORCHESTRATOR_FAILED,
                            "canary precondition flow failed", ownerHash, null)));
        });
    }

    private Mono<RunResult> evaluate(UUID ownerId, String ownerHash,
                                      WorldV2CanarySourceProbe.SourceSnapshot source,
                                      WorldV2CanaryCapacityProvider.CapacitySample capacity) {
        RunResult sourceFailure = validateSource(ownerHash, source);
        if (sourceFailure != null) return Mono.just(sourceFailure);

        if (capacity.quotaBytes() != properties.getQuotaBytes()
                || capacity.requiredHeadroomBytes() != properties.getRequiredHeadroomBytes()
                || capacity.retainedCushionBytes() != properties.getRetainedCushionBytes()
                || !capacity.admitted(properties.getMaxCurrentStorageBytes())) {
            return Mono.just(new RunResult(Status.VALIDATION_FAILED_CAPACITY,
                    "fresh provider capacity is outside the configured admission contract", mode(), ownerHash,
                    source.sourceSha(), properties.getExpectedSemanticPlanSha(), capacity.currentStorageBytes(),
                    capacity.quotaBytes(), capacity.admissionThresholdBytes() - capacity.currentStorageBytes(), 0));
        }

        if (!VALIDATE_ONLY_MODE.equals(mode()) && !EXECUTE_MODE.equals(mode())) {
            return Mono.just(failed(Status.EXECUTION_NOT_ARMED, "unsupported canary mode", ownerHash, capacity));
        }
        if (VALIDATE_ONLY_MODE.equals(mode())) {
            return Mono.just(new RunResult(Status.VALIDATION_PASS, "all read-only preconditions passed", mode(),
                    ownerHash, source.sourceSha(), properties.getExpectedSemanticPlanSha(),
                    capacity.currentStorageBytes(), capacity.quotaBytes(),
                    capacity.admissionThresholdBytes() - capacity.currentStorageBytes(), 0));
        }
        if (!EXECUTE_CONFIRMATION.equals(properties.getConfirm())) {
            return Mono.just(new RunResult(Status.EXECUTION_NOT_ARMED,
                    "explicit execute confirmation is missing or invalid", mode(), ownerHash,
                    source.sourceSha(), properties.getExpectedSemanticPlanSha(), capacity.currentStorageBytes(),
                    capacity.quotaBytes(), capacity.admissionThresholdBytes() - capacity.currentStorageBytes(), 0));
        }

        WorldStorageMigrationOrchestrator.CapacitySnapshot snapshot =
                new WorldStorageMigrationOrchestrator.CapacitySnapshot(
                        capacity.currentStorageBytes(), capacity.quotaBytes(),
                        capacity.requiredHeadroomBytes(), capacity.retainedCushionBytes());
        return orchestrator.migrate(ownerId, snapshot)
                .map(outcome -> resultFromOutcome(outcome, ownerHash, source.sourceSha(), capacity))
                .onErrorResume(error -> Mono.just(new RunResult(Status.ORCHESTRATOR_FAILED,
                        "orchestrator invocation failed", mode(), ownerHash, source.sourceSha(),
                        properties.getExpectedSemanticPlanSha(), capacity.currentStorageBytes(), capacity.quotaBytes(),
                        capacity.admissionThresholdBytes() - capacity.currentStorageBytes(), 1)));
    }

    private RunResult validateSource(String ownerHash, WorldV2CanarySourceProbe.SourceSnapshot source) {
        if (source.state() != WorldStorageMigrationExecutor.StoredState.LEGACY) {
            return failed(Status.VALIDATION_FAILED_STATE, "source state is not LEGACY", ownerHash, null);
        }
        if (!source.ownerMatch()) {
            return failed(Status.VALIDATION_FAILED_OWNER_HASH, "source owner does not match", ownerHash, null);
        }
        if (!properties.getExpectedSourceSha().equals(source.sourceSha())) {
            return failed(Status.VALIDATION_FAILED_SOURCE_CHANGED, "source checksum changed", ownerHash, null);
        }
        if (!source.careerAbsent() || source.referenceCount() != 0) {
            return failed(Status.VALIDATION_FAILED_REFERENCE, "durable career references are not empty", ownerHash, null);
        }
        if (!source.catalogCompatible()
                || !properties.getExpectedCanonicalFingerprint().equals(source.canonicalFingerprint())) {
            return failed(Status.VALIDATION_FAILED_CATALOG, "canonical catalog fingerprint is not certified", ownerHash, null);
        }
        return null;
    }

    private ConfigurationValidation validateConfiguration() {
        if (blank(properties.getOwnerId()) || blank(properties.getExpectedOwnerHash())
                || blank(properties.getExpectedSourceSha()) || blank(properties.getExpectedSemanticPlanSha())
                || blank(properties.getExpectedCanonicalFingerprint()) || properties.getMaxCurrentStorageBytes() == null
                || properties.getQuotaBytes() == null || properties.getRequiredHeadroomBytes() == null
                || properties.getRetainedCushionBytes() == null) {
            return new ConfigurationValidation(false, Status.VALIDATION_FAILED_STATE,
                    "required canary activation material is missing");
        }
        if (properties.getMaxCurrentStorageBytes() < 0 || properties.getQuotaBytes() <= 0
                || properties.getRequiredHeadroomBytes() < 0 || properties.getRetainedCushionBytes() < 0
                || properties.getMaxCurrentStorageBytes() > properties.getQuotaBytes()) {
            return new ConfigurationValidation(false, Status.VALIDATION_FAILED_CAPACITY,
                    "canary capacity contract is invalid");
        }
        return new ConfigurationValidation(true, null, null);
    }

    private RunResult resultFromOutcome(WorldStorageMigrationOrchestrator.Outcome outcome,
                                        String ownerHash, String sourceSha,
                                        WorldV2CanaryCapacityProvider.CapacitySample capacity) {
        Status status = switch (outcome.status()) {
            case MIGRATED -> Status.MIGRATED;
            case ALREADY_MIGRATED_VALID -> Status.ALREADY_MIGRATED_VALID;
            case BLOCKED_CAPACITY -> Status.VALIDATION_FAILED_CAPACITY;
            case BLOCKED_REFERENCE -> Status.VALIDATION_FAILED_REFERENCE;
            case SOURCE_CHANGED -> Status.VALIDATION_FAILED_SOURCE_CHANGED;
            case INVALID_LEGACY, INVALID_V2 -> Status.ORCHESTRATOR_FAILED;
            case RETRYABLE_PARTIAL -> Status.PARTIAL_PREPARED;
        };
        return new RunResult(status, "orchestrator result: " + status, mode(), ownerHash, sourceSha,
                properties.getExpectedSemanticPlanSha(), capacity.currentStorageBytes(), capacity.quotaBytes(),
                capacity.admissionThresholdBytes() - capacity.currentStorageBytes(), 1);
    }

    private RunResult failed(Status status, String reason, String ownerHash,
                             WorldV2CanaryCapacityProvider.CapacitySample capacity) {
        long current = capacity == null ? -1 : capacity.currentStorageBytes();
        long quota = capacity == null ? -1 : capacity.quotaBytes();
        long margin = capacity == null ? -1 : capacity.admissionThresholdBytes() - capacity.currentStorageBytes();
        return new RunResult(status, reason, mode(), ownerHash, null, properties.getExpectedSemanticPlanSha(),
                current, quota, margin, 0);
    }

    private String mode() {
        return blank(properties.getMode()) ? VALIDATE_ONLY_MODE : properties.getMode().trim().toUpperCase();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    public RunResult lastResult() { return lastResult; }

    @Override
    public int getExitCode() {
        return lastResult == null ? 1 : lastResult.exitCode();
    }

    public enum Status {
        VALIDATION_PASS,
        VALIDATION_FAILED_OWNER_HASH,
        VALIDATION_FAILED_SOURCE_CHANGED,
        VALIDATION_FAILED_STATE,
        VALIDATION_FAILED_REFERENCE,
        VALIDATION_FAILED_CATALOG,
        VALIDATION_FAILED_CAPACITY,
        VALIDATION_FAILED_PROVIDER_AUTH,
        EXECUTION_NOT_ARMED,
        MIGRATED,
        ALREADY_MIGRATED_VALID,
        PARTIAL_PREPARED,
        ORCHESTRATOR_FAILED
    }

    public record RunResult(Status status, String reason, String mode, String ownerHash,
                            String sourceSha, String semanticPlanSha, long currentStorageBytes,
                            long quotaBytes, long capacityMarginBytes, int orchestratorInvocations) {
        public int exitCode() {
            return status == Status.VALIDATION_PASS || status == Status.MIGRATED
                    || status == Status.ALREADY_MIGRATED_VALID ? 0 : 1;
        }
    }

    private record ConfigurationValidation(boolean valid, Status status, String reason) { }

    public static final class WorldV2CanaryExecutionException extends RuntimeException {
        public WorldV2CanaryExecutionException(Status status, String reason) {
            super(status + ": " + reason);
        }
    }
}
