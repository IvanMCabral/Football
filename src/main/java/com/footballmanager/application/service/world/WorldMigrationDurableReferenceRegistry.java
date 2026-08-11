package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit, reflection-verified authority for durable world identity references. */
@Component
public final class WorldMigrationDurableReferenceRegistry {

    public enum Role { WORLD_TEAM, WORLD_PLAYER, CANONICAL_TEAM, CANONICAL_PLAYER, CONTAINER, NON_REFERENCE }

    private final Map<Class<?>, Map<String, Role>> models = buildModels();
    private final WorldMigrationPersistedModelGraph.Graph persistedGraph =
            new WorldMigrationPersistedModelGraph().discover();
    private final Set<String> collectionPaths = Set.of(
            "WorldSnapshot.worldTeams.keys(worldTeamId)",
            "WorldSnapshot.worldPlayers.keys(worldPlayerId)",
            "WorldSnapshot.worldPlayerAliases.keys(legacyWorldPlayerId)",
            "WorldSnapshot.worldPlayerAliases.values(worldPlayerId)",
            "CareerPlayerManager.removedPlayers.keys(worldTeamId)",
            "CareerPlayerManager.removedPlayers.values(worldPlayerId)",
            "CareerPlayerManager.sessionPlayers.values(SessionPlayer.worldPlayerId)",
            "CareerPlayerManager.freePlayers.values(SessionPlayer.worldPlayerId)",
            "CareerSave.teamSquads.keys(sessionTeamId)",
            "CareerSave.teamSquads.values(sessionPlayerId)",
            "CareerSave.startingXI.keys(sessionTeamId)",
            "CareerSave.startingXI.values(sessionPlayerId)",
            "CareerSave.lineupSlots.values(sessionPlayerId)",
            "CareerSave.fixtures.values(sessionTeamId)",
            "CareerSave.standings.keys(sessionTeamId)"
    );

    public Set<String> referencePaths() {
        Set<String> paths = new LinkedHashSet<>();
        models.forEach((type, fields) -> fields.forEach((name, role) -> {
            if (role != Role.NON_REFERENCE && role != Role.CONTAINER) {
                paths.add(type.getSimpleName() + "." + name);
            }
        }));
        paths.addAll(collectionPaths);
        return Set.copyOf(paths);
    }

    public List<String> uncoveredModelFields() {
        List<String> result = new ArrayList<>();
        models.forEach((type, classified) -> {
            Set<String> actual = instanceFields(type);
            actual.stream().filter(field -> !classified.containsKey(field))
                    .forEach(field -> result.add(type.getSimpleName() + "." + field));
            classified.keySet().stream().filter(field -> !actual.contains(field))
                    .forEach(field -> result.add(type.getSimpleName() + "." + field + ":stale-classification"));
        });
        result.addAll(persistedGraph.unresolvedGenericPaths());
        return List.copyOf(result);
    }

    public void requireComplete() {
        List<String> uncovered = uncoveredModelFields();
        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("Unclassified durable reference fields: " + String.join(", ", uncovered));
        }
    }

    public Map<Class<?>, Map<String, Role>> models() {
        Map<Class<?>, Map<String, Role>> copy = new LinkedHashMap<>();
        models.forEach((key, value) -> copy.put(key, Map.copyOf(value)));
        return Map.copyOf(copy);
    }

    public WorldMigrationPersistedModelGraph.Graph persistedGraph() {
        return persistedGraph;
    }

    public List<String> uncoveredAnnotatedReferences(Class<?> root) {
        WorldMigrationPersistedModelGraph.Graph graph = new WorldMigrationPersistedModelGraph()
                .inspect(Set.of(root), Map.of());
        return graph.annotatedReferences().stream().sorted().toList();
    }

    private static Set<String> instanceFields(Class<?> type) {
        Set<String> result = new LinkedHashSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) result.add(field.getName());
        }
        return result;
    }

    private static Map<Class<?>, Map<String, Role>> buildModels() {
        Map<Class<?>, Map<String, Role>> result = new LinkedHashMap<>();
        result.put(WorldTeam.class, fields(
                "worldTeamId", Role.WORLD_TEAM, "realTeamId", Role.CANONICAL_TEAM,
                "realLeagueId", Role.NON_REFERENCE, "name", Role.NON_REFERENCE,
                "country", Role.NON_REFERENCE, "city", Role.NON_REFERENCE,
                "baseBudget", Role.NON_REFERENCE, "baseFormation", Role.NON_REFERENCE,
                "origin", Role.NON_REFERENCE, "division", Role.NON_REFERENCE));
        result.put(WorldPlayer.class, fields(
                "worldPlayerId", Role.WORLD_PLAYER, "realPlayerId", Role.CANONICAL_PLAYER,
                "worldTeamId", Role.WORLD_TEAM, "name", Role.NON_REFERENCE,
                "age", Role.NON_REFERENCE, "position", Role.NON_REFERENCE,
                "baseAttack", Role.NON_REFERENCE, "baseDefense", Role.NON_REFERENCE,
                "baseTechnique", Role.NON_REFERENCE, "baseSpeed", Role.NON_REFERENCE,
                "baseStamina", Role.NON_REFERENCE, "baseMentality", Role.NON_REFERENCE,
                "baseMarketValue", Role.NON_REFERENCE, "origin", Role.NON_REFERENCE,
                "heightCm", Role.NON_REFERENCE, "skillLevels", Role.NON_REFERENCE,
                "specialTraits", Role.NON_REFERENCE));
        result.put(WorldSnapshot.class, fields(
                "userId", Role.NON_REFERENCE, "leagues", Role.NON_REFERENCE,
                "worldTeams", Role.CONTAINER, "worldPlayers", Role.CONTAINER,
                "worldPlayerAliases", Role.CONTAINER, "createdAt", Role.NON_REFERENCE,
                "lastUpdated", Role.NON_REFERENCE));
        result.put(SessionTeam.class, fields(
                "sessionTeamId", Role.NON_REFERENCE, "baseTeamId", Role.CANONICAL_TEAM,
                "worldTeamId", Role.WORLD_TEAM, "name", Role.NON_REFERENCE,
                "country", Role.NON_REFERENCE, "budget", Role.NON_REFERENCE,
                "formation", Role.NON_REFERENCE, "style", Role.NON_REFERENCE,
                "managerName", Role.NON_REFERENCE, "morale", Role.NON_REFERENCE,
                "reputation", Role.NON_REFERENCE, "origin", Role.NON_REFERENCE,
                "createdAt", Role.NON_REFERENCE, "lastUpdated", Role.NON_REFERENCE));
        result.put(SessionPlayer.class, fields(
                "sessionPlayerId", Role.NON_REFERENCE, "basePlayerId", Role.CANONICAL_PLAYER,
                "worldPlayerId", Role.WORLD_PLAYER, "name", Role.NON_REFERENCE,
                "age", Role.NON_REFERENCE, "position", Role.NON_REFERENCE,
                "attack", Role.NON_REFERENCE, "defense", Role.NON_REFERENCE,
                "technique", Role.NON_REFERENCE, "speed", Role.NON_REFERENCE,
                "stamina", Role.NON_REFERENCE, "mentality", Role.NON_REFERENCE,
                "marketValue", Role.NON_REFERENCE, "energy", Role.NON_REFERENCE,
                "form", Role.NON_REFERENCE, "injured", Role.NON_REFERENCE,
                "injuryType", Role.NON_REFERENCE, "injuryRemainingMatches", Role.NON_REFERENCE,
                "matchesPlayedInRow", Role.NON_REFERENCE, "yellowCards", Role.NON_REFERENCE,
                "redCards", Role.NON_REFERENCE, "suspended", Role.NON_REFERENCE,
                "suspensionRemainingMatches", Role.NON_REFERENCE, "origin", Role.NON_REFERENCE,
                "heightCm", Role.NON_REFERENCE, "skillLevels", Role.NON_REFERENCE,
                "specialTraits", Role.NON_REFERENCE));
        result.put(CareerPlayerManager.class, fields(
                "sessionPlayersRef", Role.CONTAINER,
                "freePlayersRef", Role.CONTAINER,
                "removedPlayersRef", Role.CONTAINER));
        return result;
    }

    private static Map<String, Role> fields(Object... values) {
        Map<String, Role> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], (Role) values[i + 1]);
        return result;
    }
}
