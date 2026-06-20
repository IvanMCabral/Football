package com.footballmanager.adapters.in.web.testharness.dto;

/**
 * V24D20-TESTHARNESS — request body for {@code POST /api/v1/test-harness/career/set-formation}.
 *
 * <p>Accepts standard formation codes ("4-3-3", "4-4-2", "3-5-2",
 * "4-2-3-1", "5-3-2", "4-1-4-1"). Loose validation — the engine does
 * not enforce a strict pattern (matches existing {@code FormationController}
 * behavior). Non-blank check is done imperatively in the UseCase.
 */
public record SetFormationRequest(
    String formation
) {}
