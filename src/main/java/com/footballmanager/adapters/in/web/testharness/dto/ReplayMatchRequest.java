package com.footballmanager.adapters.in.web.testharness.dto;

/**
 * V24D20-SANDBOX-V2-MVP ÔÇö request body for
 * {@code POST /api/v1/test-harness/career/match/{matchId}/replay}.
 *
 * <p>Body is optional (Spring's {@code required=false}). If {@code seed}
 * is null, the UseCase falls back to {@code System.currentTimeMillis()}
 * (non-deterministic across runs).
 *
 * <p>Passing an explicit {@code seed} makes the replay reproducible
 * (same match + same seed + same formation = same result), which is
 * what REVISOR needs for the deterministic test sandbox v2 MVP.
 */
public record ReplayMatchRequest(
    Long seed
) {}
