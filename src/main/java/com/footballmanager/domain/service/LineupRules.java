package com.footballmanager.domain.service;

/**
 * Authoritative football lineup size rules.
 */
public final class LineupRules {

    /**
     * Minimum number of available players required to play a match.
     */
    public static final int MIN_AVAILABLE_PLAYERS = 7;

    /**
     * Maximum lineup size. 11 is the football standard.
     */
    public static final int MAX_LINEUP_PLAYERS = 11;

    /**
     * Full-strength target size.
     */
    public static final int TARGET_LINEUP_PLAYERS = 11;

    private LineupRules() {
    }
}
