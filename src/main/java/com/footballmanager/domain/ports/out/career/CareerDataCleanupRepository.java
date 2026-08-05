package com.footballmanager.domain.ports.out.career;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Removes every Redis projection owned by a career user.
 *
 * <p>The port deliberately exposes ownership, not Redis key patterns. The
 * adapter is responsible for discovering the bounded, user-scoped families
 * and deleting them reactively.</p>
 */
public interface CareerDataCleanupRepository {

    Mono<CareerDataCleanupResult> deleteOwnedData(UUID userId, String careerId);
}
