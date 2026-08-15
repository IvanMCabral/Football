package com.footballmanager.infrastructure.world.canary;

import java.util.Objects;

/** Immutable authority for the single H7.9F certified canary. */
public final class WorldV2CanaryCertifiedAuthority {

    /** Historical selection fingerprint retained for provenance only; never used for authorization. */
    public static final String HISTORICAL_SELECTED_OWNER_MD5 = "6d963e62a2a6095b976ca78156a7ef0a";
    public static final String CERTIFIED_OWNER_SHA256 =
            "7fcae17b55af464cc929f242689bb456d9cc06718cf47d2e9642966041cd71e8";
    public static final String SOURCE_SHA = "2fe7dff53f2c6f07d222337cdda0a6963841e229aef721d2e11a395ba3d4d68f";
    public static final String SEMANTIC_PLAN_SHA =
            "298b32c98e0052269896f3f9caa9a89e1f70e3fa15019ee061d7247c751ebc7a";
    public static final String CANONICAL_FINGERPRINT =
            "1e654bec389796d232aba91685ac87d9ef1de08bcf3f5a7da563fdedbfb27000";
    public static final long MAX_PROVIDER_QUOTA_BYTES = 268_435_456L;
    public static final long MIN_REQUIRED_HEADROOM_BYTES = 2_436_344L;
    public static final long MIN_RETAINED_CUSHION_BYTES = 262_144L;
    public static final long MAX_ADMITTED_CURRENT_STORAGE_BYTES = 265_736_968L;

    private final String ownerHash;
    private final String sourceSha;
    private final String semanticPlanSha;
    private final String canonicalFingerprint;
    private final long maxProviderQuotaBytes;
    private final long minRequiredHeadroomBytes;
    private final long minRetainedCushionBytes;
    private final long maxAdmittedCurrentStorageBytes;

    private WorldV2CanaryCertifiedAuthority(String ownerHash, String sourceSha, String semanticPlanSha,
                                             String canonicalFingerprint, long maxProviderQuotaBytes,
                                             long minRequiredHeadroomBytes, long minRetainedCushionBytes,
                                             long maxAdmittedCurrentStorageBytes) {
        this.ownerHash = Objects.requireNonNull(ownerHash, "ownerHash");
        this.sourceSha = Objects.requireNonNull(sourceSha, "sourceSha");
        this.semanticPlanSha = Objects.requireNonNull(semanticPlanSha, "semanticPlanSha");
        this.canonicalFingerprint = Objects.requireNonNull(canonicalFingerprint, "canonicalFingerprint");
        this.maxProviderQuotaBytes = maxProviderQuotaBytes;
        this.minRequiredHeadroomBytes = minRequiredHeadroomBytes;
        this.minRetainedCushionBytes = minRetainedCushionBytes;
        this.maxAdmittedCurrentStorageBytes = maxAdmittedCurrentStorageBytes;
        if (maxProviderQuotaBytes - minRequiredHeadroomBytes - minRetainedCushionBytes
                != maxAdmittedCurrentStorageBytes) {
            throw new IllegalArgumentException("certified canary capacity authority is inconsistent");
        }
    }

    public static WorldV2CanaryCertifiedAuthority h79f() {
        return new WorldV2CanaryCertifiedAuthority(CERTIFIED_OWNER_SHA256, SOURCE_SHA, SEMANTIC_PLAN_SHA,
                CANONICAL_FINGERPRINT, MAX_PROVIDER_QUOTA_BYTES, MIN_REQUIRED_HEADROOM_BYTES,
                MIN_RETAINED_CUSHION_BYTES, MAX_ADMITTED_CURRENT_STORAGE_BYTES);
    }

    public EffectiveCapacity effectiveCapacity(WorldV2CanaryProperties properties) {
        long configuredQuota = valueOrDefault(properties.getQuotaBytes(), maxProviderQuotaBytes);
        long configuredRequired = valueOrDefault(properties.getRequiredHeadroomBytes(), minRequiredHeadroomBytes);
        long configuredCushion = valueOrDefault(properties.getRetainedCushionBytes(), minRetainedCushionBytes);
        long configuredMaximum = valueOrDefault(properties.getMaxCurrentStorageBytes(),
                maxAdmittedCurrentStorageBytes);
        if (configuredQuota <= 0 || configuredRequired < 0 || configuredCushion < 0 || configuredMaximum < 0) {
            throw new IllegalArgumentException("canary capacity configuration is invalid");
        }
        long quota = Math.min(configuredQuota, maxProviderQuotaBytes);
        long required = Math.max(configuredRequired, minRequiredHeadroomBytes);
        long cushion = Math.max(configuredCushion, minRetainedCushionBytes);
        long derivedThreshold;
        try {
            derivedThreshold = Math.subtractExact(Math.subtractExact(quota, required), cushion);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("canary capacity configuration overflows", error);
        }
        if (derivedThreshold < 0) {
            throw new IllegalArgumentException("canary capacity configuration leaves no safe admission range");
        }
        long maximum = Math.min(Math.min(configuredMaximum, maxAdmittedCurrentStorageBytes), derivedThreshold);
        return new EffectiveCapacity(quota, required, cushion, maximum);
    }

    public boolean operatorEchoesMatch(WorldV2CanaryProperties properties) {
        return optionalMatch(properties.getExpectedOwnerHash(), ownerHash)
                && optionalMatch(properties.getExpectedSourceSha(), sourceSha)
                && optionalMatch(properties.getExpectedSemanticPlanSha(), semanticPlanSha)
                && optionalMatch(properties.getExpectedCanonicalFingerprint(), canonicalFingerprint);
    }

    public String ownerHash() { return ownerHash; }
    public String sourceSha() { return sourceSha; }
    public String semanticPlanSha() { return semanticPlanSha; }
    public String canonicalFingerprint() { return canonicalFingerprint; }

    private static long valueOrDefault(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private static boolean optionalMatch(String configured, String certified) {
        return configured == null || configured.equals(certified);
    }

    public record EffectiveCapacity(long quotaBytes, long requiredHeadroomBytes,
                                    long retainedCushionBytes, long maxCurrentStorageBytes) {
        public boolean admits(long currentStorageBytes) {
            return currentStorageBytes >= 0 && currentStorageBytes <= maxCurrentStorageBytes;
        }

        public long marginBytes(long currentStorageBytes) {
            return maxCurrentStorageBytes - currentStorageBytes;
        }
    }
}
