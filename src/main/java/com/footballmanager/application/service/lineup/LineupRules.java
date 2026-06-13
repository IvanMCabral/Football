package com.footballmanager.application.service.lineup;

/**
 * V24D6U2: Constants for short-handed lineup rules.
 *
 * <p>Authoritative source of truth for lineup size bounds across the
 * application service layer and (read-only) the V24 engine layer.
 */
public final class LineupRules {

    /**
     * Minimum number of available (healthy + non-suspended) players
     * required to play a match. Below this, the match is not playable
     * and the use case returns 422.
     */
    public static final int MIN_AVAILABLE_PLAYERS = 7;

    /**
     * Maximum lineup size. 11 is the football standard.
     */
    public static final int MAX_LINEUP_PLAYERS = 11;

    /**
     * The full-strength target. Short-handed warnings fire when the
     * lineup is below this number.
     */
    public static final int TARGET_LINEUP_PLAYERS = 11;

    private LineupRules() {
        // constants holder
    }
}
