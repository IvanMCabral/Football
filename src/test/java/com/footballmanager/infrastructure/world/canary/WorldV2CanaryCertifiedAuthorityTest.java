package com.footballmanager.infrastructure.world.canary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldV2CanaryCertifiedAuthorityTest {

    private final WorldV2CanaryCertifiedAuthority authority = WorldV2CanaryCertifiedAuthority.h79f();

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
