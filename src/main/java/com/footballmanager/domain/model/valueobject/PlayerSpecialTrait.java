package com.footballmanager.domain.model.valueobject;

import com.footballmanager.domain.model.metadata.WorldIdentityDomain;
import com.footballmanager.domain.model.metadata.WorldIdentityReference;

import java.util.UUID;

public record PlayerSpecialTrait(
        @WorldIdentityReference(domain = WorldIdentityDomain.REAL_PLAYER) UUID playerId,
        @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT) String code,
        @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT) String name,
        @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT) String description
) {
}
