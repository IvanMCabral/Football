package com.footballmanager.application.service.match;

import java.util.UUID;

/**
 * Signals that a requested match session is not active for the authenticated
 * owner.  It is deliberately distinct from generic state validation so the
 * HTTP adapter can expose a stable 404 contract without converting unrelated
 * failures into a client error.
 */
public final class MatchSessionNotFoundException extends RuntimeException {

    public MatchSessionNotFoundException(UUID matchId) {
        super("match session not found: " + matchId);
    }
}
