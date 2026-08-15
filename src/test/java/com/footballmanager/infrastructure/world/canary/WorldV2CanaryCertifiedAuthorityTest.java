package com.footballmanager.infrastructure.world.canary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldV2CanaryCertifiedAuthorityTest {

    private final WorldV2CanaryCertifiedAuthority authority = WorldV2CanaryCertifiedAuthority.h79f();

    @Test
    void productionAuthorityHasExactCertifiedValues() {
        assertThat(authority.ownerHash())
                .isEqualTo(WorldV2CanaryCertifiedAuthority.CERTIFIED_OWNER_SHA256)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
        assertThat(WorldV2CanaryCertifiedAuthority.HISTORICAL_SELECTED_OWNER_MD5)
                .hasSize(32)
                .matches("[0-9a-f]{32}");
        assertThat(authority.sourceSha()).isEqualTo(WorldV2CanaryCertifiedAuthority.SOURCE_SHA);
        assertThat(authority.semanticPlanSha()).isEqualTo(WorldV2CanaryCertifiedAuthority.SEMANTIC_PLAN_SHA);
        assertThat(authority.canonicalFingerprint())
                .isEqualTo(WorldV2CanaryCertifiedAuthority.CANONICAL_FINGERPRINT);

        var effective = authority.effectiveCapacity(new WorldV2CanaryProperties());
        assertThat(effective.quotaBytes()).isEqualTo(WorldV2CanaryCertifiedAuthority.MAX_PROVIDER_QUOTA_BYTES);
        assertThat(effective.requiredHeadroomBytes())
                .isEqualTo(WorldV2CanaryCertifiedAuthority.MIN_REQUIRED_HEADROOM_BYTES);
        assertThat(effective.retainedCushionBytes())
                .isEqualTo(WorldV2CanaryCertifiedAuthority.MIN_RETAINED_CUSHION_BYTES);
        assertThat(effective.maxCurrentStorageBytes())
                .isEqualTo(WorldV2CanaryCertifiedAuthority.MAX_ADMITTED_CURRENT_STORAGE_BYTES);
    }

    @Test
    void certifiedDefaultsProduceExactThreshold() {
        var effective = authority.effectiveCapacity(new WorldV2CanaryProperties());
        assertThat(effective.quotaBytes()).isEqualTo(268_435_456L);
        assertThat(effective.requiredHeadroomBytes()).isEqualTo(2_436_344L);
        assertThat(effective.retainedCushionBytes()).isEqualTo(262_144L);
        assertThat(effective.maxCurrentStorageBytes()).isEqualTo(265_736_968L);
        assertThat(effective.admits(265_736_968L)).isTrue();
        assertThat(effective.admits(265_736_969L)).isFalse();
    }

    @Test
    void lowerConfiguredThresholdWinsAndHigherCannotWeakenCeiling() {
        WorldV2CanaryProperties lower = new WorldV2CanaryProperties();
        lower.setMaxCurrentStorageBytes(250_000_000L);
        assertThat(authority.effectiveCapacity(lower).maxCurrentStorageBytes()).isEqualTo(250_000_000L);

        WorldV2CanaryProperties higher = new WorldV2CanaryProperties();
        higher.setMaxCurrentStorageBytes(268_435_456L);
        assertThat(authority.effectiveCapacity(higher).maxCurrentStorageBytes()).isEqualTo(265_736_968L);
    }

    @Test
    void headroomCanOnlyBecomeStricter() {
        WorldV2CanaryProperties lower = new WorldV2CanaryProperties();
        lower.setRequiredHeadroomBytes(0L);
        assertThat(authority.effectiveCapacity(lower).requiredHeadroomBytes()).isEqualTo(2_436_344L);

        WorldV2CanaryProperties higher = new WorldV2CanaryProperties();
        higher.setRequiredHeadroomBytes(4_000_000L);
        assertThat(authority.effectiveCapacity(higher).requiredHeadroomBytes()).isEqualTo(4_000_000L);
        assertThat(authority.effectiveCapacity(higher).maxCurrentStorageBytes()).isEqualTo(264_173_312L);
    }

    @Test
    void retainedCushionCanOnlyBecomeStricter() {
        WorldV2CanaryProperties lower = new WorldV2CanaryProperties();
        lower.setRetainedCushionBytes(0L);
        assertThat(authority.effectiveCapacity(lower).retainedCushionBytes()).isEqualTo(262_144L);

        WorldV2CanaryProperties higher = new WorldV2CanaryProperties();
        higher.setRetainedCushionBytes(1_000_000L);
        assertThat(authority.effectiveCapacity(higher).retainedCushionBytes()).isEqualTo(1_000_000L);
        assertThat(authority.effectiveCapacity(higher).maxCurrentStorageBytes()).isEqualTo(264_999_112L);
    }

    @Test
    void quotaCanOnlyBecomeStricter() {
        WorldV2CanaryProperties higher = new WorldV2CanaryProperties();
        higher.setQuotaBytes(999_999_999L);
        assertThat(authority.effectiveCapacity(higher).quotaBytes()).isEqualTo(268_435_456L);

        WorldV2CanaryProperties lower = new WorldV2CanaryProperties();
        lower.setQuotaBytes(260_000_000L);
        var effective = authority.effectiveCapacity(lower);
        assertThat(effective.quotaBytes()).isEqualTo(260_000_000L);
        assertThat(effective.maxCurrentStorageBytes()).isEqualTo(257_301_512L);
    }

    @Test
    void invalidCapacityCombinationFailsClosed() {
        WorldV2CanaryProperties impossible = new WorldV2CanaryProperties();
        impossible.setQuotaBytes(1L);
        assertThatThrownBy(() -> authority.effectiveCapacity(impossible))
                .isInstanceOf(IllegalArgumentException.class);

        WorldV2CanaryProperties negative = new WorldV2CanaryProperties();
        negative.setMaxCurrentStorageBytes(-1L);
        assertThatThrownBy(() -> authority.effectiveCapacity(negative))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operatorEchoesCanConfirmButNeverDefineAuthority() {
        WorldV2CanaryProperties properties = new WorldV2CanaryProperties();
        assertThat(authority.operatorEchoesMatch(properties)).isTrue();
        properties.setExpectedOwnerHash("alternate");
        assertThat(authority.operatorEchoesMatch(properties)).isFalse();
        properties.setExpectedOwnerHash(" ");
        assertThat(authority.operatorEchoesMatch(properties)).isFalse();
    }
}
