package com.footballmanager.application.observability;

import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sanitized, request-scoped timings for the public world reload path.
 * The holder contains only counts, durations and byte totals; no identity,
 * payload or Redis key is retained.
 */
public final class ReloadWorldTiming {

    public static final String CONTEXT_KEY = ReloadWorldTiming.class.getName();

    private final long startedNanos = System.nanoTime();
    private final AtomicLong canonicalLoadMs = new AtomicLong();
    private final AtomicLong leagueTeamSyncMs = new AtomicLong();
    private final AtomicLong leagueLoadMs = new AtomicLong();
    private final AtomicLong teamPlayerLoadMs = new AtomicLong();
    private final AtomicLong assemblyMs = new AtomicLong();
    private final AtomicLong serializationMs = new AtomicLong();
    private final AtomicLong ownershipInitMs = new AtomicLong();
    private final AtomicLong redisSaveMs = new AtomicLong();
    private final AtomicLong responseBuildMs = new AtomicLong();
    private final AtomicLong statusQueryMs = new AtomicLong();
    private final AtomicLong leagueCount = new AtomicLong();
    private final AtomicLong teamCount = new AtomicLong();
    private final AtomicLong playerCount = new AtomicLong();
    private final AtomicLong serializedBytes = new AtomicLong();

    public <T> Mono<T> measure(String stage, Mono<T> publisher) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            return publisher.doOnSuccess(ignored -> record(stage, started))
                    .doOnError(ignored -> record(stage, started));
        });
    }

    public void record(String stage, long startedNanos) {
        long elapsed = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        switch (stage) {
            case "canonicalLoadMs" -> canonicalLoadMs.set(elapsed);
            case "leagueTeamSyncMs" -> leagueTeamSyncMs.set(elapsed);
            case "leagueLoadMs" -> leagueLoadMs.set(elapsed);
            case "teamPlayerLoadMs" -> teamPlayerLoadMs.set(elapsed);
            case "assemblyMs" -> assemblyMs.set(elapsed);
            case "serializationMs" -> serializationMs.set(elapsed);
            case "ownershipInitMs" -> ownershipInitMs.set(elapsed);
            case "redisSaveMs" -> redisSaveMs.set(elapsed);
            case "responseBuildMs" -> responseBuildMs.set(elapsed);
            case "statusQueryMs" -> statusQueryMs.set(elapsed);
            default -> { }
        }
    }

    public void countWorld(int leagues, int teams, int players) {
        leagueCount.set(Math.max(0, leagues));
        teamCount.set(Math.max(0, teams));
        playerCount.set(Math.max(0, players));
    }

    public void serializedBytes(long bytes) {
        serializedBytes.set(Math.max(0L, bytes));
    }

    public void writeHeaders(ServerHttpResponse response) {
        response.getHeaders().set("X-Reload-Server-Ms", Long.toString(elapsedMs()));
        response.getHeaders().set("X-Reload-Canonical-Ms", Long.toString(canonicalLoadMs.get()));
        response.getHeaders().set("X-Reload-League-Team-Ms", Long.toString(leagueTeamSyncMs.get()));
        response.getHeaders().set("X-Reload-Leagues-Ms", Long.toString(leagueLoadMs.get()));
        response.getHeaders().set("X-Reload-Players-Ms", Long.toString(teamPlayerLoadMs.get()));
        response.getHeaders().set("X-Reload-Assembly-Ms", Long.toString(assemblyMs.get()));
        response.getHeaders().set("X-Reload-Serialize-Ms", Long.toString(serializationMs.get()));
        response.getHeaders().set("X-Reload-Ownership-Ms", Long.toString(ownershipInitMs.get()));
        response.getHeaders().set("X-Reload-Redis-Ms", Long.toString(redisSaveMs.get()));
        response.getHeaders().set("X-Reload-Response-Ms", Long.toString(responseBuildMs.get()));
        response.getHeaders().set("X-Reload-Status-Ms", Long.toString(statusQueryMs.get()));
        response.getHeaders().set("X-Reload-League-Count", Long.toString(leagueCount.get()));
        response.getHeaders().set("X-Reload-Team-Count", Long.toString(teamCount.get()));
        response.getHeaders().set("X-Reload-Player-Count", Long.toString(playerCount.get()));
        response.getHeaders().set("X-Reload-Serialized-Bytes", Long.toString(serializedBytes.get()));
    }

    public long elapsedMs() {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> values = new java.util.LinkedHashMap<>();
        values.put("totalMs", elapsedMs());
        values.put("canonicalLoadMs", canonicalLoadMs.get());
        values.put("leagueTeamSyncMs", leagueTeamSyncMs.get());
        values.put("leagueLoadMs", leagueLoadMs.get());
        values.put("teamPlayerLoadMs", teamPlayerLoadMs.get());
        values.put("assemblyMs", assemblyMs.get());
        values.put("serializationMs", serializationMs.get());
        values.put("ownershipInitMs", ownershipInitMs.get());
        values.put("redisSaveMs", redisSaveMs.get());
        values.put("responseBuildMs", responseBuildMs.get());
        values.put("statusQueryMs", statusQueryMs.get());
        values.put("leagueCount", leagueCount.get());
        values.put("teamCount", teamCount.get());
        values.put("playerCount", playerCount.get());
        values.put("serializedBytes", serializedBytes.get());
        return Map.copyOf(values);
    }
}
