package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Explicit semantic equality authority for persisted world snapshots. */
@Component
public final class WorldSemanticComparator {

    public Comparison compare(WorldSnapshot expected, WorldSnapshot actual) {
        List<String> differences = new ArrayList<>();
        if (expected == null || actual == null) {
            if (expected != actual) differences.add("snapshot:null-mismatch");
            return new Comparison(differences.isEmpty(), List.copyOf(differences));
        }
        check(differences, "snapshot.userId", expected.getUserId(), actual.getUserId());
        check(differences, "snapshot.createdAt", expected.getCreatedAt(), actual.getCreatedAt());
        check(differences, "snapshot.lastUpdated", expected.getLastUpdated(), actual.getLastUpdated());
        compareLeagues(differences, expected.getLeagues(), actual.getLeagues());
        compareTeams(differences, expected.getWorldTeams(), actual.getWorldTeams());
        comparePlayers(differences, expected, actual);
        compareAliases(differences, expected, actual);
        return new Comparison(differences.isEmpty(), List.copyOf(differences));
    }

    public void requireEquivalent(WorldSnapshot expected, WorldSnapshot actual) {
        Comparison result = compare(expected, actual);
        if (!result.equivalent()) {
            throw new IllegalStateException("World semantic mismatch: " + String.join(", ", result.differences()));
        }
    }

    private static void compareLeagues(List<String> out, List<WorldLeague> expected, List<WorldLeague> actual) {
        Map<String, WorldLeague> left = leagueMap(expected);
        Map<String, WorldLeague> right = leagueMap(actual);
        check(out, "leagues.keys", left.keySet(), right.keySet());
        left.forEach((key, a) -> {
            WorldLeague b = right.get(key);
            if (b == null) return;
            check(out, "league[" + key + "].realLeagueId", a.getRealLeagueId(), b.getRealLeagueId());
            check(out, "league[" + key + "].name", a.getName(), b.getName());
            check(out, "league[" + key + "].country", a.getCountry(), b.getCountry());
            check(out, "league[" + key + "].tier", a.getTier(), b.getTier());
        });
    }

    private static Map<String, WorldLeague> leagueMap(List<WorldLeague> leagues) {
        Map<String, WorldLeague> result = new LinkedHashMap<>();
        if (leagues == null) return result;
        List<WorldLeague> ordered = new ArrayList<>(leagues);
        ordered.sort(Comparator.comparing((WorldLeague league) ->
                        Objects.toString(league == null ? null : league.getRealLeagueId(), ""))
                .thenComparing(league -> Objects.toString(league == null ? null : league.getName(), "")));
        for (int i = 0; i < ordered.size(); i++) {
            WorldLeague league = ordered.get(i);
            String key = league == null ? "null#" + i
                    : Objects.toString(league.getRealLeagueId(), "custom#" + i);
            result.put(key, league);
        }
        return result;
    }

    private static void compareTeams(List<String> out, Map<String, WorldTeam> expected,
                                     Map<String, WorldTeam> actual) {
        compareEntityMaps(out, "teams", expected, actual, (path, a, b) -> {
            check(out, path + ".worldTeamId", a.getWorldTeamId(), b.getWorldTeamId());
            check(out, path + ".realTeamId", a.getRealTeamId(), b.getRealTeamId());
            check(out, path + ".realLeagueId", a.getRealLeagueId(), b.getRealLeagueId());
            check(out, path + ".name", a.getName(), b.getName());
            check(out, path + ".country", a.getCountry(), b.getCountry());
            check(out, path + ".city", a.getCity(), b.getCity());
            check(out, path + ".baseBudget", a.getBaseBudget(), b.getBaseBudget());
            check(out, path + ".baseFormation", a.getBaseFormation(), b.getBaseFormation());
            check(out, path + ".origin", a.getOrigin(), b.getOrigin());
            check(out, path + ".division", a.getDivision(), b.getDivision());
        });
    }

    private static void comparePlayers(List<String> out, WorldSnapshot expectedSnapshot,
                                       WorldSnapshot actualSnapshot) {
        Map<String, WorldPlayer> expected = normalizedPlayers(expectedSnapshot.getWorldPlayers());
        Map<String, WorldPlayer> actual = normalizedPlayers(actualSnapshot.getWorldPlayers());
        compareEntityMaps(out, "players", expected, actual, (path, a, b) -> {
            if (!Objects.equals(a.getWorldPlayerId(), b.getWorldPlayerId())
                    && !Objects.equals(actualSnapshot.getWorldPlayerAliases().get(a.getWorldPlayerId()),
                    b.getWorldPlayerId())) {
                out.add(path + ".worldPlayerId");
            }
            check(out, path + ".realPlayerId", a.getRealPlayerId(), b.getRealPlayerId());
            check(out, path + ".worldTeamId", a.getWorldTeamId(), b.getWorldTeamId());
            check(out, path + ".name", a.getName(), b.getName());
            check(out, path + ".age", a.getAge(), b.getAge());
            check(out, path + ".position", a.getPosition(), b.getPosition());
            check(out, path + ".baseAttack", a.getBaseAttack(), b.getBaseAttack());
            check(out, path + ".baseDefense", a.getBaseDefense(), b.getBaseDefense());
            check(out, path + ".baseTechnique", a.getBaseTechnique(), b.getBaseTechnique());
            check(out, path + ".baseSpeed", a.getBaseSpeed(), b.getBaseSpeed());
            check(out, path + ".baseStamina", a.getBaseStamina(), b.getBaseStamina());
            check(out, path + ".baseMentality", a.getBaseMentality(), b.getBaseMentality());
            check(out, path + ".baseMarketValue", a.getBaseMarketValue(), b.getBaseMarketValue());
            check(out, path + ".origin", a.getOrigin(), b.getOrigin());
            check(out, path + ".heightCm", a.getHeightCm(), b.getHeightCm());
            check(out, path + ".skillLevels", a.getSkillLevels(), b.getSkillLevels());
            check(out, path + ".specialTraits", a.getSpecialTraits(), b.getSpecialTraits());
        });
    }

    private static Map<String, WorldPlayer> normalizedPlayers(Map<String, WorldPlayer> players) {
        Map<String, WorldPlayer> result = new LinkedHashMap<>();
        if (players == null) return result;
        players.forEach((key, player) -> {
            String semanticKey = player != null && player.getRealPlayerId() != null
                    ? "real:" + player.getRealPlayerId() : "world:" + key;
            WorldPlayer prior = result.putIfAbsent(semanticKey, player);
            if (prior != null && prior != player) {
                throw new IllegalStateException("Duplicate semantic player identity: " + semanticKey);
            }
        });
        return result;
    }

    private static void compareAliases(List<String> out, WorldSnapshot expected, WorldSnapshot actual) {
        Map<String, String> expectedAliases = expected.getWorldPlayerAliases();
        Map<String, String> actualAliases = actual.getWorldPlayerAliases();
        Map<String, String> requiredAliases = requiredMigrationAliases(expected, actual);
        Set<String> allowedKeys = new java.util.LinkedHashSet<>(expectedAliases.keySet());
        allowedKeys.addAll(requiredAliases.keySet());

        validateAliasGraph(out, "expected", expected, expectedAliases);
        validateAliasGraph(out, "actual", actual, actualAliases);
        expectedAliases.forEach((key, value) -> {
            String resolvedExpected = resolveAlias(value, expectedAliases);
            String resolvedActual = resolveAlias(actualAliases.get(key), actualAliases);
            if (resolvedActual == null || !sameSemanticPlayer(expected, resolvedExpected, actual, resolvedActual)) {
                out.add("snapshot.aliases[" + key + "]");
            }
        });
        requiredAliases.forEach((legacyId, canonicalId) -> {
            String resolvedActual = resolveAlias(actualAliases.get(legacyId), actualAliases);
            if (!Objects.equals(canonicalId, resolvedActual)) {
                out.add("snapshot.aliases.required[" + legacyId + "]");
            }
        });
        actualAliases.forEach((key, value) -> {
            if (!allowedKeys.contains(key)) {
                out.add("snapshot.aliases.unexpected[" + key + "]");
            }
        });
    }

    private static Map<String, String> requiredMigrationAliases(WorldSnapshot expected, WorldSnapshot actual) {
        Map<String, String> required = new LinkedHashMap<>();
        Map<String, String> actualBySemanticIdentity = new LinkedHashMap<>();
        if (actual.getWorldPlayers() != null) {
            actual.getWorldPlayers().forEach((id, player) -> {
                if (player != null && player.getRealPlayerId() != null) {
                    actualBySemanticIdentity.put("real:" + player.getRealPlayerId(), id);
                }
            });
        }
        if (expected.getWorldPlayers() != null) {
            expected.getWorldPlayers().forEach((legacyId, player) -> {
                if (player == null || player.getRealPlayerId() == null) return;
                String actualId = actualBySemanticIdentity.get("real:" + player.getRealPlayerId());
                if (actualId != null && !legacyId.equals(actualId)) required.put(legacyId, actualId);
            });
        }
        return required;
    }

    private static void validateAliasGraph(List<String> out, String side, WorldSnapshot snapshot,
                                           Map<String, String> aliases) {
        aliases.forEach((key, value) -> {
            if (key == null || value == null || key.equals(value)) {
                out.add("snapshot.aliases." + side + ".invalid[" + key + "]");
                return;
            }
            String resolved = resolveAlias(value, aliases);
            if (resolved == null || snapshot.getWorldPlayer(resolved) == null) {
                out.add("snapshot.aliases." + side + ".unresolved[" + key + "]");
            }
        });
    }

    private static String resolveAlias(String id, Map<String, String> aliases) {
        if (id == null) return null;
        String current = id;
        for (int i = 0; i < 8; i++) {
            String next = aliases.get(current);
            if (next == null || next.equals(current)) return current;
            current = next;
        }
        return null;
    }

    private static boolean sameSemanticPlayer(WorldSnapshot expected, String expectedId,
                                              WorldSnapshot actual, String actualId) {
        WorldPlayer left = expected.getWorldPlayer(expectedId);
        WorldPlayer right = actual.getWorldPlayer(actualId);
        if (left == null || right == null) return false;
        if (left.getRealPlayerId() != null || right.getRealPlayerId() != null) {
            return Objects.equals(left.getRealPlayerId(), right.getRealPlayerId());
        }
        return Objects.equals(left.getWorldPlayerId(), right.getWorldPlayerId());
    }

    private static <T> void compareEntityMaps(List<String> out, String name, Map<String, T> expected,
                                              Map<String, T> actual, EntityComparison<T> comparison) {
        Map<String, T> left = expected == null ? Map.of() : expected;
        Map<String, T> right = actual == null ? Map.of() : actual;
        check(out, name + ".keys", left.keySet(), right.keySet());
        left.forEach((key, value) -> {
            T other = right.get(key);
            if (value == null || other == null) {
                if (value != other) out.add(name + "[" + key + "]:null-mismatch");
            } else {
                comparison.compare(name + "[" + key + "]", value, other);
            }
        });
    }

    private static void check(List<String> out, String path, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) out.add(path);
    }

    @FunctionalInterface
    private interface EntityComparison<T> {
        void compare(String path, T expected, T actual);
    }

    public record Comparison(boolean equivalent, List<String> differences) { }
}
