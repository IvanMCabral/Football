package com.footballmanager.adapters.in.web.testharness.dto;

/**
 * Request body for the dev/test harness player-swap matrix summary.
 *
 * @param starterPlayerId current starter to replace
 * @param benchPlayerId bench player to test in the starter's tactical slot
 * @param slotId optional UI/debug slot id; backend derives the exact slot from
 *               persisted lineup slots and only echoes this as caller context
 * @param seedStart first deterministic seed
 * @param seedCount number of seeds to aggregate
 */
public record PlayerSwapMatrixSummaryRequest(
    String starterPlayerId,
    String benchPlayerId,
    String slotId,
    Long seedStart,
    Integer seedCount
) {}
