package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CanonicalWorldCatalogFingerprintTest {

    private final CanonicalWorldCatalogFingerprint fingerprint =
            new CanonicalWorldCatalogFingerprint(new ObjectMapper().findAndRegisterModules());

    @Test
    void orderingIsNonMaterialAcrossMapsListsAndTraits() {
        WorldSnapshot first = fixture(false, false, false);
        WorldSnapshot reversed = fixture(true, true, true);
        assertEquals(fingerprint.fingerprint(first), fingerprint.fingerprint(reversed));
    }

    @Test
    void everyMaterialCatalogDimensionChangesTheFingerprint() {
        WorldSnapshot baseline = fixture(false, false, false);
        String expected = fingerprint.fingerprint(baseline);

        WorldSnapshot playerChanged = fixture(false, false, false);
        playerChanged.getAllWorldPlayers().get(0).setBaseAttack(99);
        assertNotEquals(expected, fingerprint.fingerprint(playerChanged));

        WorldSnapshot teamChanged = fixture(false, false, false);
        teamChanged.getAllWorldTeams().get(0).setBaseFormation("3-5-2");
        assertNotEquals(expected, fingerprint.fingerprint(teamChanged));

        WorldSnapshot leagueChanged = fixture(false, false, false);
        leagueChanged.getLeagues().get(0).setTier(9);
        assertNotEquals(expected, fingerprint.fingerprint(leagueChanged));

        WorldSnapshot traitChanged = fixture(false, false, false);
        WorldPlayer player = traitChanged.getAllWorldPlayers().get(0);
        player.setSpecialTraits(List.of(new PlayerSpecialTrait(player.getRealPlayerId(), "OTHER", "Other", "Other")));
        assertNotEquals(expected, fingerprint.fingerprint(traitChanged));

        assertNotEquals(expected, fingerprint.fingerprint(baseline, 3,
                CanonicalWorldCatalogFingerprint.MATERIAL_CONFIGURATION));
        assertNotEquals(expected, fingerprint.fingerprint(baseline,
                CanonicalWorldCatalogFingerprint.SCHEMA_VERSION, "different-material-seed"));
    }

    private WorldSnapshot fixture(boolean reverseTeams, boolean reversePlayers, boolean reverseTraits) {
        UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID leagueA = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID leagueB = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID teamA = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID teamB = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UUID playerA = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID playerB = UUID.fromString("30000000-0000-0000-0000-000000000002");
        WorldSnapshot world = new WorldSnapshot();
        world.setUserId(owner);
        List<WorldLeague> leagues = new ArrayList<>(List.of(
                WorldLeague.fromRealLeague(leagueA, "A", "AR", 1),
                WorldLeague.fromRealLeague(leagueB, "B", "BR", 1)));
        if (reverseTeams) java.util.Collections.reverse(leagues);
        world.setLeagues(leagues);
        WorldTeam a = WorldTeam.fromRealTeam(teamA, leagueA, "A", "AR", "A", BigDecimal.TEN, "4-4-2");
        WorldTeam b = WorldTeam.fromRealTeam(teamB, leagueB, "B", "BR", "B", BigDecimal.TEN, "4-3-3");
        LinkedHashMap<String, WorldTeam> teams = new LinkedHashMap<>();
        if (reverseTeams) { teams.put(b.getWorldTeamId(), b); teams.put(a.getWorldTeamId(), a); }
        else { teams.put(a.getWorldTeamId(), a); teams.put(b.getWorldTeamId(), b); }
        world.setWorldTeams(teams);
        WorldPlayer p1 = player(owner, playerA, a.getWorldTeamId(), "P1", reverseTraits);
        WorldPlayer p2 = player(owner, playerB, b.getWorldTeamId(), "P2", reverseTraits);
        LinkedHashMap<String, WorldPlayer> players = new LinkedHashMap<>();
        if (reversePlayers) { players.put(p2.getWorldPlayerId(), p2); players.put(p1.getWorldPlayerId(), p1); }
        else { players.put(p1.getWorldPlayerId(), p1); players.put(p2.getWorldPlayerId(), p2); }
        world.setWorldPlayers(players);
        return world;
    }

    private WorldPlayer player(UUID owner, UUID realId, String team, String name, boolean reverseTraits) {
        WorldPlayer player = WorldPlayer.fromCanonicalPlayer(owner, realId, team, name, 22,
                "MID", 70, 70, 70, 70, 70, 70, BigDecimal.TEN);
        List<PlayerSpecialTrait> traits = new ArrayList<>(List.of(
                new PlayerSpecialTrait(realId, "A", "A", "A"),
                new PlayerSpecialTrait(realId, "B", "B", "B")));
        if (reverseTraits) java.util.Collections.reverse(traits);
        player.setSpecialTraits(traits);
        return player;
    }
}
