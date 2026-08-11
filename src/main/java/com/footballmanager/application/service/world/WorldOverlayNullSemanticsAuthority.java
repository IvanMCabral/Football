package com.footballmanager.application.service.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Explicit presence/null contract for every owner-overridable World V2 value. */
public final class WorldOverlayNullSemanticsAuthority {

    public enum AbsentMeaning { PRESERVE_CANONICAL, STRUCTURAL_COLLECTION_DIFF }

    public record NullSemantics(boolean nullable, boolean canonicalNullAllowed,
                                boolean ownerExplicitNullAllowed, AbsentMeaning absentMeaning) { }

    private final Map<String, NullSemantics> fields = build();

    public Map<String, NullSemantics> fields() {
        return Map.copyOf(fields);
    }

    public NullSemantics require(String path) {
        NullSemantics semantics = fields.get(path);
        if (semantics == null) throw new IllegalArgumentException("Unclassified overlay null semantics: " + path);
        return semantics;
    }

    public void requireExactly(Set<String> expectedPaths) {
        if (!fields.keySet().equals(expectedPaths)) {
            throw new IllegalStateException("Overlay null authority mismatch: expected=" + expectedPaths
                    + ", actual=" + fields.keySet());
        }
    }

    private static Map<String, NullSemantics> build() {
        Map<String, NullSemantics> result = new LinkedHashMap<>();
        nullable(result, "WorldSnapshot.createdAt", "WorldSnapshot.lastUpdated");
        nullable(result,
                "WorldTeam.realLeagueId", "WorldTeam.name", "WorldTeam.country", "WorldTeam.city",
                "WorldTeam.baseBudget", "WorldTeam.baseFormation", "WorldTeam.division");
        nullable(result,
                "WorldPlayer.worldTeamId", "WorldPlayer.name", "WorldPlayer.age", "WorldPlayer.position",
                "WorldPlayer.baseAttack", "WorldPlayer.baseDefense", "WorldPlayer.baseTechnique",
                "WorldPlayer.baseSpeed", "WorldPlayer.baseStamina", "WorldPlayer.baseMentality",
                "WorldPlayer.baseMarketValue", "WorldPlayer.heightCm");
        structural(result, "WorldPlayer.skillLevels", "WorldPlayer.specialTraits");
        nullable(result, "WorldLeague.name", "WorldLeague.country", "WorldLeague.tier");
        return result;
    }

    private static void nullable(Map<String, NullSemantics> result, String... paths) {
        for (String path : paths) {
            result.put(path, new NullSemantics(true, true, true, AbsentMeaning.PRESERVE_CANONICAL));
        }
    }

    private static void structural(Map<String, NullSemantics> result, String... paths) {
        for (String path : paths) {
            result.put(path, new NullSemantics(false, false, false,
                    AbsentMeaning.STRUCTURAL_COLLECTION_DIFF));
        }
    }
}
