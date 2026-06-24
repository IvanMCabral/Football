package com.footballmanager.adapters.in.web.testharness.dto;

/**
 * V24D20-TESTHARNESS — request body for {@code POST /api/v1/test-harness/career/create-custom}.
 *
 * <p>Wipes the existing career (if any) and starts a fresh one with
 * caller-controlled params. Useful for Bloque A/B smoke flows that need
 * a deterministic {@code teamsPerDivision}.
 *
 * <p>Field validation is performed imperatively in
 * {@code TestHarnessUseCaseImpl.createCustom} (matches the existing
 * project style — no jakarta.validation on DTOs).
 */
public record CreateCustomCareerRequest(
    String leagueId,
    String teamId,
    String difficulty,
    String gameSpeed,
    Integer teamsPerDivision
) {}
