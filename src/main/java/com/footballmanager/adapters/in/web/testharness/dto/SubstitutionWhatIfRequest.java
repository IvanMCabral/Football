package com.footballmanager.adapters.in.web.testharness.dto;

/**
 * Dev harness request for a deterministic live-substitution replay.
 */
public record SubstitutionWhatIfRequest(
    String playerOffId,
    String playerOnId,
    Integer minute,
    Long seedStart,
    Integer seedCount,
    String controlledTeamSide
) {}
