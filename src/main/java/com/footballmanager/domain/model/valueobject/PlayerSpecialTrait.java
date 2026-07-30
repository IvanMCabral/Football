package com.footballmanager.domain.model.valueobject;

import java.util.UUID;

public record PlayerSpecialTrait(
        UUID playerId,
        String code,
        String name,
        String description
) {
}
