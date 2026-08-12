package com.footballmanager.domain.model.metadata;

/** Semantic classification for identity-shaped values in durable world/career models. */
public enum WorldIdentityDomain {
    WORLD_TEAM(true),
    WORLD_PLAYER(true),
    REAL_TEAM(true),
    REAL_PLAYER(true),
    SESSION_TEAM(true),
    SESSION_PLAYER(true),
    OTHER_ID(false),
    NON_ID_TEXT(false),
    OPAQUE_VALUE(false);

    private final boolean reference;

    WorldIdentityDomain(boolean reference) {
        this.reference = reference;
    }

    public boolean isReference() {
        return reference;
    }
}
