package com.footballmanager.infrastructure.world.canary;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldV2CanaryOwnerHashingTest {

    private static final String LOWER = "11111111-1111-1111-1111-111111111111";
    private static final String UPPER = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";

    @Test
    void canonicalUuidUsesDeterministicFullSha256() {
        assertThat(WorldV2CanaryRunner.canonicalOwnerSha256(UUID.fromString(LOWER)))
                .isEqualTo("bafde89c041e1756082b933aaf16cad8e65dec48de748479352f657e89dd6da5")
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void uppercaseAndLowercaseTextResolveToSameCanonicalDigest() {
        UUID uppercase = UUID.fromString(UPPER);
        UUID lowercase = UUID.fromString(UPPER.toLowerCase(java.util.Locale.ROOT));
        assertThat(WorldV2CanaryRunner.canonicalOwnerSha256(uppercase))
                .isEqualTo(WorldV2CanaryRunner.canonicalOwnerSha256(lowercase));
    }

    @Test
    void malformedUuidFailsBeforeHashing() {
        assertThatThrownBy(() -> UUID.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
