package com.footballmanager.application.service.simulation.detailed;

/**
 * Discriminators stored in Redis/JSON snapshots.
 *
 * <p>The string value is intentionally historical: existing match detail and
 * baseline documents already contain it. Keep this compatibility isolated here
 * so current simulation code can use domain names elsewhere.
 */
final class PersistedEngineVersions {

    static final String PERSISTED_ENGINE_VERSION_V24 = "V24";

    private PersistedEngineVersions() {
    }
}
